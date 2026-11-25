package ru.funeralagency.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import ru.funeralagency.mapper.AuditMapper;
import ru.funeralagency.model.AuditEvent;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.AuditEventRepository;
import ru.funeralagency.repository.RefreshSessionRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthAuditTest {
    @Test
    void onlySuccessfulLoginIsAuditedWithoutPasswordHashOrTokens() throws Exception {
        UserRepository users = mock(UserRepository.class);
        AuditEventRepository events = mock(AuditEventRepository.class);
        var encoder = new BCryptPasswordEncoder(4);
        User user = new User();
        user.setId(1L);
        user.setLogin("manager");
        user.setName("Менеджер");
        user.setRoles(Set.of(Role.MANAGER));
        user.setPasswordHash(encoder.encode("private-password"));
        when(users.findByLogin("manager")).thenReturn(Optional.of(user));
        var service = new AuthService(users, mock(RefreshSessionRepository.class), encoder,
                new JwtService("unit-test-key-only-not-for-production-1234567890"), new AuditService(events, users));
        var tokens = service.login("manager", "private-password");
        assertThrows(BadCredentialsException.class, () -> service.login("manager", "wrong-password"));
        var saved = org.mockito.ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(saved.capture());
        assertEquals("LOGIN", saved.getValue().getEventType());
        assertEquals(1L, saved.getValue().getUserId());
        String json = Jackson2ObjectMapperBuilder.json().build().writeValueAsString(AuditMapper.toDto(saved.getValue()));
        for (String secret : new String[]{"private-password", user.getPasswordHash(), tokens.accessToken(), tokens.refreshToken()}) {
            assertFalse(json.contains(secret));
        }
    }
}
