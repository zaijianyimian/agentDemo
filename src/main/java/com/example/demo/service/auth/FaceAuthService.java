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
    private static final double DEFAULT_THRESHOLD = 0.85D;
    private static final double MIN_QUALITY_SCORE = 0.10D;

    private final UserFaceProfileMapper userFaceProfileMapper;
    private final UserAccountMapper userAccountMapper;
    private final ObjectMapper objectMapper;

    public FaceStatusResponse register(Long userId, String imageBase64) {
        UserAccount user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        List<Double> embedding = extractEmbedding(imageBase64);
        double quality = evaluateQuality(embedding);
        if (quality < MIN_QUALITY_SCORE) {
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
        List<Double> current = extractEmbedding(imageBase64);
        return cosineSimilarity(source, current);
    }

    public void verifyForLogin(Long userId, String imageBase64) {
        double similarity = verifySimilarity(userId, imageBase64);
        if (similarity < DEFAULT_THRESHOLD) {
            throw new IllegalArgumentException("人脸验证失败，请重试");
        }
    }

    private UserFaceProfile getByUserId(Long userId) {
        return userFaceProfileMapper.selectOne(new LambdaQueryWrapper<UserFaceProfile>()
                .eq(UserFaceProfile::getUserId, userId)
                .last("LIMIT 1"));
    }

    private List<Double> extractEmbedding(String imageBase64) {
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

        BufferedImage scaled = new BufferedImage(TARGET_SIZE, TARGET_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.drawImage(raw, 0, 0, TARGET_SIZE, TARGET_SIZE, null);
        } finally {
            graphics.dispose();
        }

        double[] vector = new double[TARGET_SIZE * TARGET_SIZE];
        int idx = 0;
        for (int y = 0; y < TARGET_SIZE; y++) {
            for (int x = 0; x < TARGET_SIZE; x++) {
                int rgb = scaled.getRGB(x, y);
                int gray = rgb & 0xff;
                vector[idx++] = gray / 255.0D;
            }
        }
        normalize(vector);
        return java.util.Arrays.stream(vector).boxed().toList();
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

    private double evaluateQuality(List<Double> embedding) {
        double mean = embedding.stream().mapToDouble(Double::doubleValue).average().orElse(0.0D);
        double variance = embedding.stream()
                .mapToDouble(item -> Math.pow(item - mean, 2))
                .average()
                .orElse(0.0D);
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
}
