package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.funeralagency.model.RequestNote;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestNoteRepository extends JpaRepository<RequestNote, UUID> {
    @EntityGraph(attributePaths = "author")
    List<RequestNote> findAllByRequestIdOrderByPinnedDescCreatedAtDescUuidAsc(Long requestId);

    @EntityGraph(attributePaths = "author")
    Optional<RequestNote> findByUuidAndRequestId(UUID uuid, Long requestId);
}
