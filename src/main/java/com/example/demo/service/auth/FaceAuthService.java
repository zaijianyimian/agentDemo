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
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FaceAuthService {

    private static final int TARGET_SIZE = 32;

    private final UserFaceProfileMapper userFaceProfileMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.auth.face.threshold:0.72}")
    private double similarityThreshold;

    @Value("${app.auth.face.min-quality:0.06}")
    private double minQualityScore;

    public FaceStatusResponse register(Long userId, String imageBase64) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        FaceVector vector = extractFaceVector(imageBase64);
        List<Double> embedding = vector.embedding();
        double quality = vector.quality();
        if (quality < minQualityScore) {
            throw new IllegalArgumentException("图片质量过低，请使用清晰正脸照片");
        }

        UserFaceProfile existing = getByUserId(userId);
        UserFaceProfile profile = UserFaceProfile.builder()
                .id(existing == null ? null : existing.getId())
                .userId(userId)
                .embedding(writeEmbedding(embedding))
                .vectorDimension(embedding.size())
                .qualityScore(quality)
                .enabled(true)
                .build();
        if (existing == null) {
            userFaceProfileMapper.insert(profile);
        } else {
            userFaceProfileMapper.updateById(profile);
        }
        if (!Boolean.TRUE.equals(user.getFaceAuthEnabled())) {
            user.setFaceAuthEnabled(true);
            userAccountMapper.updateById(user);
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
        List<Double> source = readEmbedding(profile.getEmbedding());
        List<Double> current = extractFaceVector(imageBase64).embedding();
        return cosineSimilarity(source, current);
    }

    public void verifyForLogin(Long userId, String imageBase64) {
        double similarity = verifySimilarity(userId, imageBase64);
        if (similarity < similarityThreshold) {
            throw new IllegalArgumentException(String.format("人脸验证失败（相似度 %.3f，阈值 %.3f）", similarity, similarityThreshold));
        }
    }

    private UserFaceProfile getByUserId(Long userId) {
        return userFaceProfileMapper.selectOne(new LambdaQueryWrapper<UserFaceProfile>()
                .eq(UserFaceProfile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private FaceVector extractFaceVector(String imageBase64) {
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

        BufferedImage cropped = cropToCenterSquare(raw);
        BufferedImage scaled = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(cropped, 0, 0, TARGET_SIZE, TARGET_SIZE, null);
        } finally {
            graphics.dispose();
        }

        double[] rawVector = new double[TARGET_SIZE * TARGET_SIZE];
        int idx = 0;
        for (int y = 0; y < TARGET_SIZE; y++) {
            for (int x = 0; x < TARGET_SIZE; x++) {
                int rgb = scaled.getRGB(x, y);
                int gray = rgb & 0xff;
                rawVector[idx++] = gray / 255.0D;
            }
        }
        double quality = evaluateQuality(rawVector);
        normalize(rawVector);
        return new FaceVector(java.util.Arrays.stream(rawVector).boxed().toList(), quality);
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

    private String writeEmbedding(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化人脸向量失败");
        }
    }

    private List<Double> readEmbedding(String embeddingJson) {
        try {
            return objectMapper.readValue(embeddingJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析人脸向量失败");
        }
    }

    private double evaluateQuality(double[] vector) {
        if (vector == null || vector.length == 0) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (double value : vector) {
            sum += value;
        }
        double mean = sum / vector.length;
        double variance = 0.0D;
        for (double value : vector) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance = variance / vector.length;
        return Math.sqrt(variance);
    }

    private double cosineSimilarity(List<Double> left, List<Double> right) {
        if (left.size() != right.size() || left.isEmpty()) {
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

    private void normalize(double[] vector) {
        double norm = 0.0D;
        for (double value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }

    private BufferedImage cropToCenterSquare(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int size = Math.min(width, height);
        int x = Math.max(0, (width - size) / 2);
        int y = Math.max(0, (height - size) / 2);
        return source.getSubimage(x, y, size, size);
    }

    private record FaceVector(List<Double> embedding, double quality) {}
}
