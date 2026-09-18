package com.aircas.ptr.foundry.ontology.utils;

import com.aircas.ptr.foundry.ontology.config.JwtProperties;
import com.aircas.ptr.foundry.ontology.model.common.Constants;
import com.aircas.ptr.foundry.ontology.model.dto.JwtDTO;
import com.aircas.ptr.foundry.ontology.model.dto.UserContextDTO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_USER_NAME = "username";
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    private final JwtProperties jwtProperties;

    public JwtDTO generateTokens(UserContextDTO userContext) {
        String accessToken = withBearer(createToken(userContext, Constants.JWT_TOKEN_TYPE_ACCESS, jwtProperties.getAccessTokenExpireMs()));
        String refreshToken = withBearer(createToken(userContext, Constants.JWT_TOKEN_TYPE_REFRESH, jwtProperties.getRefreshTokenExpireMs()));

        return JwtDTO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public boolean validateAccessToken(String token) {
        String rawToken = resolveBearerToken(token);
        if (rawToken == null) {
            return false;
        }
        try {
            Claims claims = parseClaims(rawToken);
            return Constants.JWT_TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE));
        } catch (Exception e) {
            log.error("access-token校验失败: {}", e.getMessage());
            return false;
        }
    }

    public UserContextDTO parseUserContext(String token) {
        String rawToken = resolveBearerToken(token);
        Claims claims = parseClaims(rawToken);
        return UserContextDTO.builder()
                .userId(claims.get(CLAIM_USER_ID, Integer.class))
                .username(claims.get(CLAIM_USER_NAME, String.class))
                .build();
    }

    private String createToken(UserContextDTO userContext, String tokenType, long expireMs) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMs);
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userContext.getUserId());
        claims.put(CLAIM_USER_NAME, userContext.getUsername());
        claims.put(CLAIM_TOKEN_TYPE, tokenType);
        SecretKey key = getSigningKey();
        return Jwts.builder()
                .claims(claims)
                .subject(userContext.getUsername())
                .issuedAt(now)
                .expiration(expireAt)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private Claims parseClaims(String rawToken) {
        SecretKey key = getSigningKey();
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(rawToken)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private String withBearer(String rawToken) {
        return Constants.BEARER_PREFIX + rawToken;
    }

    /**
     * 解析并校验 Bearer 前缀，返回原始 JWT；不合法时返回 null。
     */
    private String resolveBearerToken(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        String value = token.trim();
        if (!value.regionMatches(true, 0, Constants.BEARER_PREFIX, 0, Constants.BEARER_PREFIX.length())) {
            return null;
        }
        String rawToken = value.substring(Constants.BEARER_PREFIX.length()).trim();
        if (StringUtils.isBlank(rawToken)) {
            return null;
        }
        return rawToken;
    }
}
