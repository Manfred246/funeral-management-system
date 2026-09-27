package ru.funeralagency.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.StatisticsResponseDto;
import ru.funeralagency.service.StatisticsService;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    public StatisticsResponseDto getStatistics() {
        return statisticsService.getStatistics();
    }
}
