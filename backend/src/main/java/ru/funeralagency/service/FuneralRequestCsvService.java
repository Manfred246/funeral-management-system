package ru.funeralagency.service;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.CsvApplyDto;
import ru.funeralagency.dto.CsvImportMode;
import ru.funeralagency.dto.CsvImportSuccessDto;
import ru.funeralagency.dto.CsvPreviewDto;
import ru.funeralagency.dto.CsvRowErrorDto;
import ru.funeralagency.dto.FuneralRequestFilterDto;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FuneralRequestCsvService {
    private static final Logger logger = LoggerFactory.getLogger(FuneralRequestCsvService.class);
    private final FuneralRequestCsv csv;
    private final FuneralRequestCsvRowService rows;
    private final FuneralRequestService requests;

    public FuneralRequestCsvService(FuneralRequestCsv csv, FuneralRequestCsvRowService rows, FuneralRequestService requests) {
        this.csv = csv;
        this.rows = rows;
        this.requests = requests;
    }

    public byte[] export(FuneralRequestFilterDto filter, String sort) {
        return csv.write(requests.findForExport(filter, sort));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CsvPreviewDto preview(byte[] file, CsvImportMode mode) {
        CsvApplyDto result = process(file, mode, false);
        return new CsvPreviewDto(result.successful().size(), result.errors().size(), result.errors());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CsvApplyDto apply(byte[] file, CsvImportMode mode) {
        return process(file, mode, true);
    }

    private CsvApplyDto process(byte[] file, CsvImportMode mode, boolean apply) {
        if (mode == null) {
            throw new ValidationException("Укажите режим импорта");
        }
        FuneralRequestCsv.Parsed parsed = csv.read(file);
        if (parsed.fileError() != null) {
            return new CsvApplyDto(List.of(), List.of(parsed.fileError()));
        }
        Map<String, Integer> codes = new HashMap<>();
        for (FuneralRequestCsv.Row row : parsed.rows()) {
            String code = row.fields().getOrDefault("requestCode", "").trim();
            if (!code.isEmpty()) {
                codes.merge(code, 1, Integer::sum);
            }
        }
        List<CsvImportSuccessDto> successful = new ArrayList<>();
        List<CsvRowErrorDto> errors = new ArrayList<>();
        for (FuneralRequestCsv.Row row : parsed.rows()) {
            String code = row.fields().getOrDefault("requestCode", "").trim();
            if (row.error() != null) {
                errors.add(new CsvRowErrorDto(row.line(), row.error()));
                continue;
            }
            if (!code.isEmpty() && codes.get(code) > 1) {
                errors.add(new CsvRowErrorDto(row.line(), "Повтор requestCode в CSV: " + code));
                continue;
            }
            try {
                if (apply) {
                    // RowService завершается вместе с commit; только после этого строка считается успешной.
                    successful.add(rows.apply(row, mode));
                } else {
                    rows.preview(row, mode);
                    successful.add(new CsvImportSuccessDto(row.line(), null, null, false));
                }
            } catch (ValidationException | BusinessException | EntityNotFoundException exception) {
                errors.add(new CsvRowErrorDto(row.line(), exception.getMessage()));
            } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
                errors.add(new CsvRowErrorDto(row.line(), "Заявка изменена другим пользователем; повторите preview"));
            } catch (DataIntegrityViolationException exception) {
                errors.add(new CsvRowErrorDto(row.line(), "Конфликт ограничений данных или requestCode"));
            } catch (RuntimeException exception) {
                logger.error("Ошибка CSV в строке {}", row.line(), exception);
                errors.add(new CsvRowErrorDto(row.line(), "Не удалось обработать строку CSV"));
            }
        }
        return new CsvApplyDto(successful, errors);
    }
}
