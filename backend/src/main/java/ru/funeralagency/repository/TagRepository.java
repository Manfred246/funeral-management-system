package ru.funeralagency.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.funeralagency.model.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {
    boolean existsByNameIgnoreCase(String name);
}
