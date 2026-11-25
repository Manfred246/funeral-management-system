package ru.funeralagency.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.CsvImportMode;
import ru.funeralagency.dto.CsvImportSuccessDto;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestStatus;
import ru.funeralagency.repository.FuneralRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class FuneralRequestCsvRowService {
    private final FuneralRequestService requests;
    private final FuneralRequestRepository repository;

    public FuneralRequestCsvRowService(FuneralRequestService requests, FuneralRequestRepository repository) {
        this.requests = requests;
        this.repository = repository;
    }

    private record Prepared(FuneralRequest candidate, FuneralRequest existing,
                            Long clientId, Long typeId, Boolean urgent, String code) {
    }

    @Transactional(readOnly = true)
    public void preview(FuneralRequestCsv.Row row, CsvImportMode mode) {
        prepare(row, mode);
    }

    @Transactional
    public CsvImportSuccessDto apply(FuneralRequestCsv.Row row, CsvImportMode mode) {
        Prepared input = prepare(row, mode);
        FuneralRequest saved;
        if (input.existing() == null) {
            if (mode == CsvImportMode.UPSERT_BY_REQUEST_CODE) {
                saved = requests.createFromImport(input.candidate(), input.clientId(), input.typeId(), input.code());
            } else {
                saved = requests.create(input.candidate(), input.clientId(), input.typeId());
            }
        } else {
            saved = requests.updateByUuid(input.existing().getUuid(), input.candidate(), input.clientId(),
                    input.typeId(), input.urgent(), input.existing().getVersion());
        }
        return new CsvImportSuccessDto(row.line(), saved.getUuid(), saved.getRequestCode(), input.existing() == null);
    }

    private Prepared prepare(FuneralRequestCsv.Row row, CsvImportMode mode) {
        Map<String, String> fields = row.fields();
        String code = fields.getOrDefault("requestCode", "").trim();
        if (mode == CsvImportMode.UPSERT_BY_REQUEST_CODE && code.isEmpty()) {
            throw new ValidationException("Для UPSERT_BY_REQUEST_CODE обязателен requestCode");
        }
        if (code.length() > 64) {
            throw new ValidationException("requestCode не должен превышать 64 символа");
        }
        FuneralRequest existing = code.isEmpty() ? null : repository.findByRequestCode(code).orElse(null);
        if (existing != null && mode == CsvImportMode.CREATE_ONLY) {
            throw new BusinessException("Заявка с requestCode " + code + " уже существует");
        }
        FuneralRequest candidate = new FuneralRequest();
        Long clientId;
        Long typeId;
        Boolean urgent;
        String field = "client";
        try {
            clientId = Long.valueOf(required(fields, "client"));
            field = "ceremonyType";
            typeId = Long.valueOf(required(fields, "ceremonyType"));
            candidate.setDeceasedFullName(required(fields, "deceasedFullName"));
            field = "ceremonyDate";
            candidate.setCeremonyDate(LocalDate.parse(required(fields, "ceremonyDate")));
            field = "status";
            candidate.setStatus(RequestStatus.valueOf(required(fields, "status")));
            field = "price";
            candidate.setPrice(new BigDecimal(required(fields, "price")));
            urgent = fields.containsKey("urgent") ? parseBoolean(required(fields, "urgent"), "urgent") : null;
            candidate.setUrgent(Boolean.TRUE.equals(urgent));
            candidate.setComment(fields.containsKey("comment") ? fields.get("comment") : existing == null ? null : existing.getComment());
            // Эти экспортные поля проверяются по формату, но не назначаются сущности.
            if (fields.containsKey("priority") && !fields.get("priority").isBlank()) {
                field = "priority";
                if (Integer.parseInt(fields.get("priority").trim()) < 0) {
                    throw new ValidationException("priority не может быть отрицательным");
                }
            }
            if (fields.containsKey("archived") && !fields.get("archived").isBlank()) {
                parseBoolean(fields.get("archived").trim(), "archived");
            }
            if (fields.containsKey("createdAt") && !fields.get("createdAt").isBlank()) {
                field = "createdAt";
                LocalDateTime.parse(fields.get("createdAt").trim());
            }
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ValidationException("Некорректное поле CSV: " + field);
        }
        if (existing == null) {
            if (candidate.getStatus() != RequestStatus.NEW) {
                throw new ValidationException("Новая заявка должна иметь статус NEW");
            }
            requests.validateCreate(candidate, clientId, typeId);
        } else {
            requests.validateUpdate(existing.getUuid(), candidate, clientId, typeId, existing.getVersion());
        }
        return new Prepared(candidate, existing, clientId, typeId, urgent, code);
    }

    private String required(Map<String, String> fields, String name) {
        String value = fields.get(name);
        if (value == null || value.isBlank()) {
            throw new ValidationException("Обязательное поле: " + name);
        }
        return value.trim();
    }

    private boolean parseBoolean(String value, String name) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new ValidationException(name + " должен быть true или false");
        }
        return Boolean.parseBoolean(value);
    }
}
