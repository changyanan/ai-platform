package com.aiplatform.server.security;

import com.aiplatform.server.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_VERSION = "ver";
    private static final String CLAIM_PASSWORD_CHANGE = "pc";

    private final JwtProperties properties;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("AI_PLATFORM_JWT_SECRET 必须至少 32 字节");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(UserPrincipal user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(user.getUsername())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.getRole())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(CLAIM_PASSWORD_CHANGE, user.isMustChangePassword())
                .signWith(signingKey)
                .compact();
    }

    public Claims parse(String token) throws JwtException, IllegalArgumentException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getUserId(Claims claims) {
        Number uid = claims.get(CLAIM_USER_ID, Number.class);
        if (uid == null) {
            throw new JwtException("缺少用户标识");
        }
        return uid.longValue();
    }

    public int getTokenVersion(Claims claims) {
        Number version = claims.get(CLAIM_TOKEN_VERSION, Number.class);
        return version == null ? 0 : version.intValue();
    }

    public long getExpiresInSeconds() {
        return properties.getExpirationMinutes() * 60;
    }
}
