package ru.funeralagency.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.RefreshSession;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.RefreshSessionRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository users;
    @Mock private RefreshSessionRepository sessions;
    private BCryptPasswordEncoder encoder;
    private AuthService service;
    private JwtService jwt;
    private User user;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoder(4);
        jwt = new JwtService("unit-test-key-only-not-for-production-1234567890");
        service = new AuthService(users, sessions, encoder, jwt);
        user = new User();
        user.setId(1L);
        user.setLogin("viewer");
        user.setName("Пользователь");
        user.setPasswordHash(encoder.encode("old-password"));
        user.setRoles(Set.of(Role.VIEWER));
    }

    @Test
    void bootstrapStoresOnlyBcryptAndRequiresPasswordChange() {
        service.createInitialAdmin(" admin ", "temporary-password", " Администратор ");
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        User admin = saved.getValue();
        assertEquals("admin", admin.getLogin());
        assertEquals("Администратор", admin.getName());
        assertNotEquals("temporary-password", admin.getPasswordHash());
        assertTrue(admin.getPasswordHash().startsWith("$2"));
        assertTrue(encoder.matches("temporary-password", admin.getPasswordHash()));
        assertTrue(admin.isPasswordChangeRequired());
        assertTrue(admin.isEnabled());
        assertEquals(Set.of(Role.ADMIN), admin.getRoles());
        assertNotNull(admin.getUuid());
    }

    @Test
    void bootstrapDoesNotCreateDefaultUserOrOverwriteExistingUsers() {
        service.createInitialAdmin("", "", "");
        verify(users, never()).saveAndFlush(any());
        when(users.count()).thenReturn(1L);
        service.createInitialAdmin("admin", "temporary-password", "Admin");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void partialBootstrapConfigurationFailsClearly() {
        assertThrows(IllegalStateException.class, () -> service.createInitialAdmin("admin", "", "Admin"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void loginResetsFailuresAndStoresOnlyHashOfRandomRefreshToken() throws Exception {
        user.setFailedLoginAttempts(3);
        when(users.findByLogin("viewer")).thenReturn(Optional.of(user));
        var tokens = service.login(" viewer ", "old-password");
        assertEquals(user.getUuid(), jwt.parseUserUuid(tokens.accessToken()));
        assertEquals("Bearer", tokens.tokenType());
        assertEquals(900, tokens.expiresIn());
        assertEquals(0, user.getFailedLoginAttempts());
        ArgumentCaptor<RefreshSession> saved = ArgumentCaptor.forClass(RefreshSession.class);
        verify(sessions).save(saved.capture());
        var session = saved.getValue();
        assertSame(user, session.getUser());
        assertEquals(43, tokens.refreshToken().length());
        assertEquals(64, session.getTokenHash().length());
        assertNotEquals(tokens.refreshToken(), session.getTokenHash());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(tokens.refreshToken().getBytes(StandardCharsets.UTF_8))), session.getTokenHash());
        assertTrue(session.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC).plusDays(6)));
        assertTrue(session.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC).plusDays(8)));
        assertFalse(session.isRevoked());
        String json = Jackson2ObjectMapperBuilder.json().build().writeValueAsString(tokens);
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("failedLoginAttempts"));
        assertFalse(json.contains(user.getPasswordHash()));
    }

    @Test
    void wrongPasswordsIncrementCounterAndFifthFailureBlocksEvenCorrectPassword() {
        when(users.findByLogin("viewer")).thenReturn(Optional.of(user));
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThrows(BadCredentialsException.class, () -> service.login("viewer", "wrong-password"));
            assertEquals(attempt, user.getFailedLoginAttempts());
        }
        assertThrows(BadCredentialsException.class, () -> service.login("viewer", "old-password"));
        assertEquals(5, user.getFailedLoginAttempts());
        verify(users, times(5)).saveAndFlush(user);
        verifyNoInteractions(sessions);
    }

    @Test
    void disabledUserAndUnknownLoginCannotLogin() {
        user.setEnabled(false);
        when(users.findByLogin("viewer")).thenReturn(Optional.of(user));
        when(users.findByLogin("missing")).thenReturn(Optional.empty());
        var disabled = assertThrows(BadCredentialsException.class, () -> service.login("viewer", "old-password"));
        var missing = assertThrows(BadCredentialsException.class, () -> service.login("missing", "old-password"));
        assertEquals(disabled.getMessage(), missing.getMessage());
        verifyNoInteractions(sessions);
    }

    @Test
    void refreshKeepsSameTokenAndLogoutRevokesIt() {
        RefreshSession session = session();
        when(sessions.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        var refreshed = service.refresh("opaque-refresh-token");
        assertEquals("opaque-refresh-token", refreshed.refreshToken());
        assertEquals(user.getUuid(), jwt.parseUserUuid(refreshed.accessToken()));
        service.logout("opaque-refresh-token");
        assertTrue(session.isRevoked());
        verify(sessions).save(session);
        assertThrows(BadCredentialsException.class, () -> service.refresh("opaque-refresh-token"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"expired", "revoked", "disabled"})
    void invalidRefreshStateIsRejected(String state) {
        RefreshSession session = session();
        if ("expired".equals(state)) session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        if ("revoked".equals(state)) session.setRevoked(true);
        if ("disabled".equals(state)) user.setEnabled(false);
        when(sessions.findByTokenHash(anyString())).thenReturn(Optional.of(session));
        assertThrows(BadCredentialsException.class, () -> service.refresh("opaque-refresh-token"));
        verify(sessions, never()).save(any());
    }

    @Test
    void unknownRefreshIsRejectedAndLogoutIsIdempotent() {
        when(sessions.findByTokenHash(anyString())).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> service.refresh("unknown"));
        assertDoesNotThrow(() -> service.logout("unknown"));
        verify(sessions, never()).save(any());
    }

    @Test
    void passwordChangeClearsTemporaryFlagAndRevokesSessions() {
        user.setPasswordChangeRequired(true);
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        service.changePassword(user.getUuid(), "old-password", "new-password");
        assertFalse(user.isPasswordChangeRequired());
        assertFalse(encoder.matches("old-password", user.getPasswordHash()));
        assertTrue(encoder.matches("new-password", user.getPasswordHash()));
        verify(sessions).revokeAllForUser(1L);
        when(users.findByLogin("viewer")).thenReturn(Optional.of(user));
        assertThrows(BadCredentialsException.class, () -> service.login("viewer", "old-password"));
        assertNotNull(service.login("viewer", "new-password").accessToken());
    }

    @Test
    void wrongCurrentPasswordDoesNotChangeHashOrRevokeSessions() {
        String previous = user.getPasswordHash();
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        assertThrows(BadCredentialsException.class,
                () -> service.changePassword(user.getUuid(), "wrong-password", "new-password"));
        assertEquals(previous, user.getPasswordHash());
        verifyNoInteractions(sessions);
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "        ",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "яяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяяя"})
    void newPasswordPolicyChecksLengthAndBcryptByteLimit(String password) {
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        assertThrows(ValidationException.class,
                () -> service.changePassword(user.getUuid(), "old-password", password));
        verifyNoInteractions(sessions);
    }

    @Test
    void meReturnsSafeUserInfo() throws Exception {
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        var dto = service.me(user.getUuid());
        assertEquals(user.getUuid(), dto.uuid());
        assertEquals(Set.of(Role.VIEWER), dto.roles());
        String json = Jackson2ObjectMapperBuilder.json().build().writeValueAsString(dto);
        assertFalse(json.contains("passwordHash"));
        assertFalse(json.contains("failedLoginAttempts"));
    }

    private RefreshSession session() {
        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(1));
        return session;
    }
}
