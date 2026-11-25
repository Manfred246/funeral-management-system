package ru.funeralagency.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.funeralagency.config.SecurityConfig;
import ru.funeralagency.dto.AnalyticsSummaryDto;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.SecurityErrorHandler;
import ru.funeralagency.service.AnalyticsService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class})
class AnalyticsControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private UserRepository users;
    @MockitoBean private AnalyticsService analytics;

    private String token(Role role, boolean changeRequired) {
        User user = new User();
        user.setLogin("analytics-user");
        user.setName("Аналитика");
        user.setRoles(Set.of(role));
        user.setPasswordChangeRequired(changeRequired);
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        return "Bearer " + jwt.createAccessToken(user);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void allThreeRolesCanReadSummaryWithSafeDto(Role role) throws Exception {
        when(analytics.getSummary()).thenReturn(new AnalyticsSummaryDto(5, 3, 2, 3, new BigDecimal("200.00"),
                List.of(new AnalyticsSummaryDto.CeremonyTypeCount(99L, "MEMORIAL", "Прощание", 5)),
                List.of(new AnalyticsSummaryDto.StatusCount(RequestStatus.NEW, 5)),
                List.of(new AnalyticsSummaryDto.PriorityCount(4, 5))));
        mvc.perform(get("/api/analytics/summary").header("Authorization", token(role, false)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalRequests").value(5))
                .andExpect(jsonPath("$.activeRequests").value(3)).andExpect(jsonPath("$.archivedRequests").value(2))
                .andExpect(jsonPath("$.urgentRequests").value(3)).andExpect(jsonPath("$.averagePrice").value(200.00))
                .andExpect(jsonPath("$.byCeremonyType[0].ceremonyTypeId").value(99))
                .andExpect(jsonPath("$.byCeremonyType[0].code").value("MEMORIAL"))
                .andExpect(jsonPath("$.byCeremonyType[0].name").value("Прощание"))
                .andExpect(jsonPath("$.byCeremonyType[0].count").value(5))
                .andExpect(jsonPath("$.byStatus[0].status").value("NEW"))
                .andExpect(jsonPath("$.byStatus[0].count").value(5))
                .andExpect(jsonPath("$.byPriority[0].priority").value(4))
                .andExpect(jsonPath("$.byPriority[0].count").value(5));
    }

    @Test
    void missingJwtIs401AndDoesNotCallService() throws Exception {
        mvc.perform(get("/api/analytics/summary")).andExpect(status().isUnauthorized());
        verifyNoInteractions(analytics);
    }

    @Test
    void temporaryPasswordStillBlocksAnalytics() throws Exception {
        mvc.perform(get("/api/analytics/summary").header("Authorization", token(Role.ADMIN, true)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(analytics);
    }
}
