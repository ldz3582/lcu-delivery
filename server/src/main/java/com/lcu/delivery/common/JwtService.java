package com.lcu.delivery.common;

import com.lcu.delivery.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(props.tokenExpireHours());
    }

    public String issue(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.id()))
                .claim("role", user.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public AuthUser parse(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            Claims c = jws.getPayload();
            return new AuthUser(Long.valueOf(c.getSubject()), String.valueOf(c.get("role")));
        } catch (Exception e) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
        }
    }
}
