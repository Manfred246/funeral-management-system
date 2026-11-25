package ru.funeralagency.controller;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.FuneralRequestRepository;
import ru.funeralagency.service.ExcelExporter;
import ru.funeralagency.service.FuneralRequestService;
import ru.funeralagency.service.TagService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FuneralRequestControllerTest {
    private static final UUID UUID_VALUE = UUID.fromString("8d44b5ce-45e2-487d-81d8-b714004db0d7");

    @Mock
    private FuneralRequestRepository requests;
    @Mock
    private ClientRepository clients;
    @Mock
    private ExcelExporter exporter;

    @Mock
    private CeremonyTypeRepository ceremonyTypes;
    @Mock
    private TagService tagService;

    private MockMvc mvc;
    private FuneralRequest stored;

    @BeforeEach
    void setUp() {
        FuneralRequestService service = new FuneralRequestService(
                requests, clients, new FuneralRequestMapper(), ceremonyTypes, tagService, org.mockito.Mockito.mock(ru.funeralagency.service.AuditService.class));
        mvc = MockMvcBuilders.standaloneSetup(
                        new FuneralRequestController(service, new FuneralRequestMapper(), exporter))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        stored = new FuneralRequest(5L, new Client(1L, "Клиент", "+79991234567", null),
                "Иван Иванов", LocalDate.now().plusDays(4), new CeremonyType(1L, "BURIAL", "Захоронение", true),
                RequestStatus.NEW, new BigDecimal("100.00"), LocalDateTime.of(2026, 1, 1, 12, 0), null);
        stored.setUuid(UUID_VALUE);
        stored.setRequestCode("FR-2026-000005");
        stored.setVersion(3L);
        stored.setUpdatedAt(stored.getCreatedAt());
        stored.setPriority(0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void serializesPageAndAcceptsCombinedQueryParameters() throws Exception {
        when(requests.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(stored), invocation.getArgument(1), 3));
        mvc.perform(get("/api/requests")
                        .param("q", "FR-2026").param("client", "1")
                        .param("ceremonyTypeId", "1").param("status", "NEW")
                        .param("urgent", "false").param("priority", "0").param("archived", "false")
                        .param("minPrice", "100").param("maxPrice", "200").param("tag", "3")
                        .param("page", "1").param("size", "2").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].uuid").value(UUID_VALUE.toString()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultPageStartsAtZeroWithSize20() throws Exception {
        when(requests.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        mvc.perform(get("/api/requests"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-1", "size=101", "page=abc", "size=abc",
            "minPrice=-1", "maxPrice=-1", "minPrice=20&maxPrice=10", "minPrice=abc",
            "sort=id,asc", "sort=price,up", "sort=price", "status=BAD", "ceremonyTypeId=BAD", "ceremonyTypeId=0", "ceremonyTypeId=4294967297",
            "urgent=BAD", "archived=BAD", "priority=-1", "priority=abc", "client=0"})
    void invalidQueryParametersReturn400WithoutQueryingDatabase(String query) throws Exception {
        mvc.perform(get("/api/requests?" + query))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(requests, clients);
    }

    @Test
    void getsRequestByUuid() throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        mvc.perform(get("/api/requests/{uuid}", UUID_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(UUID_VALUE.toString()))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.requestCode").value("FR-2026-000005"))
                .andExpect(jsonPath("$.version").value(3));
        verify(requests, never()).findById(any());
    }

    @Test
    void existingRequestWithDisabledTypeStillReturnsNestedTypeDto() throws Exception {
        stored.getCeremonyType().setEnabled(false);
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        mvc.perform(get("/api/requests/{uuid}", UUID_VALUE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ceremonyType.id").value(1))
                .andExpect(jsonPath("$.ceremonyType.code").value("BURIAL"))
                .andExpect(jsonPath("$.ceremonyType.enabled").value(false));
    }

    @Test
    void normalRestPostCannotAssignImportedRequestCode() throws Exception {
        when(clients.findById(1L)).thenReturn(Optional.of(stored.getClient()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(stored.getCeremonyType()));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> {
            FuneralRequest request = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertNull(request.getRequestCode());
            return request;
        });
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("3").replace("{", "{\"requestCode\":\"IMPORTED-CODE\",")))
                .andExpect(status().isCreated());
        verify(requests).saveAndFlush(any());
    }

    @Test
    void createLoadsCustomTypeByIdRatherThanAcceptingAnEnum() throws Exception {
        CeremonyType custom = new CeremonyType(3L, "MEMORIAL", "Прощание", true);
        when(clients.findById(1L)).thenReturn(Optional.of(stored.getClient()));
        when(ceremonyTypes.findById(3L)).thenReturn(Optional.of(custom));
        when(requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("3").replace("\"ceremonyTypeId\":1", "\"ceremonyTypeId\":3")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(1))
                .andExpect(jsonPath("$.ceremonyType.id").value(3))
                .andExpect(jsonPath("$.ceremonyType.code").value("MEMORIAL"))
                .andExpect(jsonPath("$.ceremonyType.name").value("Прощание"));
        verify(ceremonyTypes).findById(3L);
    }

    @Test
    void disabledTypeReturns409AndMissingTypeIdReturns400OnCreate() throws Exception {
        stored.getCeremonyType().setEnabled(false);
        when(clients.findById(1L)).thenReturn(Optional.of(stored.getClient()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(stored.getCeremonyType()));
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(updateBody("3")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("3").replace("\"ceremonyTypeId\":1,", "")))
                .andExpect(status().isBadRequest());
        verify(requests, never()).saveAndFlush(any());
    }

    @Test
    void unknownUuidReturns404() throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.empty());
        mvc.perform(get("/api/requests/{uuid}", UUID_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Заявка с UUID " + UUID_VALUE + " не найдена"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-uuid", "15"})
    void invalidUuidAndOldLongPathReturn400(String value) throws Exception {
        mvc.perform(get("/api/requests/{uuid}", value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Некорректное значение параметра: uuid"));
        verifyNoInteractions(requests);
    }

    @Test
    void createLocationUsesPublicUuid() throws Exception {
        when(clients.findById(1L)).thenReturn(Optional.of(stored.getClient()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(stored.getCeremonyType()));
        when(requests.saveAndFlush(any())).thenReturn(stored);
        mvc.perform(post("/api/requests").contentType(MediaType.APPLICATION_JSON).content(updateBody("3")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/requests/" + UUID_VALUE));
    }

    @Test
    void updatesByUuidAndReturnsVersionFromSavedEntity() throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        when(clients.findById(1L)).thenReturn(Optional.of(stored.getClient()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(stored.getCeremonyType()));
        when(requests.saveAndFlush(stored)).thenReturn(stored);
        mvc.perform(put("/api/requests/{uuid}", UUID_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("3")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(200))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.uuid").value(UUID_VALUE.toString()))
                .andExpect(jsonPath("$.requestCode").value("FR-2026-000005"))
                // У mock Repository нет Hibernate; реальное увеличение версии проверяется в IT.
                .andExpect(jsonPath("$.version").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1"})
    void invalidVersionReturns400BeforeCallingService(String version) throws Exception {
        mvc.perform(put("/api/requests/{uuid}", UUID_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(version)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(requests, clients);
    }

    @Test
    void missingVersionReturns400() throws Exception {
        mvc.perform(put("/api/requests/{uuid}", UUID_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("3").replace(",\"version\":3", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Версия заявки обязательна"));
        verifyNoInteractions(requests, clients);
    }

    @Test
    void staleVersionReturns409WithoutSaving() throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        mvc.perform(put("/api/requests/{uuid}", UUID_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("2")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Запись была изменена другим пользователем. Обновите данные и повторите попытку."))
                .andExpect(jsonPath("$.trace").doesNotExist());
        verify(requests, never()).saveAndFlush(any());
        verifyNoInteractions(clients);
    }

    @ParameterizedTest
    @MethodSource("optimisticFailures")
    void persistenceOptimisticConflictReturnsSafe409(RuntimeException failure) throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        when(clients.findById(1L)).thenReturn(Optional.of(stored.getClient()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(stored.getCeremonyType()));
        when(requests.saveAndFlush(stored)).thenThrow(failure);
        mvc.perform(put("/api/requests/{uuid}", UUID_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("3")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value(
                        "Запись была изменена другим пользователем. Обновите данные и повторите попытку."))
                .andExpect(jsonPath("$.path").value("/api/requests/" + UUID_VALUE))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    static Stream<RuntimeException> optimisticFailures() {
        return Stream.of(new OptimisticLockingFailureException("internal"),
                new ObjectOptimisticLockingFailureException(FuneralRequest.class, 5L),
                new OptimisticLockException("internal"));
    }

    @Test
    void deleteArchivesAndGetStillReturnsRecord() throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        mvc.perform(delete("/api/requests/{uuid}", UUID_VALUE)).andExpect(status().isNoContent());
        mvc.perform(get("/api/requests/{uuid}", UUID_VALUE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
        verify(requests).saveAndFlush(stored);
        verify(requests, never()).delete(any(FuneralRequest.class));
    }

    @Test
    void restoreReturnsActiveRequest() throws Exception {
        stored.setArchived(true);
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        when(requests.saveAndFlush(stored)).thenReturn(stored);
        mvc.perform(post("/api/requests/{uuid}/restore", UUID_VALUE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void permanentDeleteCallsPhysicalDeletion() throws Exception {
        when(requests.findByUuid(UUID_VALUE)).thenReturn(Optional.of(stored));
        mvc.perform(delete("/api/requests/{uuid}/permanent", UUID_VALUE))
                .andExpect(status().isNoContent());
        verify(requests).delete(stored);
        verify(requests).flush();
    }

    private String updateBody(String version) {
        return """
                {"clientId":1,"deceasedFullName":"Иван Иванов","ceremonyDate":"%s",
                 "ceremonyTypeId":1,"status":"CONFIRMED","price":200,"version":%s}
                """.formatted(LocalDate.now().plusDays(4), version);
    }
}
