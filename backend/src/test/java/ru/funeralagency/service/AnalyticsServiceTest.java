package ru.funeralagency.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import ru.funeralagency.dto.AnalyticsSummaryDto;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    @Mock private FuneralRequestRepository repository;
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    @Test
    void returnsTotalsArchiveUrgencyAverageAndAllGroupsFromProjections() {
        when(repository.summarizeRequests()).thenReturn(totals(5, 3, 2, 3, "1000.00"));
        when(repository.countByCeremonyType()).thenReturn(List.of(
                projections.createProjection(FuneralRequestRepository.CeremonyTypeCount.class,
                        Map.of("ceremonyTypeId", 1L, "code", "BURIAL", "name", "Захоронение", "requestCount", 3L)),
                projections.createProjection(FuneralRequestRepository.CeremonyTypeCount.class,
                        Map.of("ceremonyTypeId", 99L, "code", "MEMORIAL", "name", "Прощание", "requestCount", 2L))));
        when(repository.countByStatus()).thenReturn(List.of(
                projections.createProjection(FuneralRequestRepository.StatusCount.class,
                        Map.of("status", RequestStatus.NEW, "requestCount", 3L)),
                projections.createProjection(FuneralRequestRepository.StatusCount.class,
                        Map.of("status", RequestStatus.COMPLETED, "requestCount", 2L))));
        when(repository.countByPriority()).thenReturn(List.of(
                projections.createProjection(FuneralRequestRepository.PriorityCount.class,
                        Map.of("priority", 0, "requestCount", 2L)),
                projections.createProjection(FuneralRequestRepository.PriorityCount.class,
                        Map.of("priority", 4, "requestCount", 3L))));

        var result = new AnalyticsService(repository).getSummary();

        assertEquals(5, result.totalRequests());
        assertEquals(3, result.activeRequests());
        assertEquals(2, result.archivedRequests());
        assertEquals(3, result.urgentRequests());
        assertEquals(new BigDecimal("200.00"), result.averagePrice());
        assertEquals(List.of(new AnalyticsSummaryDto.CeremonyTypeCount(1L, "BURIAL", "Захоронение", 3),
                new AnalyticsSummaryDto.CeremonyTypeCount(99L, "MEMORIAL", "Прощание", 2)), result.byCeremonyType());
        assertEquals(List.of(new AnalyticsSummaryDto.StatusCount(RequestStatus.NEW, 3),
                new AnalyticsSummaryDto.StatusCount(RequestStatus.COMPLETED, 2)), result.byStatus());
        assertEquals(List.of(new AnalyticsSummaryDto.PriorityCount(0, 2),
                new AnalyticsSummaryDto.PriorityCount(4, 3)), result.byPriority());
        verify(repository).summarizeRequests();
        verify(repository).countByCeremonyType();
        verify(repository).countByStatus();
        verify(repository).countByPriority();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void emptyDatabaseReturnsZeroAverageAndEmptyGroups() {
        when(repository.summarizeRequests()).thenReturn(totals(0, 0, 0, 0, "0"));
        var result = new AnalyticsService(repository).getSummary();
        assertEquals(0, result.totalRequests());
        assertEquals(0, result.activeRequests());
        assertEquals(0, result.archivedRequests());
        assertEquals(0, result.urgentRequests());
        assertEquals(new BigDecimal("0.00"), result.averagePrice());
        assertTrue(result.byCeremonyType().isEmpty());
        assertTrue(result.byStatus().isEmpty());
        assertTrue(result.byPriority().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"3,1.00,0.33", "8,1.00,0.13", "3,2.00,0.67", "2,19999999999.97,9999999999.99"})
    void monetaryAverageUsesExactSumAndHalfUpRounding(long count, String sum, String expected) {
        when(repository.summarizeRequests()).thenReturn(totals(count, count, 0, 0, sum));
        assertEquals(new BigDecimal(expected), new AnalyticsService(repository).getSummary().averagePrice());
    }

    private FuneralRequestRepository.SummaryTotals totals(long total, long active, long archived, long urgent, String sum) {
        return projections.createProjection(FuneralRequestRepository.SummaryTotals.class,
                Map.of("totalRequests", total, "activeRequests", active, "archivedRequests", archived,
                        "urgentRequests", urgent, "totalPrice", new BigDecimal(sum)));
    }
}
