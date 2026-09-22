package ru.funeralagency.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.funeralagency.exception.InvalidFuneralRequestException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void createsRequestForExistingClient() {
        FuneralRequest request = validRequest(null, RequestStatus.CONFIRMED);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(client()));
        when(requestRepository.save(any())).thenAnswer(invocation -> {
            FuneralRequest saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        FuneralRequest created = service.create(request);

        assertEquals(10L, created.getId());
        assertEquals(RequestStatus.NEW, created.getStatus());
        assertNotNull(created.getCreatedAt());
        verify(requestRepository).save(request);
    }

    @Test
    void rejectsRequestForMissingClient() {
        FuneralRequest request = validRequest(null, RequestStatus.NEW);
        when(clientRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(InvalidFuneralRequestException.class, () -> service.create(request));

        verify(requestRepository, never()).save(any());
    }

    @Test
    void rejectsNewToCompletedTransition() {
        FuneralRequest existing = validRequest(5L, RequestStatus.NEW);
        FuneralRequest update = validRequest(null, RequestStatus.COMPLETED);
        when(requestRepository.findById(5L)).thenReturn(Optional.of(existing));

        assertThrows(InvalidFuneralRequestException.class, () -> service.update(5L, update));

        verify(requestRepository, never()).update(any());
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
        when(requestRepository.findAll()).thenReturn(List.of(first, second));

        assertEquals(List.of(second), service.search("иванов"));
        assertEquals(
                List.of(second),
                service.filter(RequestStatus.CONFIRMED, CeremonyType.CREMATION, null, null)
        );
        assertEquals(List.of(second, first), service.sort("price", true));
    }

    private Client client() {
        return new Client(1L, "Заказчик Иван", "+7 999 123 45 67", null);
    }

    private FuneralRequest validRequest(Long id, RequestStatus status) {
        return new FuneralRequest(
                id,
                1L,
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
