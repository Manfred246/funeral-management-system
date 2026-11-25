package ru.funeralagency.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.AuthTokensDto;
import ru.funeralagency.dto.AuthUserDto;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.RefreshSession;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.RefreshSessionRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.PasswordPolicy;
import ru.funeralagency.model.AuditEventType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {
    public static final int MAX_FAILED_ATTEMPTS = 5;
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final String dummyPasswordHash;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshSessionRepository sessions,
                       PasswordEncoder passwordEncoder, JwtService jwtService, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    // Ошибка пароля не должна откатывать сохранённый счётчик неудачных входов.
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public AuthTokensDto login(String login, String password) {
        User user = users.findByLogin(login.trim()).orElse(null);
        if (user == null || !user.isEnabled() || user.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
            // Для неизвестного/закрытого login тоже выполняем BCrypt, без изменения его состояния.
            if (password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72) {
                passwordEncoder.matches(password, dummyPasswordHash);
            }
            throw invalidCredentials();
        }
        if (!matches(password, user)) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            user.setUpdatedAt(now());
            users.saveAndFlush(user);
            throw invalidCredentials();
        }
        user.setFailedLoginAttempts(0);
        user.setUpdatedAt(now());
        users.save(user);

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshSession session = new RefreshSession();
        session.setUser(user);
        session.setTokenHash(hashToken(token));
        session.setExpiresAt(now().plusDays(7));
        sessions.save(session);
        audit.record(AuditEventType.LOGIN, "USER", user.getUuid(), user.getId());
        return tokens(user, token);
    }

    @Transactional(readOnly = true)
    public AuthTokensDto refresh(String token) {
        RefreshSession session = sessions.findByTokenHash(hashToken(token)).orElseThrow(this::invalidCredentials);
        if (session.isRevoked() || !session.getExpiresAt().isAfter(now()) || !session.getUser().isEnabled()) {
            throw invalidCredentials();
        }
        return tokens(session.getUser(), token);
    }

    @Transactional
    public void logout(String token) {
        sessions.findByTokenHash(hashToken(token)).ifPresent(session -> {
            session.setRevoked(true);
            sessions.save(session);
        });
    }

    @Transactional(readOnly = true)
    public AuthUserDto me(UUID uuid) {
        return userDto(enabledUser(uuid));
    }

    @Transactional
    public void changePassword(UUID uuid, String currentPassword, String newPassword) {
        User user = enabledUser(uuid);
        if (!matches(currentPassword, user)) {
            throw invalidCredentials();
        }
        PasswordPolicy.validate(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangeRequired(false);
        user.setFailedLoginAttempts(0);
        user.setUpdatedAt(now());
        users.save(user);
        sessions.revokeAllForUser(user.getId());
    }

    @Transactional
    public void createInitialAdmin(String login, String password, String name) {
        if (users.count() != 0) {
            return;
        }
        if (login.isBlank() && password.isBlank() && name.isBlank()) {
            return;
        }
        if (login.isBlank() || password.isBlank() || name.isBlank()) {
            throw new IllegalStateException("Для первого ADMIN задайте APP_ADMIN_LOGIN, APP_ADMIN_PASSWORD и APP_ADMIN_NAME");
        }
        if (login.trim().length() > 100 || name.trim().length() > 255) {
            throw new ValidationException("Логин или имя первого ADMIN слишком длинные");
        }
        PasswordPolicy.validate(password);
        User user = new User();
        user.setLogin(login.trim());
        user.setName(name.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangeRequired(true);
        user.setRoles(Set.of(Role.ADMIN));
        users.saveAndFlush(user);
        audit.record(AuditEventType.USER_CREATED, "USER", user.getUuid());
    }

    private boolean matches(String password, User user) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwordEncoder.matches(password, user.getPasswordHash());
    }

    private AuthTokensDto tokens(User user, String refreshToken) {
        return new AuthTokensDto(jwtService.createAccessToken(user), refreshToken,
                "Bearer", JwtService.ACCESS_TOKEN_SECONDS, userDto(user));
    }

    private AuthUserDto userDto(User user) {
        return new AuthUserDto(user.getUuid(), user.getLogin(), user.getName(), Set.copyOf(user.getRoles()),
                user.isEnabled(), user.isPasswordChangeRequired());
    }

    private User enabledUser(UUID uuid) {
        User user = users.findByUuid(uuid).orElseThrow(this::invalidCredentials);
        if (!user.isEnabled()) {
            throw invalidCredentials();
        }
        return user;
    }

    private BadCredentialsException invalidCredentials() {
        return new BadCredentialsException("Неверные учётные данные или недействительный токен");
    }

    private String hashToken(String token) {
        if (token == null || token.isBlank() || token.length() > 128) {
            throw invalidCredentials();
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 недоступен", exception);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
