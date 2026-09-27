package ru.funeralagency.mapper;

import org.springframework.stereotype.Component;
import ru.funeralagency.dto.ClientRequestDto;
import ru.funeralagency.dto.ClientResponseDto;
import ru.funeralagency.model.Client;

import java.util.ArrayList;
import java.util.List;

@Component
public class ClientMapper {
    public Client toEntity(ClientRequestDto dto) {
        Client client = new Client();
        client.setFullName(dto.getFullName());
        client.setPhone(dto.getPhone());
        client.setEmail(dto.getEmail());
        return client;
    }

    public ClientResponseDto toResponseDto(Client client) {
        return new ClientResponseDto(client.getId(), client.getFullName(), client.getPhone(), client.getEmail());
    }

    public List<ClientResponseDto> toResponseDtoList(List<Client> clients) {
        List<ClientResponseDto> result = new ArrayList<>();
        for (Client client : clients) {
            result.add(toResponseDto(client));
        }
        return result;
    }
}