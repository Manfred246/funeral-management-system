package ru.funeralagency.validation;

import org.springframework.stereotype.Component;
import ru.funeralagency.dto.ClientRequestDto;
import ru.funeralagency.exception.InvalidClientException;
import ru.funeralagency.model.Client;

/** Проверяет данные клиентского запроса до преобразования в доменную модель. */
@Component
public class ClientRequestValidator {

    public void validate(ClientRequestDto dto) {
        if (dto == null) {
            throw new InvalidClientException("Данные клиента обязательны");
        }

        Client client = new Client(dto.getFullName(), dto.getPhone(), dto.getEmail());
        ClientValidationRules.normalize(client);
        ClientValidationRules.validate(client);
    }
}
