package ru.funeralagency.mapper;

import org.springframework.stereotype.Component;
import ru.funeralagency.dto.ClientRequestDto;
import ru.funeralagency.dto.ClientResponseDto;
import ru.funeralagency.dto.CreateClientDto;
import ru.funeralagency.dto.UpdateClientDto;
import ru.funeralagency.model.Client;

import java.util.List;
import java.util.Objects;

/** Преобразует транспортные DTO в доменную модель и обратно. */
@Component
public class ClientMapper {

    public Client toEntity(ClientRequestDto dto) {
        Objects.requireNonNull(dto, "ClientRequestDto не может быть null");
        return new Client(
                normalize(dto.getFullName()),
                normalize(dto.getPhone()),
                normalizeNullable(dto.getEmail())
        );
    }

    public Client toEntity(ClientRequestDto dto, Long id) {
        Client client = toEntity(dto);
        client.setId(id);
        return client;
    }

    public Client toEntity(CreateClientDto dto) {
        Objects.requireNonNull(dto, "CreateClientDto не может быть null");
        return new Client(
                normalize(dto.getFullName()),
                normalize(dto.getPhone()),
                normalizeNullable(dto.getEmail())
        );
    }

    public Client toEntity(UpdateClientDto dto, Long id) {
        Objects.requireNonNull(dto, "UpdateClientDto не может быть null");
        return new Client(
                id,
                normalize(dto.getFullName()),
                normalize(dto.getPhone()),
                normalizeNullable(dto.getEmail())
        );
    }

    public Client toEntity(UpdateClientDto dto, Client client) {
        Objects.requireNonNull(dto, "UpdateClientDto не может быть null");
        Objects.requireNonNull(client, "Client не может быть null");
        client.setFullName(normalize(dto.getFullName()));
        client.setPhone(normalize(dto.getPhone()));
        client.setEmail(normalizeNullable(dto.getEmail()));
        return client;
    }

    public ClientResponseDto toResponseDto(Client client) {
        Objects.requireNonNull(client, "Client не может быть null");
        return new ClientResponseDto(
                client.getId(),
                client.getFullName(),
                client.getPhone(),
                client.getEmail()
        );
    }

    public List<ClientResponseDto> toResponseDtoList(List<Client> clients) {
        Objects.requireNonNull(clients, "Список клиентов не может быть null");
        return clients.stream().map(this::toResponseDto).toList();
    }

    // Явные имена-синонимы делают назначение преобразований очевидным для вызывающего кода.
    public Client toClient(CreateClientDto dto) {
        return toEntity(dto);
    }

    public Client toClient(ClientRequestDto dto) {
        return toEntity(dto);
    }

    public Client toClient(UpdateClientDto dto, Long id) {
        return toEntity(dto, id);
    }

    public ClientResponseDto toDto(Client client) {
        return toResponseDto(client);
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
