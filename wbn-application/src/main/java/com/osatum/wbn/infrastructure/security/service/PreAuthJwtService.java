package com.osatum.wbn.infrastructure.security.service;

import com.osatum.wbn.application.auth.port.PreAuthTokenService;
import com.osatum.wbn.infrastructure.security.PreAuthRequiredException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class PreAuthJwtService implements PreAuthTokenService {
    private static final String STAGE_CLAIM = "stage";
    private static final String STAGE_PRE_AUTH = "PRE_AUTH";
    private static final String UID_CLAIM = "uid";
    private static final String IID_CLAIM = "iid";
    private final SecretKey key;
    private final Duration ttl;

    public PreAuthJwtService(
            @Value("${app.security.jwt.pre-auth-secret:${app.security.jwt.secret}}") String secret,
            @Value("${app.security.jwt.pre-auth-ttl-minutes:5}") long ttlMinutes
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Pre-auth JWT secret must contain at least 32 characters.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Override
    public String createPreAuthToken(Long userId, Long institutionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(ttl);
        JwtBuilder builder = Jwts.builder();
        if (userId != null){
            builder.claim(UID_CLAIM, userId);
        }
        if (institutionId != null){
            builder.claim(IID_CLAIM, institutionId);
        }
        return builder
                .claim(STAGE_CLAIM, STAGE_PRE_AUTH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    @Override
    public long requireUserIdFromPreAuthToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object stage = claims.get(STAGE_CLAIM);
            if (!STAGE_PRE_AUTH.equals(stage)) {
                throw new PreAuthRequiredException();
            }

            Object uid = claims.get(UID_CLAIM);
            if (uid instanceof Integer i) return i.longValue();
            if (uid instanceof Long l) return l;

            throw new PreAuthRequiredException();
        } catch (Exception e) {
            throw new PreAuthRequiredException();
        }
    }

}
