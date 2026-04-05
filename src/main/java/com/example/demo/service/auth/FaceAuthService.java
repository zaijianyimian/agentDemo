package com.example.demo.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.auth.FaceStatusResponse;
import com.example.demo.entity.UserAccount;
import com.example.demo.entity.UserFaceProfile;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.mapper.UserFaceProfileMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FaceAuthService {

    private static final int MODERN_FACE_SIZE = 96;
    private static final int LEGACY_FACE_SIZE = 32;
    private static final int CELL_GRID = 6;
    private static final int LBP_BINS = 16;
    private static final int INTENSITY_BINS = 16;
    private static final int GRADIENT_BINS = 8;
    private static final int SAMPLE_LIMIT = 3;

    private final UserFaceProfileMapper userFaceProfileMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.face.threshold:0.82}")
    private double similarityThreshold;

    @Value("${app.auth.face.min-quality:0.22}")
    private double minQualityScore;

    public FaceStatusResponse register(Long userId, String imageBase64) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        FaceSample sample = extractFaceSample(imageBase64);
        if (sample.quality() < minQualityScore) {
            throw new IllegalArgumentException("图片质量过低，请使用光线充足、正脸居中的清晰照片");
        }

        UserFaceProfile existing = getByUserId(userId);
        FaceTemplate template = mergeTemplate(existing == null ? null : existing.getEmbedding(), sample);
        UserFaceProfile profile = UserFaceProfile.builder()
                .id(existing == null ? null : existing.getId())
                .userId(userId)
                .embedding(writeTemplate(template))
                .vectorDimension(template.template().size())
                .qualityScore(template.qualityScore())
                .enabled(true)
                .build();
        if (existing == null) {
            userFaceProfileMapper.insert(profile);
        } else {
            userFaceProfileMapper.updateById(profile);
        }
        return getStatus(userId);
    }

    public FaceStatusResponse updateRequired(Long userId, boolean required) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (required && getByUserId(userId) == null) {
            throw new IllegalArgumentException("请先绑定人脸后再开启人脸二次验证");
        }
        user.setFaceAuthEnabled(required);
        userAccountMapper.updateById(user);
        return getStatus(userId);
    }

    public FaceStatusResponse getStatus(Long userId) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserFaceProfile profile = getByUserId(userId);
        return FaceStatusResponse.builder()
                .enrolled(profile != null)
                .required(Boolean.TRUE.equals(user.getFaceAuthEnabled()))
                .enabled(profile != null && Boolean.TRUE.equals(profile.getEnabled()))
                .vectorDimension(profile == null ? null : profile.getVectorDimension())
                .qualityScore(profile == null ? null : profile.getQualityScore())
                .build();
    }

    public boolean isFaceRequired(UserAccount user) {
        return user != null && Boolean.TRUE.equals(user.getFaceAuthEnabled());
    }

    public double verifySimilarity(Long userId, String imageBase64) {
        UserFaceProfile profile = getByUserId(userId);
        if (profile == null || !Boolean.TRUE.equals(profile.getEnabled())) {
            throw new IllegalArgumentException("当前账号未绑定可用人脸，请联系管理员");
        }

        FaceSample sample = extractFaceSample(imageBase64);
        FaceTemplate stored = readTemplate(profile.getEmbedding());
        if (stored.legacy()) {
            return cosineSimilarity(stored.template(), sample.legacyEmbedding());
        }

        double aggregateScore = cosineSimilarity(stored.template(), sample.embedding());
        double bestScore = aggregateScore;
        for (FaceTemplateSample item : stored.samples()) {
            bestScore = Math.max(bestScore, cosineSimilarity(item.embedding(), sample.embedding()));
        }
        return bestScore;
    }

    public void verifyForLogin(Long userId, String imageBase64) {
        UserFaceProfile profile = getByUserId(userId);
        if (profile == null || !Boolean.TRUE.equals(profile.getEnabled())) {
            throw new IllegalArgumentException("当前账号未绑定可用人脸，请联系管理员");
        }

        FaceTemplate stored = readTemplate(profile.getEmbedding());
        FaceSample sample = extractFaceSample(imageBase64);
        double threshold = stored.legacy() ? Math.min(similarityThreshold, 0.72D) : similarityThreshold;
        double similarity;
        if (stored.legacy()) {
            similarity = cosineSimilarity(stored.template(), sample.legacyEmbedding());
        } else {
            similarity = cosineSimilarity(stored.template(), sample.embedding());
            for (FaceTemplateSample item : stored.samples()) {
                similarity = Math.max(similarity, cosineSimilarity(item.embedding(), sample.embedding()));
            }
        }
        if (similarity < threshold) {
            throw new IllegalArgumentException(String.format(Locale.ROOT,
                    "人脸验证失败（相似度 %.3f，阈值 %.3f）", similarity, threshold));
        }
    }

    private UserFaceProfile getByUserId(Long userId) {
        return userFaceProfileMapper.selectOne(new LambdaQueryWrapper<UserFaceProfile>()
                .eq(UserFaceProfile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private FaceSample extractFaceSample(String imageBase64) {
        byte[] bytes = decodeImageBytes(imageBase64);
        BufferedImage raw;
        try {
            raw = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new IllegalArgumentException("图片解码失败，请上传合法图片");
        }
        if (raw == null) {
            throw new IllegalArgumentException("图片内容无效");
        }

        FaceBounds bounds = detectFaceBounds(raw);
        BufferedImage modernFace = normalizeFaceRegion(raw, bounds, MODERN_FACE_SIZE);
        List<Double> modernEmbedding = extractModernEmbedding(modernFace);
        double quality = evaluateQuality(modernFace, bounds, raw.getWidth(), raw.getHeight());

        BufferedImage legacyFace = normalizeLegacyFace(raw);
        List<Double> legacyEmbedding = extractLegacyEmbedding(legacyFace);
        return new FaceSample(modernEmbedding, legacyEmbedding, quality, bounds);
    }

    private FaceBounds detectFaceBounds(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int minSize = Math.min(width, height);
        if (minSize < 48) {
            return centerSquareBounds(width, height);
        }

        FaceBounds bestBounds = centerSquareBounds(width, height);
        double bestScore = Double.NEGATIVE_INFINITY;
        int minWindow = Math.max(48, (int) (minSize * 0.45));
        int maxWindow = Math.max(minWindow, (int) (minSize * 0.85));
        int sizeStep = Math.max(8, minSize / 12);

        for (int size = maxWindow; size >= minWindow; size -= sizeStep) {
            int centerX = width / 2 - size / 2;
            int centerY = height / 2 - size / 2;
            int travelX = Math.max(8, width / 8);
            int travelY = Math.max(8, height / 8);
            int step = Math.max(4, size / 10);
            for (int y = Math.max(0, centerY - travelY); y <= Math.min(height - size, centerY + travelY); y += step) {
                for (int x = Math.max(0, centerX - travelX); x <= Math.min(width - size, centerX + travelX); x += step) {
                    FaceBounds candidate = new FaceBounds(x, y, size);
                    double score = scoreFaceWindow(source, candidate);
                    if (score > bestScore) {
                        bestScore = score;
                        bestBounds = candidate;
                    }
                }
            }
        }

        if (bestScore < 0.28D) {
            return centerSquareBounds(width, height);
        }
        return bestBounds;
    }

    private double scoreFaceWindow(BufferedImage source, FaceBounds bounds) {
        int step = Math.max(2, bounds.size() / 24);
        double skinHits = 0.0D;
        double sampled = 0.0D;
        double leftRightDelta = 0.0D;
        double detail = 0.0D;
        int midX = bounds.x() + bounds.size() / 2;

        for (int y = bounds.y(); y < bounds.y() + bounds.size() - step; y += step) {
            for (int x = bounds.x(); x < bounds.x() + bounds.size() - step; x += step) {
                int rgb = source.getRGB(x, y);
                if (looksLikeSkin(rgb)) {
                    skinHits += 1.0D;
                }
                sampled += 1.0D;

                int mirrorX = midX + (midX - x) - 1;
                if (mirrorX >= bounds.x() && mirrorX < bounds.x() + bounds.size()) {
                    leftRightDelta += Math.abs(grayOf(rgb) - grayOf(source.getRGB(mirrorX, y))) / 255.0D;
                }
                detail += Math.abs(grayOf(rgb) - grayOf(source.getRGB(Math.min(x + step, bounds.x() + bounds.size() - 1), y))) / 255.0D;
            }
        }

        double skinRatio = sampled == 0 ? 0 : skinHits / sampled;
        double symmetry = sampled == 0 ? 0 : 1.0D - Math.min(1.0D, leftRightDelta / sampled);
        double detailScore = sampled == 0 ? 0 : Math.min(1.0D, detail / sampled * 1.6D);
        double centerDistance = Math.hypot(
                bounds.x() + bounds.size() / 2.0D - source.getWidth() / 2.0D,
                bounds.y() + bounds.size() / 2.0D - source.getHeight() / 2.0D
        );
        double maxDistance = Math.hypot(source.getWidth() / 2.0D, source.getHeight() / 2.0D);
        double centrality = 1.0D - Math.min(1.0D, centerDistance / Math.max(1.0D, maxDistance));

        double skinScore = 1.0D - Math.min(1.0D, Math.abs(skinRatio - 0.42D) / 0.42D);
        return skinScore * 0.35D + symmetry * 0.30D + detailScore * 0.20D + centrality * 0.15D;
    }

    private boolean looksLikeSkin(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        int cb = (int) Math.round(128 - 0.168736 * r - 0.331264 * g + 0.5 * b);
        int cr = (int) Math.round(128 + 0.5 * r - 0.418688 * g - 0.081312 * b);
        return cb >= 77 && cb <= 127 && cr >= 133 && cr <= 177 && r > 60 && g > 40 && b > 20;
    }

    private BufferedImage normalizeFaceRegion(BufferedImage source, FaceBounds bounds, int targetSize) {
        BufferedImage cropped = source.getSubimage(bounds.x(), bounds.y(), bounds.size(), bounds.size());
        BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(cropped, 0, 0, targetSize, targetSize, null);
        } finally {
            graphics.dispose();
        }
        equalizeHistogram(scaled);
        return scaled;
    }

    private BufferedImage normalizeLegacyFace(BufferedImage source) {
        FaceBounds bounds = centerSquareBounds(source.getWidth(), source.getHeight());
        BufferedImage cropped = source.getSubimage(bounds.x(), bounds.y(), bounds.size(), bounds.size());
        BufferedImage scaled = new BufferedImage(LEGACY_FACE_SIZE, LEGACY_FACE_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(cropped, 0, 0, LEGACY_FACE_SIZE, LEGACY_FACE_SIZE, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private void equalizeHistogram(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] histogram = new int[256];
        int[] values = new int[width * height];
        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = image.getRGB(x, y) & 0xff;
                values[idx++] = gray;
                histogram[gray]++;
            }
        }

        int[] cumulative = new int[256];
        cumulative[0] = histogram[0];
        for (int i = 1; i < cumulative.length; i++) {
            cumulative[i] = cumulative[i - 1] + histogram[i];
        }

        int total = width * height;
        idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = values[idx++];
                int mapped = (cumulative[gray] - cumulative[0]) * 255 / Math.max(1, total - cumulative[0]);
                int rgb = (mapped << 16) | (mapped << 8) | mapped;
                image.setRGB(x, y, rgb);
            }
        }
    }

    private List<Double> extractModernEmbedding(BufferedImage image) {
        List<Double> features = new ArrayList<>(CELL_GRID * CELL_GRID * LBP_BINS + INTENSITY_BINS + GRADIENT_BINS);
        int cellSize = image.getWidth() / CELL_GRID;

        for (int cellY = 0; cellY < CELL_GRID; cellY++) {
            for (int cellX = 0; cellX < CELL_GRID; cellX++) {
                double[] histogram = new double[LBP_BINS];
                int startX = cellX * cellSize;
                int startY = cellY * cellSize;
                int endX = Math.min(image.getWidth() - 1, startX + cellSize - 1);
                int endY = Math.min(image.getHeight() - 1, startY + cellSize - 1);
                double pixels = 0.0D;

                for (int y = Math.max(1, startY); y < endY; y++) {
                    for (int x = Math.max(1, startX); x < endX; x++) {
                        int center = grayOf(image.getRGB(x, y));
                        int code = 0;
                        if (grayOf(image.getRGB(x, y - 1)) >= center) code |= 1;
                        if (grayOf(image.getRGB(x + 1, y)) >= center) code |= 2;
                        if (grayOf(image.getRGB(x, y + 1)) >= center) code |= 4;
                        if (grayOf(image.getRGB(x - 1, y)) >= center) code |= 8;
                        histogram[code] += 1.0D;
                        pixels += 1.0D;
                    }
                }

                normalizeHistogram(histogram, pixels);
                append(features, histogram);
            }
        }

        double[] intensity = new double[INTENSITY_BINS];
        double[] gradient = new double[GRADIENT_BINS];
        double pixels = image.getWidth() * image.getHeight();
        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                int gray = grayOf(image.getRGB(x, y));
                intensity[Math.min(INTENSITY_BINS - 1, gray * INTENSITY_BINS / 256)] += 1.0D;

                int gx = grayOf(image.getRGB(x + 1, y)) - grayOf(image.getRGB(x - 1, y));
                int gy = grayOf(image.getRGB(x, y + 1)) - grayOf(image.getRGB(x, y - 1));
                double angle = Math.atan2(gy, gx) + Math.PI;
                int bin = Math.min(GRADIENT_BINS - 1, (int) Math.floor(angle / (2 * Math.PI) * GRADIENT_BINS));
                gradient[bin] += Math.min(255.0D, Math.hypot(gx, gy));
            }
        }

        normalizeHistogram(intensity, pixels);
        normalizeHistogram(gradient, sum(gradient));
        append(features, intensity);
        append(features, gradient);
        normalize(features);
        return features;
    }

    private List<Double> extractLegacyEmbedding(BufferedImage image) {
        double[] rawVector = new double[LEGACY_FACE_SIZE * LEGACY_FACE_SIZE];
        int idx = 0;
        for (int y = 0; y < LEGACY_FACE_SIZE; y++) {
            for (int x = 0; x < LEGACY_FACE_SIZE; x++) {
                int gray = image.getRGB(x, y) & 0xff;
                rawVector[idx++] = gray / 255.0D;
            }
        }
        normalize(rawVector);
        return box(rawVector);
    }

    private double evaluateQuality(BufferedImage image, FaceBounds bounds, int sourceWidth, int sourceHeight) {
        double brightnessSum = 0.0D;
        double contrastSum = 0.0D;
        double sharpnessSum = 0.0D;
        double symmetryDelta = 0.0D;
        double center = image.getWidth() / 2.0D;
        double total = 0.0D;

        for (int y = 1; y < image.getHeight() - 1; y++) {
            for (int x = 1; x < image.getWidth() - 1; x++) {
                int gray = grayOf(image.getRGB(x, y));
                brightnessSum += gray;
                contrastSum += gray * gray;
                int laplace = Math.abs(4 * gray
                        - grayOf(image.getRGB(x - 1, y))
                        - grayOf(image.getRGB(x + 1, y))
                        - grayOf(image.getRGB(x, y - 1))
                        - grayOf(image.getRGB(x, y + 1)));
                sharpnessSum += laplace;

                int mirrorX = (int) Math.max(0, Math.min(image.getWidth() - 1, Math.round(center + (center - x) - 1)));
                symmetryDelta += Math.abs(gray - grayOf(image.getRGB(mirrorX, y)));
                total += 1.0D;
            }
        }

        double mean = brightnessSum / Math.max(1.0D, total);
        double variance = contrastSum / Math.max(1.0D, total) - mean * mean;
        double contrast = Math.min(1.0D, Math.sqrt(Math.max(0.0D, variance)) / 72.0D);
        double brightness = 1.0D - Math.min(1.0D, Math.abs(mean - 128.0D) / 128.0D);
        double sharpness = Math.min(1.0D, sharpnessSum / Math.max(1.0D, total) / 28.0D);
        double symmetry = 1.0D - Math.min(1.0D, symmetryDelta / Math.max(1.0D, total) / 42.0D);

        double sizeRatio = bounds.size() / (double) Math.max(1, Math.min(sourceWidth, sourceHeight));
        double framing = 1.0D - Math.min(1.0D, Math.abs(sizeRatio - 0.62D) / 0.38D);
        return brightness * 0.22D + contrast * 0.28D + sharpness * 0.30D + symmetry * 0.12D + framing * 0.08D;
    }

    private FaceTemplate mergeTemplate(String existingEmbedding, FaceSample currentSample) {
        List<FaceTemplateSample> samples = new ArrayList<>();
        samples.add(new FaceTemplateSample(currentSample.embedding(), currentSample.quality()));

        FaceTemplate existing = readTemplate(existingEmbedding);
        if (!existing.legacy()) {
            samples.addAll(existing.samples());
        }

        samples = samples.stream()
                .filter(item -> item.embedding() != null && !item.embedding().isEmpty())
                .sorted(Comparator.comparing((FaceTemplateSample item) ->
                        item.quality() == null ? Double.NEGATIVE_INFINITY : item.quality()).reversed())
                .limit(SAMPLE_LIMIT)
                .toList();

        List<Double> aggregate = averageEmbeddings(samples.stream().map(FaceTemplateSample::embedding).toList());
        double qualityScore = samples.stream()
                .map(FaceTemplateSample::quality)
                .filter(item -> item != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(currentSample.quality());
        return new FaceTemplate(2, "lbp_v2", samples, aggregate, qualityScore, false);
    }

    private String writeTemplate(FaceTemplate template) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "version", template.version(),
                    "algorithm", template.algorithm(),
                    "samples", template.samples(),
                    "template", template.template(),
                    "qualityScore", template.qualityScore()
            ));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化人脸模板失败");
        }
    }

    private FaceTemplate readTemplate(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isBlank()) {
            throw new IllegalStateException("人脸模板为空");
        }
        try {
            String trimmed = embeddingJson.trim();
            if (trimmed.startsWith("[")) {
                List<Double> legacy = objectMapper.readValue(trimmed, new TypeReference<>() {});
                return new FaceTemplate(1, "legacy_gray_32", List.of(new FaceTemplateSample(legacy, null)), legacy, null, true);
            }

            Map<String, Object> payload = objectMapper.readValue(trimmed, new TypeReference<>() {});
            List<Double> template = objectMapper.convertValue(payload.get("template"), new TypeReference<>() {});
            List<FaceTemplateSample> samples = payload.get("samples") == null
                    ? List.of()
                    : objectMapper.convertValue(payload.get("samples"), new TypeReference<>() {});
            Integer version = payload.get("version") == null ? 2 : Integer.parseInt(String.valueOf(payload.get("version")));
            String algorithm = payload.get("algorithm") == null ? "lbp_v2" : String.valueOf(payload.get("algorithm"));
            Double quality = payload.get("qualityScore") == null ? null : Double.parseDouble(String.valueOf(payload.get("qualityScore")));
            return new FaceTemplate(version, algorithm, samples, template, quality, false);
        } catch (Exception e) {
            throw new IllegalStateException("解析人脸模板失败");
        }
    }

    private List<Double> averageEmbeddings(List<List<Double>> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }
        int dimension = embeddings.get(0).size();
        double[] aggregate = new double[dimension];
        int count = 0;
        for (List<Double> vector : embeddings) {
            if (vector == null || vector.size() != dimension) {
                continue;
            }
            for (int i = 0; i < dimension; i++) {
                aggregate[i] += vector.get(i);
            }
            count++;
        }
        if (count == 0) {
            return List.of();
        }
        for (int i = 0; i < aggregate.length; i++) {
            aggregate[i] = aggregate[i] / count;
        }
        normalize(aggregate);
        return box(aggregate);
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.size() != right.size() || left.isEmpty()) {
            return 0.0D;
        }
        double dot = 0.0D;
        double normLeft = 0.0D;
        double normRight = 0.0D;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            normLeft += a * a;
            normRight += b * b;
        }
        if (normLeft == 0 || normRight == 0) {
            return 0.0D;
        }
        return dot / (Math.sqrt(normLeft) * Math.sqrt(normRight));
    }

    private byte[] decodeImageBytes(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("人脸图片不能为空");
        }
        String normalized = imageBase64.trim();
        int commaIdx = normalized.indexOf(',');
        if (normalized.startsWith("data:") && commaIdx > 0) {
            normalized = normalized.substring(commaIdx + 1);
        }
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("图片编码无效，请提供Base64图片");
        }
    }

    private void append(List<Double> target, double[] values) {
        for (double value : values) {
            target.add(value);
        }
    }

    private void normalizeHistogram(double[] histogram, double total) {
        double denominator = Math.max(1.0D, total);
        for (int i = 0; i < histogram.length; i++) {
            histogram[i] = histogram[i] / denominator;
        }
    }

    private double sum(double[] values) {
        double result = 0.0D;
        for (double value : values) {
            result += value;
        }
        return result;
    }

    private void normalize(List<Double> vector) {
        double norm = 0.0D;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0D) {
            return;
        }
        for (int i = 0; i < vector.size(); i++) {
            vector.set(i, vector.get(i) / norm);
        }
    }

    private void normalize(double[] vector) {
        double norm = 0.0D;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0.0D) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }

    private List<Double> box(double[] values) {
        List<Double> result = new ArrayList<>(values.length);
        for (double value : values) {
            result.add(value);
        }
        return result;
    }

    private int grayOf(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (int) Math.round(r * 0.299D + g * 0.587D + b * 0.114D);
    }

    private FaceBounds centerSquareBounds(int width, int height) {
        int size = Math.min(width, height);
        int x = Math.max(0, (width - size) / 2);
        int y = Math.max(0, (height - size) / 2);
        return new FaceBounds(x, y, size);
    }

    private record FaceSample(List<Double> embedding,
                              List<Double> legacyEmbedding,
                              double quality,
                              FaceBounds bounds) {
    }

    private record FaceTemplateSample(List<Double> embedding, Double quality) {
    }

    private record FaceTemplate(int version,
                                String algorithm,
                                List<FaceTemplateSample> samples,
                                List<Double> template,
                                Double qualityScore,
                                boolean legacy) {
    }

    private record FaceBounds(int x, int y, int size) {
    }
}
