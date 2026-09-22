package ru.funeralagency.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.ClientResponseDto;
import ru.funeralagency.dto.ClientRequestDto;
import ru.funeralagency.mapper.ClientMapper;
import ru.funeralagency.model.Client;
import ru.funeralagency.service.ClientService;

import java.net.URI;
import java.util.List;

/** REST API для управления клиентами агентства. */
@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;
    private final ClientMapper clientMapper;

    public ClientController(ClientService clientService, ClientMapper clientMapper) {
        this.clientService = clientService;
        this.clientMapper = clientMapper;
    }

    @PostMapping
    public ResponseEntity<ClientResponseDto> create(@Valid @RequestBody ClientRequestDto dto) {
        Client createdClient = clientService.create(clientMapper.toEntity(dto));
        ClientResponseDto response = clientMapper.toResponseDto(createdClient);
        return ResponseEntity
                .created(URI.create("/api/clients/" + createdClient.getId()))
                .body(response);
    }

    @GetMapping
    public List<ClientResponseDto> findAll() {
        return clientMapper.toResponseDtoList(clientService.findAll());
    }

    @GetMapping("/{id}")
    public ClientResponseDto findById(@PathVariable Long id) {
        return clientMapper.toResponseDto(clientService.findById(id));
    }

    @PutMapping("/{id}")
    public ClientResponseDto update(
            @PathVariable Long id,
            @Valid @RequestBody ClientRequestDto dto
    ) {
        Client updatedClient = clientService.update(id, clientMapper.toEntity(dto, id));
        return clientMapper.toResponseDto(updatedClient);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        clientService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
