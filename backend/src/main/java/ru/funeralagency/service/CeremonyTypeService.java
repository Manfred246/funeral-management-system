package ru.funeralagency.service;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.repository.CeremonyTypeRepository;

import java.util.List;
import java.util.Locale;

@Service
public class CeremonyTypeService {
    private final CeremonyTypeRepository repository;

    public CeremonyTypeService(CeremonyTypeRepository repository) {
        this.repository = repository;
    }

    public List<CeremonyType> getAll() {
        return repository.findAll(Sort.by("id"));
    }

    public CeremonyType getById(Long id) {
        if (id == null || id <= 0 || id > Integer.MAX_VALUE) {
            throw new ValidationException("ID типа церемонии должен быть от 1 до " + Integer.MAX_VALUE);
        }
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException(
                "Тип церемонии с ID " + id + " не найден"));
    }

    @Transactional
    public CeremonyType create(String code, String name) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("Код типа церемонии обязателен");
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.length() > 50) {
            throw new ValidationException("Код типа церемонии не должен превышать 50 символов");
        }
        String normalizedName = validateName(name);
        if (repository.existsByCode(normalizedCode)) {
            throw new BusinessException("Тип церемонии с таким кодом уже существует");
        }
        return repository.saveAndFlush(new CeremonyType(null, normalizedCode, normalizedName, true));
    }

    @Transactional
    public CeremonyType update(Long id, String name) {
        CeremonyType type = getById(id);
        type.setName(validateName(name));
        return repository.saveAndFlush(type);
    }

    @Transactional
    public CeremonyType enable(Long id) {
        CeremonyType type = getById(id);
        type.setEnabled(true);
        return repository.saveAndFlush(type);
    }

    @Transactional
    public CeremonyType disable(Long id) {
        CeremonyType type = getById(id);
        type.setEnabled(false);
        return repository.saveAndFlush(type);
    }

    private String validateName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 100) {
            throw new ValidationException("Название типа церемонии обязательно и не должно превышать 100 символов");
        }
        return name.trim();
    }
}
