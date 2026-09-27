package ru.funeralagency.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.funeralagency.dto.StatisticsResponseDto;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    @Mock
    private ClientRepository clientRepository;

    @Mock
    private FuneralRequestRepository requestRepository;

    @Test
    void calculatesAllRequiredIndicators() {
        when(clientRepository.findAll()).thenReturn(List.of(new ru.funeralagency.model.Client(),
                new ru.funeralagency.model.Client()));
        when(requestRepository.findAll()).thenReturn(List.of(
                request(RequestStatus.NEW, "100.00"),
                request(RequestStatus.COMPLETED, "200.00"),
                request(RequestStatus.CANCELLED, "300.00"),
                request(RequestStatus.CONFIRMED, "400.00")
        ));

        StatisticsResponseDto result = new StatisticsService(
                clientRepository,
                requestRepository
        ).getStatistics();

        assertEquals(2, result.getTotalClients());
        assertEquals(4, result.getTotalRequests());
        assertEquals(1, result.getNewRequests());
        assertEquals(1, result.getCompletedRequests());
        assertEquals(1, result.getCancelledRequests());
        assertEquals(new BigDecimal("250.00"), result.getAveragePrice());
    }

    @Test
    void returnsZeroAverageForEmptyRequestList() {
        when(clientRepository.findAll()).thenReturn(List.of());
        when(requestRepository.findAll()).thenReturn(List.of());

        StatisticsResponseDto result = new StatisticsService(
                clientRepository,
                requestRepository
        ).getStatistics();

        assertEquals(0, result.getTotalClients());
        assertEquals(0, result.getTotalRequests());
        assertEquals(new BigDecimal("0.00"), result.getAveragePrice());
    }

    private FuneralRequest request(RequestStatus status, String price) {
        FuneralRequest request = new FuneralRequest();
        request.setStatus(status);
        request.setPrice(new BigDecimal(price));
        return request;
    }
}
