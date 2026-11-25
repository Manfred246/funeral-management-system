package ru.funeralagency.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FuneralRequestServiceTest {

    @Mock
    private FuneralRequestRepository requestRepository;

    @Mock
    private ClientRepository clientRepository;

    private FuneralRequestService service;

    @BeforeEach
    void setUp() {
        service = new FuneralRequestService(requestRepository, clientRepository);
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

        FuneralRequest created = service.create(request, 1L);

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

        assertThrows(ValidationException.class, () -> service.create(request, 1L));

        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNewToCompletedTransition() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        FuneralRequest update = validRequest(null, RequestStatus.COMPLETED);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.update(5L, update, 1L, null));

        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void searchesFiltersAndSortsRequests() {
        FuneralRequest first = validRequest(1L, RequestStatus.NEW);
        first.setDeceasedFullName("Петров Пётр");
        first.setPrice(new BigDecimal("200.00"));
        FuneralRequest second = validRequest(2L, RequestStatus.CONFIRMED);
        second.setDeceasedFullName("Иванов Иван");
        second.setCeremonyType(CeremonyType.CREMATION);
        second.setPrice(new BigDecimal("100.00"));
        when(requestRepository.findByDeceasedFullNameContainingIgnoreCaseOrderByIdAsc("иванов")).thenReturn(List.of(second));
        when(requestRepository.findByClientIdOrderByIdAsc(1L)).thenReturn(List.of(first, second));
        when(requestRepository.findFiltered(RequestStatus.CONFIRMED, CeremonyType.CREMATION, null, null))
                .thenReturn(List.of(second));
        when(requestRepository.findAll(Sort.by(Sort.Direction.ASC, "price").and(Sort.by("id")))).thenReturn(List.of(second, first));

        assertEquals(List.of(second), service.search("иванов", null));
        assertEquals(List.of(first, second), service.search(null, 1L));
        assertEquals(
                List.of(second),
                service.filter(RequestStatus.CONFIRMED, CeremonyType.CREMATION, null, null)
        );
        assertEquals(List.of(second, first), service.sort("price", true));
        verify(requestRepository, never()).findAll();
    }

    private Client client() {
        return new Client(1L, "Заказчик Иван", "+7 999 123 45 67", null);
    }

    @Test
    void updatePreservesIdentifiersCreationTimeAndVersionButRecalculatesPriority() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        UUID uuid = UUID.randomUUID();
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
        when(requestRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);

        assertSame(existing, service.update(5L, input, 1L, true));

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
    void legacyUpdateKeepsUrgentUnlessExplicitlyChanged(String urgentValue, boolean expectedUrgent, int expectedPriority) {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        existing.setUrgent(true);
        FuneralRequest input = validRequest(null, RequestStatus.CONFIRMED);
        input.setCeremonyDate(LocalDate.now().plusDays(30));
        when(requestRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(requestRepository.saveAndFlush(existing)).thenReturn(existing);
        Boolean urgent = "null".equals(urgentValue) ? null : Boolean.valueOf(urgentValue);

        FuneralRequest updated = service.update(5L, input, 1L, urgent);

        assertEquals(expectedUrgent, updated.isUrgent());
        assertEquals(expectedPriority, updated.getPriority());
    }

    @Test
    void missingRequestCannotBeUpdated() {
        when(requestRepository.findById(5L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> service.update(5L, validRequest(null, RequestStatus.CONFIRMED), 1L, null));
        verify(requestRepository, never()).saveAndFlush(any());
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
        return new FuneralRequest(
                id,
                new Client(1L, "Клиент", "+79991234567", null),
                "Иванов Иван Иванович",
                LocalDate.now().plusDays(2),
                CeremonyType.BURIAL,
                status,
                new BigDecimal("10000.00"),
                LocalDateTime.now(),
                "Комментарий"
        );
    }
}
