package ru.funeralagency.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class FuneralRequestSpecificationsTest {
    private Root<FuneralRequest> root;
    private CriteriaBuilder cb;
    private CriteriaQuery<?> query;

    @BeforeEach
    void setUp() {
        root = mock(Root.class, RETURNS_DEEP_STUBS);
        cb = mock(CriteriaBuilder.class);
        query = mock(CriteriaQuery.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void archivedHasAnExplicitEqualityPredicate(boolean archived) {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setArchived(archived);
        FuneralRequestSpecifications.matching(filter).toPredicate(root, query, cb);
        verify(cb).equal(root.get("archived"), archived);
    }

    @ParameterizedTest
    @MethodSource("equalityFilters")
    void optionalFiltersUseEquality(Consumer<FuneralRequestFilterDto> configure, String field, Object value) {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        configure.accept(filter);
        FuneralRequestSpecifications.matching(filter).toPredicate(root, query, cb);
        if ("client".equals(field) || "ceremonyType".equals(field)) {
            verify(cb).equal(root.get(field).get("id"), value);
        } else {
            verify(cb).equal(root.get(field), value);
        }
        verify(cb).equal(root.get("archived"), false);
    }

    static Stream<Arguments> equalityFilters() {
        return Stream.of(
                Arguments.of((Consumer<FuneralRequestFilterDto>) f -> f.setClient(2L), "client", 2L),
                Arguments.of((Consumer<FuneralRequestFilterDto>) f -> f.setCeremonyTypeId(2L),
                        "ceremonyType", 2L),
                Arguments.of((Consumer<FuneralRequestFilterDto>) f -> f.setStatus(RequestStatus.NEW),
                        "status", RequestStatus.NEW),
                Arguments.of((Consumer<FuneralRequestFilterDto>) f -> f.setUrgent(true), "urgent", true),
                Arguments.of((Consumer<FuneralRequestFilterDto>) f -> f.setUrgent(false), "urgent", false),
                Arguments.of((Consumer<FuneralRequestFilterDto>) f -> f.setPriority(3), "priority", 3));
    }

    @Test
    void absentUrgentAndPriorityDoNotAddPredicates() {
        FuneralRequestSpecifications.matching(new FuneralRequestFilterDto()).toPredicate(root, query, cb);
        ArgumentCaptor<Predicate[]> predicates = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicates.capture());
        assertEquals(1, predicates.getValue().length);
    }

    @ParameterizedTest
    @MethodSource("literalSearches")
    void qSearchesThreeLowercaseFieldsWithEscapedLiteralInput(String text, String pattern) {
        when(cb.lower(any())).thenAnswer(invocation -> invocation.getArgument(0));
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setQ(text);
        FuneralRequestSpecifications.matching(filter).toPredicate(root, query, cb);
        verify(cb).lower(root.get("deceasedFullName"));
        verify(cb).lower(root.get("requestCode"));
        verify(cb).lower(root.get("client").get("fullName"));
        verify(cb).like(root.get("deceasedFullName"), pattern, '!');
        verify(cb).like(root.get("requestCode"), pattern, '!');
        verify(cb).like(root.get("client").get("fullName"), pattern, '!');
        verify(cb).or(any(), any(), any());
    }

    static Stream<Arguments> literalSearches() {
        return Stream.of(Arguments.of("ИВАНОВ", "%иванов%"),
                Arguments.of("FR-2026-000015", "%fr-2026-000015%"),
                Arguments.of("50%_!", "%50!%!_!!%"),
                Arguments.of("O'Brien", "%o'brien%"),
                Arguments.of("' OR 1=1 --", "%' or 1=1 --%"));
    }

    @ParameterizedTest
    @MethodSource("priceBounds")
    void priceBoundsAreInclusive(BigDecimal min, BigDecimal max) {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setMinPrice(min);
        filter.setMaxPrice(max);
        FuneralRequestSpecifications.matching(filter).toPredicate(root, query, cb);
        if (min != null) {
            verify(cb).greaterThanOrEqualTo(root.<BigDecimal>get("price"), min);
        }
        if (max != null) {
            verify(cb).lessThanOrEqualTo(root.<BigDecimal>get("price"), max);
        }
    }

    static Stream<Arguments> priceBounds() {
        return Stream.of(Arguments.of(new BigDecimal("10.00"), null),
                Arguments.of(null, new BigDecimal("20.00")),
                Arguments.of(new BigDecimal("10.00"), new BigDecimal("20.00")));
    }

    @Test
    void allFiltersAreCombinedWithAnd() {
        FuneralRequestFilterDto filter = new FuneralRequestFilterDto();
        filter.setQ("Иванов");
        filter.setClient(1L);
        filter.setCeremonyTypeId(1L);
        filter.setStatus(RequestStatus.NEW);
        filter.setUrgent(true);
        filter.setPriority(4);
        filter.setMinPrice(BigDecimal.ZERO);
        filter.setMaxPrice(new BigDecimal("100"));
        FuneralRequestSpecifications.matching(filter).toPredicate(root, query, cb);
        ArgumentCaptor<Predicate[]> predicates = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(predicates.capture());
        assertEquals(9, predicates.getValue().length);
    }

    @Test
    void legacyDateFilterUsesInclusiveBoundaries() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(7);
        FuneralRequestSpecifications.ceremonyDateBetween(from, to).toPredicate(root, query, cb);
        verify(cb).greaterThanOrEqualTo(root.<LocalDate>get("ceremonyDate"), from);
        verify(cb).lessThanOrEqualTo(root.<LocalDate>get("ceremonyDate"), to);
    }
}
