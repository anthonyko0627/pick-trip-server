package travel_agency.pick_trip.gloal.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    /** 토큰 용도 클레임. 리프레시 토큰이 액세스 토큰으로 사용되는 것을 막기 위해 넣는다. */
    public static final String CLAIM_TOKEN_TYPE = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";

    @Value("${jwt.secret-key}")
    private String secretKey;

    @Value("${jwt.access-token-expire-time}")
    private Long accessTokenExpireTimeMs;

    @Value("${jwt.refresh-token-expire-time}")
    private Long refreshTokenExpireTimeDays;

    private SecretKey cachedKey;

    @PostConstruct
    private void init() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("jwt.secret-key는 최소 32바이트여야 합니다.");
        }
        this.cachedKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(JwtUserInfo info) {
        return generateToken(info, TokenType.ACCESS);
    }

    public String generateRefreshToken(JwtUserInfo info) {
        return generateToken(info, TokenType.REFRESH);
    }

    public Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .verifyWith(cachedKey)
                .build()
                .parseSignedClaims(token);
    }

    private String generateToken(JwtUserInfo info, TokenType type) {
        Instant now = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(info.uid().toString())
                .issuedAt(Date.from(now));

        if (type == TokenType.ACCESS) {
            builder.claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                    .claim("name", info.nickname())
                    .claim("email", info.email())
                    .claim("role", info.role())
                    .expiration(Date.from(now.plus(Duration.ofMillis(accessTokenExpireTimeMs))));
        } else {
            builder.expiration(Date.from(now.plus(Duration.ofDays(refreshTokenExpireTimeDays))));
        }

        return builder.signWith(cachedKey, Jwts.SIG.HS256).compact();
    }

    enum TokenType {
        ACCESS, REFRESH
    }
}
