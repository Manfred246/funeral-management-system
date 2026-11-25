package ru.funeralagency.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    public static final long ACCESS_TOKEN_SECONDS = 15 * 60;
    private final SecretKey key;
    private final JwtParser parser;

    public JwtService(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Задайте JWT_SECRET длиной не менее 32 байт UTF-8");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        parser = Jwts.parser().verifyWith(key).sig().clear().add(Jwts.SIG.HS256).and().build();
    }

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = new ArrayList<>();
        for (Role role : user.getRoles()) {
            roles.add(role.name());
        }
        return Jwts.builder().subject(user.getUuid().toString())
                .claim("login", user.getLogin()).claim("roles", roles)
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_SECONDS)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }

    public UUID parseUserUuid(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getSubject() == null || claims.getExpiration() == null) {
            throw new JwtException("Отсутствуют обязательные claims");
        }
        return UUID.fromString(claims.getSubject());
    }
}
