package com.osatum.wbn.infrastructure.security.service;

import com.osatum.wbn.application.auth.port.AccessTokenService;
import com.osatum.wbn.domain.model.institution.Institution;
import com.osatum.wbn.domain.model.institution.InstitutionRole;
import com.osatum.wbn.domain.model.institution.InstitutionType;
import com.osatum.wbn.domain.model.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtService implements AccessTokenService {

    private final SecretKey key;
    private final String issuer;
    private final long accessTtlMinutes;
    private final String rawSecret;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.access-ttl-minutes}") long accessTtlMinutes
    ) {
        this.rawSecret = secret;
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTtlMinutes = accessTtlMinutes;
    }

    @PostConstruct
    void validateConfig() {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is empty. Set app.security.jwt.secret (e.g. env APP_JWT_SECRET).");
        }
        if ("CHANGE_ME_TO_A_LONG_RANDOM_SECRET_32CHARS_MIN".equals(rawSecret)) {
            throw new IllegalStateException("JWT secret is still a placeholder. Set a real secret (32+ chars) via env/secret manager.");
        }
        if (rawSecret.length() < 32) {
            throw new IllegalStateException("JWT secret too short. Use 32+ characters (preferably much longer).");
        }
    }



    public String createInstitutionAccessToken(Institution institution, InstitutionType institutionType, InstitutionRole institutionRole) {
        return createInstitutionAccessToken(institution, institutionType, institutionRole, null);
    }

    @Override
    public String createInstitutionAccessToken(Institution institution, InstitutionType institutionType, InstitutionRole institutionRole, Long loginSessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
        Map<String, Object> claims = new HashMap<>();
        claims.put(ClaimsConstant.CLAIMS_TYPE, ClaimsConstant.CLAIMS_TYPE_INSTITUTION);
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_ID, institution.getId());
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_TYPE, institutionType.name());
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_NAME, institution.getName());
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_ROLE, institutionRole.name());
        if (loginSessionId != null) {
            claims.put(ClaimsConstant.CLAIMS_LOGIN_SESSION_ID, loginSessionId);
        }

        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(institution.getId()))
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public String createUserAccessToken(AppUser user, Long institutionId, InstitutionType institutionType, InstitutionRole institutionRole) {
        return createUserAccessToken(user, institutionId, institutionType, institutionRole, null);
    }

    @Override
    public String createUserAccessToken(AppUser user, Long institutionId, InstitutionType institutionType, InstitutionRole institutionRole, Long loginSessionId) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtlMinutes, ChronoUnit.MINUTES);
        Map<String, Object> claims = new HashMap<>();
        claims.put(ClaimsConstant.CLAIMS_TYPE, ClaimsConstant.CLAIMS_TYPE_USER);
        claims.put(ClaimsConstant.CLAIMS_EMAIL, user.getEmail());
        claims.put(ClaimsConstant.CLAIMS_ROLES, user.getRoles());
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_ID, institutionId);
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_TYPE, institutionType.name());
        claims.put(ClaimsConstant.CLAIMS_INSTITUTION_ROLE, institutionRole.name());
        if (loginSessionId != null) {
            claims.put(ClaimsConstant.CLAIMS_LOGIN_SESSION_ID, loginSessionId);
        }

        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getId().toString())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


}
