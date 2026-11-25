package ru.funeralagency.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.AdminUserCreateDto;
import ru.funeralagency.dto.AdminUserDto;
import ru.funeralagency.dto.AdminUserPageDto;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.mapper.AdminUserMapper;
import ru.funeralagency.model.AuditEventType;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.RefreshSessionRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.PasswordPolicy;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminUserService {
    private final UserRepository users;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public AdminUserService(UserRepository users, RefreshSessionRepository sessions, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public AdminUserPageDto findPage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new ValidationException("Укажите page >= 0 и size от 1 до 100 без переполнения номера страницы");
        }
        var result = users.findAll(PageRequest.of(page, size, Sort.by("login").and(Sort.by("id"))));
        return new AdminUserPageDto(result.map(AdminUserMapper::toDto).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AdminUserDto create(AdminUserCreateDto dto) {
        String login = normalized(dto.login(), 100, "Логин");
        String name = normalized(dto.name(), 255, "Имя");
        validateRoles(dto.roles());
        PasswordPolicy.validate(dto.temporaryPassword());
        if (users.existsByLogin(login)) {
            throw new BusinessException("Логин уже используется");
        }
        User user = new User();
        user.setLogin(login);
        user.setName(name);
        user.setRoles(Set.copyOf(dto.roles()));
        user.setPasswordHash(encoder.encode(dto.temporaryPassword()));
        user.setPasswordChangeRequired(true);
        users.saveAndFlush(user);
        audit.record(AuditEventType.USER_CREATED, "USER", user.getUuid());
        return AdminUserMapper.toDto(user);
    }

    @Transactional
    public AdminUserDto edit(UUID uuid, String login, String name) {
        User user = findUser(uuid);
        String normalizedLogin = normalized(login, 100, "Логин");
        String normalizedName = normalized(name, 255, "Имя");
        if (users.existsByLoginAndUuidNot(normalizedLogin, uuid)) {
            throw new BusinessException("Логин уже используется");
        }
        user.setLogin(normalizedLogin);
        user.setName(normalizedName);
        return save(user);
    }

    @Transactional
    public AdminUserDto changeRoles(UUID uuid, Set<Role> roles) {
        validateRoles(roles);
        lockUserAdministration();
        User user = findUser(uuid);
        protectLastAdmin(user, user.isEnabled(), roles);
        user.setRoles(Set.copyOf(roles));
        return save(user);
    }

    @Transactional
    public AdminUserDto setEnabled(UUID uuid, boolean enabled) {
        lockUserAdministration();
        User user = findUser(uuid);
        protectLastAdmin(user, enabled, user.getRoles());
        user.setEnabled(enabled);
        if (!enabled) {
            sessions.revokeAllForUser(user.getId());
        }
        return save(user);
    }

    @Transactional
    public AdminUserDto resetPassword(UUID uuid, String temporaryPassword) {
        PasswordPolicy.validate(temporaryPassword);
        User user = findUser(uuid);
        user.setPasswordHash(encoder.encode(temporaryPassword));
        user.setPasswordChangeRequired(true);
        user.setFailedLoginAttempts(0);
        sessions.revokeAllForUser(user.getId());
        AdminUserDto result = save(user);
        audit.record(AuditEventType.PASSWORD_RESET, "USER", uuid);
        return result;
    }

    @Transactional
    public AdminUserDto unlock(UUID uuid) {
        User user = findUser(uuid);
        user.setFailedLoginAttempts(0);
        return save(user);
    }

    private void lockUserAdministration() {
        // Первый user не удаляется: одна стабильная строка сериализует смену ролей и enabled.
        users.findFirstByOrderByIdAsc().orElseThrow(() -> new EntityNotFoundException("Пользователи не найдены"));
    }

    private void protectLastAdmin(User user, boolean enabled, Set<Role> roles) {
        if (user.isEnabled() && user.getRoles().contains(Role.ADMIN) && (!enabled || !roles.contains(Role.ADMIN))
                && users.countByEnabledTrueAndRolesContaining(Role.ADMIN) <= 1) {
            throw new BusinessException("Нельзя отключить или лишить роли последнего включённого ADMIN");
        }
    }

    private User findUser(UUID uuid) {
        if (uuid == null) {
            throw new ValidationException("UUID пользователя обязателен");
        }
        return users.findByUuid(uuid).orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
    }

    private AdminUserDto save(User user) {
        user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        return AdminUserMapper.toDto(users.saveAndFlush(user));
    }

    private String normalized(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new ValidationException(field + " обязателен, максимальная длина: " + max);
        }
        return value.trim();
    }

    private void validateRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new ValidationException("Укажите хотя бы одну роль");
        }
        for (Role role : roles) {
            if (role == null) {
                throw new ValidationException("Роль не может быть null");
            }
        }
    }
}
