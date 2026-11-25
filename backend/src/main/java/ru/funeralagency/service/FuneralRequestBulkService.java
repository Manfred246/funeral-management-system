package ru.funeralagency.service;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.BulkOperation;
import ru.funeralagency.dto.FuneralRequestBulkDto;
import ru.funeralagency.dto.FuneralRequestBulkErrorDto;
import ru.funeralagency.dto.FuneralRequestBulkResponseDto;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
public class FuneralRequestBulkService {
    private static final Logger logger = LoggerFactory.getLogger(FuneralRequestBulkService.class);
    private final FuneralRequestService requests;

    public FuneralRequestBulkService(FuneralRequestService requests) {
        this.requests = requests;
    }

    // Методы другого Spring bean открывают и завершают отдельную транзакцию для каждой заявки.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FuneralRequestBulkResponseDto execute(FuneralRequestBulkDto dto) {
        validateOperation(dto);
        List<UUID> successful = new ArrayList<>();
        List<FuneralRequestBulkErrorDto> errors = new ArrayList<>();
        for (UUID uuid : new LinkedHashSet<>(dto.uuids())) {
            try {
                switch (dto.operation()) {
                    case ARCHIVE -> requests.archiveByUuid(uuid);
                    case RESTORE -> requests.restoreByUuid(uuid);
                    case CHANGE_STATUS -> requests.changeStatusByUuid(uuid, dto.status());
                    case ADD_TAG -> requests.addTagByUuid(uuid, dto.tagId());
                    case REMOVE_TAG -> requests.removeTagByUuid(uuid, dto.tagId());
                }
                // Успех записывается только после возврата из transactional proxy, включая commit.
                successful.add(uuid);
            } catch (EntityNotFoundException | BusinessException | ValidationException exception) {
                errors.add(new FuneralRequestBulkErrorDto(uuid, exception.getMessage()));
            } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
                errors.add(new FuneralRequestBulkErrorDto(uuid,
                        "Запись была изменена другим пользователем. Обновите данные и повторите попытку."));
            } catch (DataIntegrityViolationException exception) {
                errors.add(new FuneralRequestBulkErrorDto(uuid, "Операция нарушает ограничения связанных данных"));
            } catch (RuntimeException exception) {
                logger.error("Ошибка массовой операции {} для заявки {}", dto.operation(), uuid, exception);
                errors.add(new FuneralRequestBulkErrorDto(uuid, "Не удалось выполнить операцию с заявкой"));
            }
        }
        return new FuneralRequestBulkResponseDto(successful, errors);
    }

    private void validateOperation(FuneralRequestBulkDto dto) {
        if (dto == null || dto.operation() == null) {
            throw new ValidationException("Укажите массовую операцию");
        }
        if (dto.uuids() == null || dto.uuids().isEmpty()) {
            throw new ValidationException("Укажите непустой список UUID заявок без null");
        }
        for (UUID uuid : dto.uuids()) {
            if (uuid == null) {
                throw new ValidationException("UUID заявки обязателен");
            }
        }
        if (dto.operation() == BulkOperation.CHANGE_STATUS) {
            if (dto.status() == null) {
                throw new ValidationException("Для CHANGE_STATUS укажите status");
            }
        } else if (dto.status() != null) {
            throw new ValidationException("status допустим только для CHANGE_STATUS");
        }
        if (dto.operation() == BulkOperation.ADD_TAG || dto.operation() == BulkOperation.REMOVE_TAG) {
            if (dto.tagId() == null || dto.tagId() <= 0) {
                throw new ValidationException("Для операции с тегом укажите положительный tagId");
            }
        } else if (dto.tagId() != null) {
            throw new ValidationException("tagId допустим только для ADD_TAG и REMOVE_TAG");
        }
    }
}
