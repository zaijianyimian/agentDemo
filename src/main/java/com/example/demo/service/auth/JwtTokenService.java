package com.example.demo.service.auth;

import com.example.demo.entity.UserAccount;
import com.example.demo.properties.AuthSecurityProperties;
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
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthSecurityProperties securityProperties;

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

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public long getAccessTokenExpiresInSeconds() {
        return securityProperties.getAccessTokenMinutes() * 60;
    }

    private int safeTokenVersion(UserAccount user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
