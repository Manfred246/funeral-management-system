package ru.funeralagency.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String SECRET = "unit-test-key-only-not-for-production-1234567890";

    @Test
    void jwtContainsOnlyIdentityRolesAndLifetime() {
        User user = new User();
        user.setLogin("viewer");
        user.setRoles(Set.of(Role.VIEWER));
        String token = new JwtService(SECRET).createAccessToken(user);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals(user.getUuid().toString(), claims.getSubject());
        assertEquals("viewer", claims.get("login"));
        assertEquals(List.of("VIEWER"), claims.get("roles"));
        assertEquals(900, (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000);
        assertEquals(Set.of("sub", "login", "roles", "iat", "exp"), claims.keySet());
    }

    @Test
    void invalidSignatureExpiredMalformedAndUnsignedTokensAreRejected() {
        JwtService service = new JwtService(SECRET);
        User user = new User();
        user.setRoles(Set.of(Role.ADMIN));
        String wrongSignature = new JwtService("another-test-key-not-for-production-1234567890").createAccessToken(user);
        String expired = Jwts.builder().subject(user.getUuid().toString())
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        String unsigned = Jwts.builder().subject(user.getUuid().toString()).compact();
        for (String token : List.of(wrongSignature, expired, unsigned, "malformed")) {
            assertThrows(JwtException.class, () -> service.parseUserUuid(token));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short", "                                "})
    void missingOrWeakSecretFailsStartup(String secret) {
        assertThrows(IllegalStateException.class, () -> new JwtService(secret));
    }
}
