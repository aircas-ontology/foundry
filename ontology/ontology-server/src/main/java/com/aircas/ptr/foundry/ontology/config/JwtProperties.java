package com.aircas.ptr.foundry.ontology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * HS256 签名密钥
     */
    private String secret;

    /**
     * accessToken 有效期（天）
     */
    private long accessTokenExpireDays = 7;

    /**
     * refreshToken 有效期（天）
     */
    private long refreshTokenExpireDays = 30;

    public long getAccessTokenExpireMs() {
        return accessTokenExpireDays * 24 * 60 * 60 * 1000;
    }

    public long getRefreshTokenExpireMs() {
        return refreshTokenExpireDays * 24 * 60 * 60 * 1000;
    }
}
