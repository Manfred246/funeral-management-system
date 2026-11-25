package ru.funeralagency.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.model.Client;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.repository.ClientRepository;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class FuneralRequestQueryTest {
    @Mock private FuneralRequestRepository requests;
    @Mock private ClientRepository clients;
    private FuneralRequestService service;

    @BeforeEach
    void setUp() {
        service = new FuneralRequestService(requests, clients, new FuneralRequestMapper());
    }

    @ParameterizedTest
    @CsvSource({"createdAt,asc", "createdAt,desc", "ceremonyDate,asc", "ceremonyDate,desc",
            "price,asc", "price,desc", "priority,desc", "requestCode,asc"})
    void pageAndAllowListedSortArePassedToDatabaseAndDtosAreMapped(String field, String direction) {
        FuneralRequest request = new FuneralRequest();
        request.setId(15L);
        request.setUuid(UUID.randomUUID());
        request.setClient(new Client(1L, "Клиент", "+79991234567", null));
        when(requests.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(request), invocation.getArgument(1), 42));

        var result = service.findPage(new FuneralRequestFilterDto(), 1, 2, field + "," + direction);

        assertEquals(1, result.page());
        assertEquals(2, result.size());
        assertEquals(42, result.totalElements());
        assertEquals(21, result.totalPages());
        assertEquals(request.getUuid(), result.content().get(0).getUuid());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(requests).findAll(any(Specification.class), page.capture());
        assertEquals(2, page.getValue().getOffset());
        assertEquals(Sort.Direction.fromString(direction), page.getValue().getSort().getOrderFor(field).getDirection());
        assertEquals(Sort.Direction.ASC, page.getValue().getSort().getOrderFor("id").getDirection());
        verify(requests, never()).findAll();
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void invalidPageOrSizeIsRejectedBeforeDatabase(int page, int size) {
        assertThrows(ValidationException.class,
                () -> service.findPage(new FuneralRequestFilterDto(), page, size, "price,asc"));
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"id,asc", "client.fullName,asc", "price,up", "price", "price,asc,createdAt,desc"})
    void invalidSortIsRejected(String sort) {
        assertThrows(ValidationException.class,
                () -> service.findPage(new FuneralRequestFilterDto(), 0, 20, sort));
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @CsvSource({"-1,10", "0,-1", "20,10"})
    void invalidPriceBoundsAreRejected(String min, String max) {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setMinPrice(new BigDecimal(min));
        filter.setMaxPrice(new BigDecimal(max));
        assertThrows(ValidationException.class, () -> service.findPage(filter, 0, 20, "price,asc"));
        verifyNoInteractions(requests);
    }

    @Test
    void size100AndEqualPriceBoundsAreAccepted() {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setMinPrice(BigDecimal.TEN);
        filter.setMaxPrice(BigDecimal.TEN);
        when(requests.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        assertEquals(100, service.findPage(filter, 0, 100, null).size());
    }
}
