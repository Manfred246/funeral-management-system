package ru.funeralagency.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.AuditEvent;
import ru.funeralagency.model.AuditEventType;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.AuditEventRepository;
import ru.funeralagency.repository.AuditEventSpecifications;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.AuthenticatedUser;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AuditServiceTest {
    @Mock private AuditEventRepository events;
    @Mock private UserRepository users;

    @AfterEach
    void clean() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void allEventsUseFixedDescriptionsWithActorAndCorrelationWithoutEntityContent() {
        User actor = new User();
        actor.setId(7L);
        actor.setPasswordHash("secret-hash");
        when(users.findByUuid(actor.getUuid())).thenReturn(Optional.of(actor));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(actor.getUuid(), "login", false), null, List.of()));
        MDC.put("correlationId", "audit-test");
        AuditService service = new AuditService(events, users);
        UUID entity = UUID.randomUUID();
        for (AuditEventType type : AuditEventType.values()) {
            service.record(type, "FUNERAL_REQUEST", entity);
        }
        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events, times(8)).save(saved.capture());
        for (int index = 0; index < saved.getAllValues().size(); index++) {
            AuditEvent event = saved.getAllValues().get(index);
            assertEquals(AuditEventType.values()[index].name(), event.getEventType());
            assertEquals(AuditEventType.values()[index].getDescription(), event.getDescription());
            assertEquals(entity, event.getEntityUuid());
            assertEquals(7L, event.getUserId());
            assertEquals("audit-test", event.getCorrelationId());
            assertFalse(event.getDescription().contains("secret-hash"));
        }
    }

    @Test
    void nonHttpActionCanHaveNoActorButGetsCorrelationUuid() {
        new AuditService(events, users).record(AuditEventType.USER_CREATED, "USER", UUID.randomUUID());
        ArgumentCaptor<AuditEvent> saved = ArgumentCaptor.forClass(AuditEvent.class);
        verify(events).save(saved.capture());
        assertNull(saved.getValue().getUserId());
        assertNotNull(UUID.fromString(saved.getValue().getCorrelationId()));
    }

    @Test
    void auditSearchEscapesWildcardsAndUsesParameterizedPredicates() {
        Root<AuditEvent> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        when(cb.lower(any())).thenAnswer(invocation -> invocation.getArgument(0));
        AuditEventSpecifications.search("LOGIN%_!").toPredicate(root, query, cb);
        for (String field : List.of("eventType", "entityType", "description", "correlationId")) {
            verify(cb).like(root.get(field), "%login!%!_!!%", '!');
        }
        verify(cb).or(any(), any(), any(), any());
    }

    @Test
    void auditPageSortAndDtoAreReturnedWithoutEntityLeak() {
        AuditEvent event = new AuditEvent("LOGIN", "USER", UUID.randomUUID(), 1L,
                LocalDateTime.now(), "Успешный вход", "search-test");
        when(events.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(event), invocation.getArgument(1), 9));
        var result = new AuditService(events, users).findPage("LOGIN", 1, 2, "timestamp,asc");
        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(9, result.totalElements());
        assertEquals(5, result.totalPages());
        assertEquals("LOGIN", result.content().get(0).eventType());
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(events).findAll(any(Specification.class), pageable.capture());
        assertEquals(Sort.by("timestamp").and(Sort.by(Sort.Direction.DESC, "id")), pageable.getValue().getSort());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id,asc", "timestamp", "timestamp,bad", "description,asc", "timestamp;DROP,desc"})
    void sortIsAllowListed(String sort) {
        assertThrows(ValidationException.class, () -> new AuditService(events, users).findPage(null, 0, 20, sort));
        verifyNoInteractions(events);
    }

    @Test
    void pageBoundsAreValidated() {
        AuditService service = new AuditService(events, users);
        assertThrows(ValidationException.class, () -> service.findPage(null, -1, 20, "timestamp,desc"));
        assertThrows(ValidationException.class, () -> service.findPage(null, 0, 101, "timestamp,desc"));
        assertThrows(ValidationException.class, () -> service.findPage(null, Integer.MAX_VALUE, 100, "timestamp,desc"));
        verifyNoInteractions(events);
    }
}
