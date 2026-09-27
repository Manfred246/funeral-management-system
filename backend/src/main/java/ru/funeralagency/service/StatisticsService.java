package ru.funeralagency.service;

import org.springframework.stereotype.Service;
import ru.funeralagency.dto.StatisticsResponseDto;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Рассчитывает статистику по данным, полученным через репозитории. */
@Service
public class StatisticsService {
    private final ClientRepository clientRepository;
    private final FuneralRequestRepository requestRepository;

    public StatisticsService(
            ClientRepository clientRepository,
            FuneralRequestRepository requestRepository
    ) {
        this.clientRepository = clientRepository;
        this.requestRepository = requestRepository;
    }

    public StatisticsResponseDto getStatistics() {
        long totalClients = clientRepository.findAll().size();
        List<FuneralRequest> requests = requestRepository.findAll();

        long newRequests = 0;
        long completedRequests = 0;
        long cancelledRequests = 0;
        BigDecimal totalPrice = BigDecimal.ZERO;

        for (FuneralRequest request : requests) {
            if (request.getStatus() == RequestStatus.NEW) {
                newRequests++;
            } else if (request.getStatus() == RequestStatus.COMPLETED) {
                completedRequests++;
            } else if (request.getStatus() == RequestStatus.CANCELLED) {
                cancelledRequests++;
            }
            totalPrice = totalPrice.add(request.getPrice());
        }

        BigDecimal averagePrice = BigDecimal.ZERO.setScale(2);
        if (!requests.isEmpty()) {
            averagePrice = totalPrice.divide(
                    BigDecimal.valueOf(requests.size()),
                    2,
                    RoundingMode.HALF_UP
            );
        }

        return new StatisticsResponseDto(
                totalClients,
                requests.size(),
                newRequests,
                completedRequests,
                cancelledRequests,
                averagePrice
        );
    }
}
