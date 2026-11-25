package ru.funeralagency.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuneralRequestServiceTest {

    private static final UUID REQUEST_UUID = UUID.fromString("8d44b5ce-45e2-487d-81d8-b714004db0d7");

    @Mock
    private FuneralRequestRepository requestRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private CeremonyTypeRepository ceremonyTypes;
    @Mock
    private TagService tags;

    private FuneralRequestService service;

    @BeforeEach
    void setUp() {
        service = new FuneralRequestService(requestRepository, clientRepository,
                new ru.funeralagency.mapper.FuneralRequestMapper(), ceremonyTypes, tags);
    }

    @Test
    void createsRequestWithServerGeneratedFieldsAndDefaults() {
        FuneralRequest request = validRequest(999L, RequestStatus.CONFIRMED);
        request.setClient(null);
        UUID suppliedUuid = UUID.randomUUID();
        request.setUuid(suppliedUuid);
        request.setRequestCode("supplied");
        request.setPriority(999);
        request.setArchived(true);
        request.setUpdatedAt(LocalDateTime.of(2000, 1, 1, 0, 0));
        request.setVersion(999L);
        Client existingClient = client();
        when(clientRepository.findById(1L)).thenReturn(Optional.of(existingClient));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(new CeremonyType(1L, "BURIAL", "Захоронение", true)));
        when(requestRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            FuneralRequest saved = invocation.getArgument(0);
            assertNull(saved.getId());
            assertNotNull(saved.getCreatedAt());
            assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());
            assertNull(saved.getRequestCode());
            assertNull(saved.getVersion());
            // Mock не выполняет INSERT: генерацию БД проверяют PostgreSQL IT, настройки — mapping test.
            return saved;
        });

        FuneralRequest created = service.create(request, 1L, 1L);

        assertNull(created.getId());
        assertEquals(RequestStatus.NEW, created.getStatus());
        assertNotNull(created.getUuid());
        assertNotEquals(suppliedUuid, created.getUuid());
        assertNull(created.getRequestCode());
        assertEquals(created.getCreatedAt(), created.getUpdatedAt());
        assertFalse(created.isUrgent());
        assertFalse(created.isArchived());
        assertEquals(2, created.getPriority());
        assertTrue(created.getUpdatedAt().isAfter(LocalDateTime.of(2000, 1, 1, 0, 0)));
        assertSame(existingClient, created.getClient());
        verify(requestRepository).saveAndFlush(request);
    }

    @ParameterizedTest
    @CsvSource({
            "false, -1, 0", "true, -1, 2",
            "false, 0, 2", "true, 0, 4",
            "false, 3, 2", "true, 3, 4",
            "false, 4, 1", "false, 7, 1", "true, 7, 3",
            "false, 8, 0", "false, 30, 0", "true, 30, 2"
    })
    void calculatesPriorityFromFixedCreationDateWithoutChangingSystemClock(boolean urgent, int days, int expected) {
        LocalDateTime createdAt = LocalDateTime.of(2000, 1, 10, 12, 0);
        assertEquals(expected, FuneralRequestService.calculatePriority(
                urgent, createdAt, createdAt.toLocalDate().plusDays(days)));
    }

    @Test
    void rejectsRequestForMissingClient() {
        FuneralRequest request = validRequest(null, RequestStatus.NEW);
        when(clientRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ValidationException.class, () -> service.create(request, 1L, 1L));

        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNewToCompletedTransition() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        FuneralRequest update = validRequest(null, RequestStatus.COMPLETED);
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.updateByUuid(REQUEST_UUID, update, 1L, 1L, null, 0L));

        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void legacyQueriesDelegateToSpecificationsAndPreserveListResponses() {
        FuneralRequest request = validRequest(1L, RequestStatus.NEW);
        when(requestRepository.findAll(any(Specification.class), eq(Sort.by("id"))))
                .thenReturn(List.of(request));
        Sort priceSort = Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id"));
        when(requestRepository.findAll(any(Specification.class), eq(priceSort)))
                .thenReturn(List.of(request));

        assertEquals(List.of(request), service.search("иванов", null));
        assertEquals(List.of(request), service.search(null, 1L));
        when(ceremonyTypes.findByCode("BURIAL")).thenReturn(Optional.of(new CeremonyType(1L, "BURIAL", "Захоронение", true)));
        assertEquals(List.of(request), service.filter(RequestStatus.NEW, "BURIAL", null, null));
        assertEquals(List.of(request), service.sort("price", true));
        verify(requestRepository, never()).findAll();
    }

    private Client client() {
        return new Client(1L, "Заказчик Иван", "+7 999 123 45 67", null);
    }

    @Test
    void updatePreservesIdentifiersCreationTimeAndVersionButRecalculatesPriority() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        UUID uuid = REQUEST_UUID;
        existing.setUuid(uuid);
        existing.setRequestCode("FR-2026-000005");
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, 0);
        existing.setCreatedAt(createdAt);
        existing.setUpdatedAt(createdAt);
        existing.setVersion(4L);
        existing.setArchived(true);
        existing.setPriority(0);
        FuneralRequest input = validRequest(99L, RequestStatus.CONFIRMED);
        input.setUuid(UUID.randomUUID());
        input.setRequestCode("changed");
        input.setArchived(false);
        input.setVersion(999L);
        input.setUrgent(true);
        existing.setCeremonyDate(createdAt.toLocalDate().plusDays(6));
        input.setCeremonyDate(existing.getCeremonyDate());
        input.setPriority(999);
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(new CeremonyType(1L, "BURIAL", "Захоронение", true)));
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);

        assertSame(existing, service.updateByUuid(REQUEST_UUID, input, 1L, 1L, true, 4L));

        assertEquals(5L, existing.getId());
        assertEquals(uuid, existing.getUuid());
        assertEquals("FR-2026-000005", existing.getRequestCode());
        assertEquals(createdAt, existing.getCreatedAt());
        assertTrue(existing.getUpdatedAt().isAfter(createdAt));
        assertTrue(existing.isUrgent());
        assertTrue(existing.isArchived());
        assertEquals(3, existing.getPriority());
        // Значение версии изменяет Hibernate, а не входной объект.
        assertEquals(4L, existing.getVersion());
        assertEquals(RequestStatus.CONFIRMED, existing.getStatus());
        assertEquals(input.getPrice(), existing.getPrice());
    }

    @ParameterizedTest
    @CsvSource({"null, true, 2", "false, false, 0"})
    void updateKeepsUrgentUnlessExplicitlyChanged(String urgentValue, boolean expectedUrgent, int expectedPriority) {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        existing.setUrgent(true);
        FuneralRequest input = validRequest(null, RequestStatus.CONFIRMED);
        input.setCeremonyDate(LocalDate.now().plusDays(30));
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(new CeremonyType(1L, "BURIAL", "Захоронение", true)));
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);
        Boolean urgent = "null".equals(urgentValue) ? null : Boolean.valueOf(urgentValue);

        FuneralRequest updated = service.updateByUuid(REQUEST_UUID, input, 1L, 1L, urgent, 0L);

        assertEquals(expectedUrgent, updated.isUrgent());
        assertEquals(expectedPriority, updated.getPriority());
    }

    @Test
    void missingRequestCannotBeUpdated() {
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.updateByUuid(REQUEST_UUID, validRequest(null, RequestStatus.CONFIRMED), 1L, 1L, null, 0L));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void staleVersionIsRejectedBeforeChangingEntityOrLookingUpClient() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        existing.setVersion(3L);
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));
        FuneralRequest input = validRequest(null, RequestStatus.CONFIRMED);
        input.setPrice(new BigDecimal("999.00"));

        assertThrows(OptimisticLockingFailureException.class,
                () -> service.updateByUuid(REQUEST_UUID, input, 1L, 1L, true, 2L));

        assertEquals(RequestStatus.NEW, existing.getStatus());
        assertEquals(new BigDecimal("10000.00"), existing.getPrice());
        assertEquals(3L, existing.getVersion());
        verify(requestRepository, never()).saveAndFlush(any());
        org.mockito.Mockito.verifyNoInteractions(clientRepository);
    }

    @Test
    void versionIsRequiredForServiceUpdate() {
        when(requestRepository.findByUuid(REQUEST_UUID))
                .thenReturn(Optional.of(validRequest(5L, RequestStatus.NEW)));
        FuneralRequest input = validRequest(null, RequestStatus.CONFIRMED);
        assertThrows(ValidationException.class,
                () -> service.updateByUuid(REQUEST_UUID, input, 1L, 1L, null, null));
        assertThrows(ValidationException.class,
                () -> service.updateByUuid(REQUEST_UUID, input, 1L, 1L, null, -1L));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void archivePreservesRecordIdentifiersAndIsIdempotent() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        LocalDateTime before = LocalDateTime.of(2000, 1, 1, 12, 0);
        existing.setUpdatedAt(before);
        LocalDateTime createdAt = existing.getCreatedAt();
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));

        service.archiveByUuid(REQUEST_UUID);
        assertTrue(existing.isArchived());
        assertTrue(existing.getUpdatedAt().isAfter(before));
        LocalDateTime archivedAt = existing.getUpdatedAt();
        assertSame(existing, service.getByUuid(REQUEST_UUID));
        service.archiveByUuid(REQUEST_UUID);

        assertEquals(archivedAt, existing.getUpdatedAt());
        assertEquals(REQUEST_UUID, existing.getUuid());
        assertEquals("FR-2026-000005", existing.getRequestCode());
        assertEquals(createdAt, existing.getCreatedAt());
        assertEquals(0L, existing.getVersion()); // Mock не исполняет Hibernate @Version.
        verify(requestRepository).saveAndFlush(existing);
        verify(requestRepository, never()).delete(any(FuneralRequest.class));
    }

    @Test
    void restoreChangesArchiveFlagAndTimestampOnlyAndIsIdempotent() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        existing.setArchived(true);
        LocalDateTime before = LocalDateTime.of(2000, 1, 1, 12, 0);
        existing.setUpdatedAt(before);
        LocalDateTime createdAt = existing.getCreatedAt();
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);

        assertSame(existing, service.restoreByUuid(REQUEST_UUID));
        assertFalse(existing.isArchived());
        assertTrue(existing.getUpdatedAt().isAfter(before));
        LocalDateTime restoredAt = existing.getUpdatedAt();
        assertSame(existing, service.restoreByUuid(REQUEST_UUID));

        assertEquals(restoredAt, existing.getUpdatedAt());
        assertEquals(REQUEST_UUID, existing.getUuid());
        assertEquals("FR-2026-000005", existing.getRequestCode());
        assertEquals(createdAt, existing.getCreatedAt());
        assertEquals(0L, existing.getVersion());
        verify(requestRepository).saveAndFlush(existing);
        verify(requestRepository, never()).delete(any(FuneralRequest.class));
    }

    @Test
    void permanentDeleteRemovesLoadedRequest() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));

        service.deletePermanentlyByUuid(REQUEST_UUID);

        verify(requestRepository).delete(existing);
        verify(requestRepository).flush();
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void defaultListQueriesOnlyActiveRequests() {
        FuneralRequest active = validRequest(5L, RequestStatus.NEW);
        when(requestRepository.findAll(any(Specification.class), eq(Sort.by("id")))).thenReturn(List.of(active));
        assertEquals(List.of(active), service.findAll());
        verify(requestRepository, never()).findAllByOrderByIdAsc();
    }

    @Test
    void missingCeremonyTypeIsNotFoundAndDisabledTypeCannotBeUsedForCreate() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(ceremonyTypes.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class, () -> service.create(validRequest(null, RequestStatus.NEW), 1L, 99L));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(new CeremonyType(1L, "BURIAL", "Захоронение", false)));
        assertThrows(BusinessException.class, () -> service.create(validRequest(null, RequestStatus.NEW), 1L, 1L));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void existingDisabledTypeCanBeReadAndKeptOnUpdate() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        existing.getCeremonyType().setEnabled(false);
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(ceremonyTypes.findById(1L)).thenReturn(Optional.of(existing.getCeremonyType()));
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);
        assertSame(existing, service.getByUuid(REQUEST_UUID));
        service.updateByUuid(REQUEST_UUID, validRequest(null, RequestStatus.CONFIRMED), 1L, 1L, null, 0L);
        assertFalse(existing.getCeremonyType().isEnabled());
        assertEquals(1L, existing.getCeremonyType().getId());
        assertEquals(RequestStatus.CONFIRMED, existing.getStatus());
    }

    @Test
    void updateCanAssignAnotherEnabledTypeButRejectsDisabledReplacement() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        CeremonyType replacement = new CeremonyType(2L, "CREMATION", "Кремация", false);
        when(requestRepository.findByUuid(REQUEST_UUID)).thenReturn(Optional.of(existing));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(ceremonyTypes.findById(2L)).thenReturn(Optional.of(replacement));
        FuneralRequest input = validRequest(null, RequestStatus.CONFIRMED);
        assertThrows(BusinessException.class,
                () -> service.updateByUuid(REQUEST_UUID, input, 1L, 2L, null, 0L));
        assertEquals(1L, existing.getCeremonyType().getId());
        verify(requestRepository, never()).saveAndFlush(any());
        replacement.setEnabled(true);
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);
        service.updateByUuid(REQUEST_UUID, input, 1L, 2L, null, 0L);
        assertSame(replacement, existing.getCeremonyType());
    }

    @Test
    void rejectsInvalidSearchSortAndDateRangeBeforeQueryingDatabase() {
        assertThrows(ValidationException.class, () -> service.search(null, null));
        assertThrows(ValidationException.class, () -> service.search("Имя", 1L));
        assertThrows(ValidationException.class, () -> service.search(" ", null));
        assertThrows(ValidationException.class, () -> service.search(null, 0L));
        assertThrows(ValidationException.class, () -> service.sort("unknown", true));
        assertThrows(ValidationException.class,
                () -> service.filter(null, null, LocalDate.now(), LocalDate.now().minusDays(1)));
        org.mockito.Mockito.verifyNoInteractions(requestRepository);
    }

    private FuneralRequest validRequest(Long id, RequestStatus status) {
        FuneralRequest request = new FuneralRequest(
                id,
                new Client(1L, "Клиент", "+79991234567", null),
                "Иванов Иван Иванович",
                LocalDate.now().plusDays(2),
                new CeremonyType(1L, "BURIAL", "Захоронение", true),
                status,
                new BigDecimal("10000.00"),
                LocalDateTime.now(),
                "Комментарий"
        );
        request.setUuid(REQUEST_UUID);
        request.setRequestCode("FR-2026-000005");
        request.setVersion(0L);
        request.setUpdatedAt(request.getCreatedAt());
        return request;
    }
}
