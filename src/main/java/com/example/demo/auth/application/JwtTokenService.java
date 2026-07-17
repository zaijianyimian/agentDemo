package com.example.demo.auth.application;

import com.example.demo.auth.domain.UserAccount;
import com.example.demo.infrastructure.properties.AuthSecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
/**
 * JWT 令牌服务。负责 access/refresh token 的签发、解码以及过期秒数查询，
 * token 中携带 userId、tokenVersion 等关键声明用于版本失效控制。
 */
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthSecurityProperties securityProperties;

    /**
     * 生成 access token。有效期取自 {@link AuthSecurityProperties#getAccessTokenMinutes()}。
     */
    public String generateAccessToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.getAccessTokenMinutes(), ChronoUnit.MINUTES);
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("type", AuthConstants.TOKEN_TYPE_ACCESS)
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("tokenVersion", safeTokenVersion(user))
                .claim("roles", List.of(user.getRole() == null ? "USER" : user.getRole()))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
    }

    /**
     * 生成 refresh token。有效期以天为单位由配置控制；token 中不含角色声明。
     */
    public String generateRefreshToken(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(securityProperties.getRefreshTokenDays(), ChronoUnit.DAYS);
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();

        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(securityProperties.getIssuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.getUsername())
                .claim("type", AuthConstants.TOKEN_TYPE_REFRESH)
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("tokenVersion", safeTokenVersion(user))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet)).getTokenValue();
    }

    /**
     * 解码并校验 JWT 签名与有效期。失败时抛出 JwtException。
     */
    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    /**
     * 返回 access token 的有效期（秒），由配置换算得到。
     */
    public long getAccessTokenExpiresInSeconds() {
        return securityProperties.getAccessTokenMinutes() * 60;
    }

    private int safeTokenVersion(UserAccount user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
