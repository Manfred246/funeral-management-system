package ru.funeralagency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.Tag;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.TagRepository;
import ru.funeralagency.service.ExcelExporter;
import ru.funeralagency.service.FuneralRequestService;
import ru.funeralagency.service.TagService;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class RequestTagControllerTest {
    @Mock private FuneralRequestRepository requests;
    @Mock private ClientRepository clients;
    @Mock private CeremonyTypeRepository types;
    @Mock private TagRepository tags;
    @Mock private ExcelExporter exporter;
    private MockMvc mvc;
    private FuneralRequest stored;
    private final UUID uuid = UUID.fromString("8d44b5ce-45e2-487d-81d8-b714004db0d7");
    private final LocalDateTime originalTime = LocalDateTime.of(2026, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        var mapper = new FuneralRequestMapper();
        var service = new FuneralRequestService(requests, clients, mapper, types, new TagService(tags));
        mvc = MockMvcBuilders.standaloneSetup(new FuneralRequestController(service, mapper, exporter))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        stored = new FuneralRequest();
        stored.setUuid(uuid);
        stored.setRequestCode("FR-2026-000001");
        stored.setVersion(5L);
        stored.setCreatedAt(originalTime);
        stored.setUpdatedAt(originalTime);
    }

    private void existingRequestAndTag() {
        when(requests.findByUuid(uuid)).thenReturn(Optional.of(stored));
        // Разные Java-объекты с одним ID не должны создать вторую связь.
        when(tags.findById(3L)).thenAnswer(invocation -> Optional.of(new Tag(3L, "VIP")));
    }

    @Test
    void addAndRepeatedAddAreIdempotentAndPreserveIdentifiers() throws Exception {
        existingRequestAndTag();
        mvc.perform(post("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNoContent());
        assertEquals(1, stored.getTags().size());
        assertTrue(stored.getUpdatedAt().isAfter(originalTime));
        var changedAt = stored.getUpdatedAt();
        mvc.perform(post("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNoContent());
        assertEquals(1, stored.getTags().size());
        assertEquals(changedAt, stored.getUpdatedAt());
        assertEquals(uuid, stored.getUuid());
        assertEquals("FR-2026-000001", stored.getRequestCode());
        assertEquals(originalTime, stored.getCreatedAt());
        // Mock repository не имитирует Hibernate increment; сервис version не назначает.
        assertEquals(5L, stored.getVersion());
        verify(requests, times(1)).saveAndFlush(stored);
    }

    @Test
    void removeOnlyLinkAndRepeatedRemoveAreIdempotent() throws Exception {
        existingRequestAndTag();
        stored.getTags().add(new Tag(3L, "VIP"));
        mvc.perform(delete("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNoContent());
        var changedAt = stored.getUpdatedAt();
        assertTrue(stored.getTags().isEmpty());
        assertTrue(changedAt.isAfter(originalTime));
        mvc.perform(delete("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNoContent());
        assertEquals(changedAt, stored.getUpdatedAt());
        verify(requests, times(1)).saveAndFlush(stored);
        verify(tags, never()).delete(any());
    }

    @Test
    void removeAbsentRelationDoesNotSaveOrChangeTime() throws Exception {
        existingRequestAndTag();
        mvc.perform(delete("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNoContent());
        assertEquals(originalTime, stored.getUpdatedAt());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void archivedTagsReadableButMutationsAre409() throws Exception {
        stored.setArchived(true);
        stored.getTags().add(new Tag(3L, "VIP"));
        when(requests.findByUuid(uuid)).thenReturn(Optional.of(stored));
        mvc.perform(get("/api/requests/{uuid}/tags", uuid)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("VIP"));
        mvc.perform(post("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isConflict());
        mvc.perform(delete("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isConflict());
        verifyNoInteractions(tags);
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void unknownRequestAndTagAre404() throws Exception {
        mvc.perform(get("/api/requests/{uuid}/tags", uuid)).andExpect(status().isNotFound());
        mvc.perform(post("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNotFound());
        when(requests.findByUuid(uuid)).thenReturn(Optional.of(stored));
        mvc.perform(post("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "bad"})
    void invalidTagIdIs400(String id) throws Exception {
        if (!"bad".equals(id)) {
            when(requests.findByUuid(uuid)).thenReturn(Optional.of(stored));
        }
        mvc.perform(post("/api/requests/{uuid}/tags/{id}", uuid, id)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/requests/{uuid}/tags/{id}", uuid, id)).andExpect(status().isBadRequest());
        verifyNoInteractions(tags);
    }

    @Test
    void malformedUuidAndFilterIdAre400() throws Exception {
        mvc.perform(get("/api/requests/bad/tags")).andExpect(status().isBadRequest());
        for (String value : new String[]{"0", "-1", "bad"}) {
            mvc.perform(get("/api/requests").param("tag", value)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(requests, tags);
    }

    @Test
    void optimisticConflictUsesExisting409() throws Exception {
        existingRequestAndTag();
        when(requests.saveAndFlush(stored)).thenThrow(new OptimisticLockingFailureException("conflict"));
        mvc.perform(post("/api/requests/{uuid}/tags/3", uuid)).andExpect(status().isConflict());
    }
}
