package ru.funeralagency.model;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import org.hibernate.annotations.BatchSize;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import ru.funeralagency.mapper.FuneralRequestMapper;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TagMappingTest {
    @Test
    void owningLazySetHasNoCascadesAndPageGraphDoesNotFetchCollection() throws Exception {
        var field = FuneralRequest.class.getDeclaredField("tags");
        var mapping = field.getAnnotation(ManyToMany.class);
        assertEquals(FetchType.LAZY, mapping.fetch());
        assertEquals(0, mapping.cascade().length);
        assertEquals("", mapping.mappedBy());
        var table = field.getAnnotation(JoinTable.class);
        assertEquals("funeral_request_tags", table.name());
        assertEquals("funeral_request_id", table.joinColumns()[0].name());
        assertEquals("tag_id", table.inverseJoinColumns()[0].name());
        assertEquals(100, field.getAnnotation(BatchSize.class).size());
        var pageMethod = FuneralRequestRepository.class.getMethod("findAll", Specification.class, Pageable.class);
        assertEquals(List.of("client", "ceremonyType"), List.of(pageMethod.getAnnotation(EntityGraph.class).attributePaths()));
    }

    @Test
    void collectionsAreIndependentAndMapperReturnsTagDtos() {
        FuneralRequest first = new FuneralRequest();
        FuneralRequest second = new FuneralRequest();
        first.setClient(new Client(1L, "Клиент", "+79991234567", null));
        second.setClient(first.getClient());
        first.getTags().add(new Tag(3L, "VIP"));
        assertTrue(second.getTags().isEmpty());
        var dto = new FuneralRequestMapper().toResponseDto(first);
        assertEquals(3L, dto.getTags().get(0).id());
        assertEquals("VIP", dto.getTags().get(0).name());
        assertTrue(new FuneralRequestMapper().toResponseDto(second).getTags().isEmpty());
    }
}
