package ru.funeralagency.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.Tag;
import ru.funeralagency.repository.TagRepository;

import java.util.List;

@Service
public class TagService {
    private final TagRepository repository;

    public TagService(TagRepository repository) {
        this.repository = repository;
    }

    public List<Tag> getAll() {
        return repository.findAll(Sort.by("id"));
    }

    public Tag getById(Long id) {
        if (id == null || id <= 0) {
            throw new ValidationException("ID тега должен быть положительным");
        }
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Тег с ID " + id + " не найден"));
    }

    @Transactional
    public Tag create(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 50) {
            throw new ValidationException("Имя тега обязательно и не должно превышать 50 символов");
        }
        String normalizedName = name.trim();
        if (repository.existsByNameIgnoreCase(normalizedName)) {
            throw new BusinessException("Тег с таким именем уже существует");
        }
        return repository.saveAndFlush(new Tag(null, normalizedName));
    }
}
