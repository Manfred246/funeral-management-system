package ru.funeralagency.service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import ru.funeralagency.dto.CsvImportMode;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.model.Tag;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FuneralRequestCsvServiceTest {
    private static final String HEADER = "requestCode,deceasedFullName,client,ceremonyDate,ceremonyType,status,price,urgent\n";
    @Mock private FuneralRequestRepository repository;
    @Mock private ClientRepository clients;
    @Mock private CeremonyTypeRepository types;
    @Mock private TagService tags;
    private FuneralRequestCsvService csv;
    private FuneralRequest existing;
    private final CeremonyType type = new CeremonyType(1L, "BURIAL", "Захоронение", true);
    private final Client client = new Client(1L, "Клиент", "+79991234567", null);

    @BeforeEach
    void setUp() {
        FuneralRequestService requests = new FuneralRequestService(repository, clients, new FuneralRequestMapper(), types, tags);
        csv = new FuneralRequestCsvService(new FuneralRequestCsv(), new FuneralRequestCsvRowService(requests, repository), requests);
        existing = new FuneralRequest(7L, client, "Старое имя", LocalDate.now().plusDays(5), type,
                RequestStatus.NEW, new BigDecimal("10.00"), LocalDateTime.of(2026, 1, 1, 12, 0), "Сохранить комментарий");
        existing.setUuid(UUID.randomUUID());
        existing.setRequestCode("FR-2026-000007");
        existing.setVersion(4L);
        existing.setPriority(2);
        existing.setUpdatedAt(existing.getCreatedAt());
        existing.getTags().add(new Tag(3L, "VIP"));
    }

    private void references() {
        lenient().when(clients.findById(1L)).thenReturn(Optional.of(client));
        lenient().when(types.findById(1L)).thenReturn(Optional.of(type));
    }

    private String row(String code, String status) {
        return code + ",Новое имя,1," + existing.getCeremonyDate() + ",1," + status + ",123.45,true\n";
    }

    private byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private void existingCode() {
        when(repository.findByRequestCode(existing.getRequestCode())).thenReturn(Optional.of(existing));
        lenient().when(repository.findByUuid(existing.getUuid())).thenReturn(Optional.of(existing));
    }

    @Test
    void validCreatePreviewNeverWrites() {
        references();
        var result = csv.preview(bytes(HEADER + row("", "NEW")), CsvImportMode.CREATE_ONLY);
        assertEquals(1, result.validCount());
        assertEquals(0, result.errorCount());
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).save(any());
        verify(repository, never()).flush();
        verifyNoInteractions(repository, tags);
    }

    @Test
    void upsertPreviewDoesNotChangeManagedStateTimesVersionsOrAssociations() {
        references();
        existingCode();
        var result = csv.preview(bytes(HEADER + row(existing.getRequestCode(), "CONFIRMED")), CsvImportMode.UPSERT_BY_REQUEST_CODE);
        assertEquals(1, result.validCount());
        assertEquals("Старое имя", existing.getDeceasedFullName());
        assertEquals(RequestStatus.NEW, existing.getStatus());
        assertEquals(new BigDecimal("10.00"), existing.getPrice());
        assertEquals(existing.getCreatedAt(), existing.getUpdatedAt());
        assertEquals(4L, existing.getVersion());
        assertSame(client, existing.getClient());
        assertSame(type, existing.getCeremonyType());
        assertEquals(1, existing.getTags().size());
        assertFalse(existing.isUrgent());
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).flush();
    }

    @Test
    void createOnlyConflictingCodeIsAnErrorInPreviewAndApply() {
        existingCode();
        byte[] file = bytes(HEADER + row(existing.getRequestCode(), "NEW"));
        assertEquals(1, csv.preview(file, CsvImportMode.CREATE_ONLY).errorCount());
        var applied = csv.apply(file, CsvImportMode.CREATE_ONLY);
        assertTrue(applied.successful().isEmpty());
        assertTrue(applied.errors().get(0).message().contains("уже существует"));
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CREATE_ONLY", "UPSERT_BY_REQUEST_CODE"})
    void createsMissingRequestWithCodeOnlyForUpsert(String mode) {
        references();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = csv.apply(bytes(HEADER + row("SOURCE-CODE", "NEW")), CsvImportMode.valueOf(mode));
        assertEquals(1, result.successful().size());
        assertTrue(result.successful().get(0).created());
        assertNotNull(result.successful().get(0).uuid());
        ArgumentCaptor<FuneralRequest> saved = ArgumentCaptor.forClass(FuneralRequest.class);
        verify(repository).saveAndFlush(saved.capture());
        if ("UPSERT_BY_REQUEST_CODE".equals(mode)) {
            assertEquals("SOURCE-CODE", saved.getValue().getRequestCode());
        } else {
            assertNull(saved.getValue().getRequestCode());
        }
        assertEquals(RequestStatus.NEW, saved.getValue().getStatus());
        assertTrue(saved.getValue().isUrgent());
        assertFalse(saved.getValue().isArchived());
        assertEquals(saved.getValue().getCreatedAt(), saved.getValue().getUpdatedAt());
    }

    @Test
    void repeatedUpsertWithUnknownCodeCreatesOnceThenUpdatesSameRequest() {
        references();
        java.util.Map<String, FuneralRequest> stored = new java.util.HashMap<>();
        when(repository.findByRequestCode("SOURCE-CODE")).thenAnswer(invocation -> Optional.ofNullable(stored.get("SOURCE-CODE")));
        when(repository.findByUuid(any())).thenAnswer(invocation -> stored.values().stream()
                .filter(request -> request.getUuid().equals(invocation.getArgument(0))).findFirst());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            FuneralRequest request = invocation.getArgument(0);
            stored.put(request.getRequestCode(), request);
            return request;
        });
        byte[] file = bytes(HEADER + row("SOURCE-CODE", "NEW"));
        var first = csv.apply(file, CsvImportMode.UPSERT_BY_REQUEST_CODE);
        assertEquals(1, first.successful().size());
        assertTrue(first.successful().get(0).created());
        assertEquals("SOURCE-CODE", first.successful().get(0).requestCode());
        var created = stored.get("SOURCE-CODE");
        var createdAt = created.getCreatedAt();
        // Unit mock не генерирует @Version; задаём состояние уже сохранённой строки для update validation.
        created.setVersion(0L);
        var second = csv.apply(file, CsvImportMode.UPSERT_BY_REQUEST_CODE);
        assertEquals(1, stored.size());
        assertEquals(1, second.successful().size());
        assertFalse(second.successful().get(0).created());
        assertEquals(first.successful().get(0).uuid(), second.successful().get(0).uuid());
        assertEquals(createdAt, created.getCreatedAt());
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void upsertRequiresCodeButCreateOnlyStillAllowsBlankCode() {
        var result = csv.preview(bytes(HEADER + row("", "NEW")), CsvImportMode.UPSERT_BY_REQUEST_CODE);
        assertEquals(1, result.errorCount());
        assertTrue(result.errors().get(0).message().contains("обязателен requestCode"));
        verifyNoInteractions(repository);
    }

    @Test
    void upsertUpdatesBusinessFieldsAndPreservesIdentityAndTags() {
        references();
        existingCode();
        when(repository.saveAndFlush(existing)).thenReturn(existing);
        UUID uuid = existing.getUuid();
        var createdAt = existing.getCreatedAt();
        var result = csv.apply(bytes(HEADER + row(existing.getRequestCode(), "CONFIRMED")), CsvImportMode.UPSERT_BY_REQUEST_CODE);
        assertFalse(result.successful().get(0).created());
        assertEquals(uuid, result.successful().get(0).uuid());
        assertEquals("FR-2026-000007", existing.getRequestCode());
        assertEquals(createdAt, existing.getCreatedAt());
        assertEquals("Новое имя", existing.getDeceasedFullName());
        assertEquals(RequestStatus.CONFIRMED, existing.getStatus());
        assertEquals("Сохранить комментарий", existing.getComment());
        assertTrue(existing.isUrgent());
        assertEquals(1, existing.getTags().size());
        assertEquals(4L, existing.getVersion());
    }

    @Test
    void duplicateCodesRejectEveryOccurrenceAndOtherRowsStillApply() {
        references();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        byte[] file = bytes(HEADER + row("DUP", "NEW") + row("DUP", "NEW") + row("", "NEW"));
        var preview = csv.preview(file, CsvImportMode.CREATE_ONLY);
        assertEquals(1, preview.validCount());
        assertEquals(2, preview.errorCount());
        var result = csv.apply(file, CsvImportMode.CREATE_ONLY);
        assertEquals(1, result.successful().size());
        assertEquals(4, result.successful().get(0).line());
        assertEquals(2, result.errors().size());
        verify(repository, times(1)).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {",Имя,1,2099-01-01,1,NEW,-1,true", ",Имя,1,2099-01-01,1,NEW,1.001,true",
            ",Имя,999,2099-01-01,1,NEW,10,true", ",Имя,1,2099-01-01,999,NEW,10,true",
            ",Имя,1,2000-01-01,1,NEW,10,true", ",Имя,1,2099-02-30,1,NEW,10,true",
            ",Имя,1,2099-01-01,1,BAD,10,true", ",Имя,1,2099-01-01,1,CONFIRMED,10,true",
            ",,1,2099-01-01,1,NEW,10,true", ",Имя,abc,2099-01-01,1,NEW,10,true",
            ",Имя,1,2099-01-01,1,NEW,10,yes"})
    void invalidPreviewDoesNotWriteAndReportsPhysicalLine(String row) {
        references();
        var result = csv.preview(bytes(HEADER + row + "\n"), CsvImportMode.CREATE_ONLY);
        assertEquals(0, result.validCount());
        assertEquals(1, result.errorCount());
        assertEquals(2, result.errors().get(0).line());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void disabledTypeAndInvalidStatusTransitionUseExistingRules() {
        references();
        type.setEnabled(false);
        assertEquals(1, csv.preview(bytes(HEADER + row("", "NEW")), CsvImportMode.CREATE_ONLY).errorCount());
        type.setEnabled(true);
        existingCode();
        assertEquals(1, csv.preview(bytes(HEADER + row(existing.getRequestCode(), "COMPLETED")), CsvImportMode.UPSERT_BY_REQUEST_CODE).errorCount());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void mixedValidInvalidApplyReportsEachLineAndContinues() {
        references();
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = csv.apply(bytes(HEADER + row("", "NEW") + row("", "NEW").replace("123.45", "-1")
                + row("", "NEW")), CsvImportMode.CREATE_ONLY);
        assertEquals(2, result.successful().size());
        assertEquals(2, result.successful().get(0).line());
        assertEquals(4, result.successful().get(1).line());
        assertEquals(1, result.errors().size());
        assertEquals(3, result.errors().get(0).line());
        verify(repository, times(2)).saveAndFlush(any());
    }

    @Test
    void malformedTailStopsEntireApplyBeforeAnyDatabaseAccess() {
        var result = csv.apply(bytes(HEADER + row("", "NEW") + "\"broken"), CsvImportMode.CREATE_ONLY);
        assertTrue(result.successful().isEmpty());
        assertEquals(1, result.errors().size());
        verifyNoInteractions(repository, clients, types);
    }

    @Test
    void exportUsesDatabaseSpecificationAndSameSort() {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setTag(3L);
        filter.setStatus(RequestStatus.NEW);
        filter.setArchived(true);
        when(repository.findAll(any(Specification.class), any(Sort.class))).thenReturn(List.of(existing));
        byte[] exported = csv.export(filter, "price,asc");
        assertTrue(new String(exported, StandardCharsets.UTF_8).contains(existing.getRequestCode()));
        ArgumentCaptor<Specification<FuneralRequest>> spec = ArgumentCaptor.forClass(Specification.class);
        verify(repository).findAll(spec.capture(), eq(Sort.by("price").and(Sort.by("id"))));
        Root<FuneralRequest> root = mock(Root.class, RETURNS_DEEP_STUBS);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        spec.getValue().toPredicate(root, query, cb);
        verify(cb).equal(root.join("tags").get("id"), 3L);
        verify(cb).equal(root.get("status"), RequestStatus.NEW);
        verify(cb).equal(root.get("archived"), true);
        verify(query).distinct(true);
    }
}
