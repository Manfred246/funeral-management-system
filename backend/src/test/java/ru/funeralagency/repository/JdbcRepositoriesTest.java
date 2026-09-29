package ru.funeralagency.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
@Import({JdbcClientRepository.class, JdbcFuneralRequestRepository.class})
class JdbcRepositoriesTest {

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private FuneralRequestRepository requestRepository;

    @Test
    void storesClientAndFuneralRequest() {
        Client client = clientRepository.save(
                new Client("Иванов Иван", "+7 999 123 45 67", "ivan@example.com")
        );
        FuneralRequest request = requestRepository.save(new FuneralRequest(
                null,
                client.getId(),
                "Петров Пётр Петрович",
                LocalDate.now().plusDays(3),
                CeremonyType.CREMATION,
                RequestStatus.NEW,
                new BigDecimal("15000.00"),
                LocalDateTime.now(),
                null
        ));

        assertNotNull(client.getId());
        assertNotNull(request.getId());
        assertTrue(requestRepository.existsByClientId(client.getId()));
        assertEquals(
                "Петров Пётр Петрович",
                requestRepository.findById(request.getId()).orElseThrow().getDeceasedFullName()
        );
    }
}
