package com.example.demo.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.demo.dto.auth.AuthTokenResponse;
import com.example.demo.dto.auth.GithubAuthorizeResponse;
import com.example.demo.dto.auth.GithubExchangeResponse;
import com.example.demo.entity.OauthAccount;
import com.example.demo.entity.UserAccount;
import com.example.demo.mapper.OauthAccountMapper;
import com.example.demo.mapper.UserAccountMapper;
import com.example.demo.properties.GithubOAuthProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubOAuthService {

    private static final String PROVIDER = "github";

    private final GithubOAuthProperties properties;
    private final OAuthStateService oAuthStateService;
    private final OauthAccountMapper oauthAccountMapper;
    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    private final WebClient webClient = WebClient.builder().build();

    public GithubAuthorizeResponse createAuthorizeUrl(String redirectPath) {
        ensureGithubEnabled();

        String state = oAuthStateService.issueState(redirectPath, properties.getStateTtlSeconds());

        URI uri = UriComponentsBuilder.fromUriString(properties.getAuthorizeUri())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                .build(true)
                .toUri();

        return GithubAuthorizeResponse.builder()
                .authorizationUrl(uri.toString())
                .stateExpiresIn(properties.getStateTtlSeconds())
                .build();
    }

    public GithubExchangeResponse exchangeCode(String code, String state) {
        ensureGithubEnabled();

        String redirectPath = oAuthStateService.consumeRedirectPath(state);
        String accessToken = exchangeToken(code, state);

        Map<String, Object> githubUser = fetchGithubUser(accessToken);
        String providerUserId = String.valueOf(githubUser.get("id"));
        if (providerUserId == null || "null".equals(providerUserId)) {
            throw new IllegalArgumentException("GitHub 用户信息异常：缺少 id");
        }

        String login = asString(githubUser.get("login"));
        String name = asString(githubUser.get("name"));
        String email = resolveVerifiedGithubEmail(accessToken);

        OauthAccount oauthAccount = oauthAccountMapper.selectOne(new LambdaQueryWrapper<OauthAccount>()
                .eq(OauthAccount::getProvider, PROVIDER)
                .eq(OauthAccount::getProviderUserId, providerUserId)
                .last("LIMIT 1"));

        UserAccount user;
        if (oauthAccount != null) {
            user = userAccountMapper.selectById(oauthAccount.getUserId());
            if (user == null) {
                throw new IllegalArgumentException("OAuth 关联用户不存在");
            }
        } else {
            user = findByEmail(email);
            if (user == null) {
                user = createUserFromGithub(login, name, email, providerUserId);
            }
            oauthAccount = OauthAccount.builder()
                    .userId(user.getId())
                    .provider(PROVIDER)
                    .providerUserId(providerUserId)
                    .build();
            oauthAccountMapper.insert(oauthAccount);
        }

        oauthAccount.setLogin(login);
        oauthAccountMapper.updateById(oauthAccount);

        user.setLastLoginTime(LocalDateTime.now());
        if (email != null && !email.isBlank()) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                user.setEmail(email);
            }
            user.setEmailVerified(true);
        }
        if ((user.getDisplayName() == null || user.getDisplayName().isBlank()) && name != null && !name.isBlank()) {
            user.setDisplayName(name);
        }
        userAccountMapper.updateById(user);

        AuthTokenResponse token = authService.issueTokensOrChallenge(user);
        return GithubExchangeResponse.builder()
                .token(token)
                .redirectPath(redirectPath)
                .build();
    }

    private String exchangeToken(String code, String state) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", properties.getClientId());
        body.add("client_secret", properties.getClientSecret());
        body.add("code", code);
        body.add("redirect_uri", properties.getRedirectUri());
        body.add("state", state);

        Map<String, Object> tokenResp = webClient.post()
                .uri(properties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (tokenResp == null || tokenResp.get("access_token") == null) {
            String err = tokenResp == null ? "unknown" : asString(tokenResp.get("error_description"));
            throw new IllegalArgumentException("GitHub token 交换失败: " + err);
        }

        return asString(tokenResp.get("access_token"));
    }

    private Map<String, Object> fetchGithubUser(String accessToken) {
        Map<String, Object> user = webClient.get()
                .uri(properties.getUserUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (user == null) {
            throw new IllegalArgumentException("获取 GitHub 用户信息失败");
        }
        return user;
    }

    private String resolveVerifiedGithubEmail(String accessToken) {
        List<Map<String, Object>> emails = webClient.get()
                .uri(properties.getEmailsUri())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .bodyToMono(List.class)
                .block();

        if (emails == null || emails.isEmpty()) {
            return null;
        }

        for (Map<String, Object> item : emails) {
            boolean primary = Boolean.TRUE.equals(item.get("primary"));
            boolean verified = Boolean.TRUE.equals(item.get("verified"));
            String itemEmail = asString(item.get("email"));
            if (primary && verified && itemEmail != null && !itemEmail.isBlank()) {
                return itemEmail.toLowerCase();
            }
        }

        for (Map<String, Object> item : emails) {
            boolean verified = Boolean.TRUE.equals(item.get("verified"));
            String itemEmail = asString(item.get("email"));
            if (verified && itemEmail != null && !itemEmail.isBlank()) {
                return itemEmail.toLowerCase();
            }
        }
        return null;
    }

    private UserAccount createUserFromGithub(String login, String name, String email, String providerUserId) {
        String username = generateUniqueUsername(login, providerUserId);
        UserAccount user = UserAccount.builder()
                .username(username)
                .email((email == null || email.isBlank()) ? (username + "@github.local") : email.toLowerCase())
                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                .displayName((name == null || name.isBlank()) ? username : name)
                .role(AuthConstants.DEFAULT_USER_ROLE)
                .enabled(true)
                .emailVerified(email != null && !email.isBlank())
                .faceAuthEnabled(false)
                .tokenVersion(0)
                .build();
        userAccountMapper.insert(user);
        return user;
    }

    private String generateUniqueUsername(String login, String providerUserId) {
        String base = (login == null || login.isBlank()) ? ("github_" + providerUserId) : login;
        String candidate = normalizeUsername(base);
        if (!usernameExists(candidate)) {
            return candidate;
        }

        for (int i = 1; i <= 20; i++) {
            String next = normalizeUsername(base + "_" + i);
            if (!usernameExists(next)) {
                return next;
            }
        }
        return "github_" + providerUserId + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    private UserAccount findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getEmail, email.toLowerCase())
                .last("LIMIT 1"));
    }

    private boolean usernameExists(String username) {
        return userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, username)) > 0;
    }

    private String normalizeUsername(String value) {
        String cleaned = value == null ? "github_user" : value.trim().toLowerCase();
        cleaned = cleaned.replaceAll("[^a-z0-9_]", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        if (cleaned.length() < 3) {
            cleaned = (cleaned + "___").substring(0, 3);
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void ensureGithubEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalArgumentException("GitHub 登录未启用");
        }
        if (properties.getClientId() == null || properties.getClientId().isBlank()
                || properties.getClientSecret() == null || properties.getClientSecret().isBlank()) {
            throw new IllegalArgumentException("GitHub OAuth 配置不完整，请设置 client-id 和 client-secret");
        }
    }
}
