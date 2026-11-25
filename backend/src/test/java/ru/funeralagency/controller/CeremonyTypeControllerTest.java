package ru.funeralagency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.repository.CeremonyTypeRepository;
import ru.funeralagency.service.CeremonyTypeService;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CeremonyTypeControllerTest {
    private CeremonyTypeRepository repository;
    private MockMvc mvc;
    private CeremonyType burial;

    @BeforeEach
    void setUp() {
        repository = mock(CeremonyTypeRepository.class);
        mvc = MockMvcBuilders.standaloneSetup(new CeremonyTypeController(new CeremonyTypeService(repository)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        burial = new CeremonyType(1L, "BURIAL", "Захоронение", true);
    }

    @Test
    void listAndGetReturnDtoIncludingDisabledTypes() throws Exception {
        when(repository.findAll(Sort.by("id"))).thenReturn(List.of(burial,
                new CeremonyType(2L, "CREMATION", "Кремация", false)));
        when(repository.findById(1L)).thenReturn(Optional.of(burial));
        mvc.perform(get("/api/ceremony-types"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1)).andExpect(jsonPath("$[0].code").value("BURIAL"))
                .andExpect(jsonPath("$[1].code").value("CREMATION")).andExpect(jsonPath("$[1].enabled").value(false));
        mvc.perform(get("/api/ceremony-types/1")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Захоронение"));
    }

    @Test
    void createNormalizesCodeAndNameAndStartsEnabled() throws Exception {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            CeremonyType value = invocation.getArgument(0);
            org.junit.jupiter.api.Assertions.assertNull(value.getId());
            org.junit.jupiter.api.Assertions.assertEquals("MEMORIAL", value.getCode());
            org.junit.jupiter.api.Assertions.assertEquals("Прощание", value.getName());
            org.junit.jupiter.api.Assertions.assertTrue(value.isEnabled());
            return new CeremonyType(3L, value.getCode(), value.getName(), value.isEnabled());
        });
        mvc.perform(post("/api/ceremony-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" memorial \",\"name\":\" Прощание \"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/ceremony-types/3"))
                .andExpect(jsonPath("$.code").value("MEMORIAL")).andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void updateChangesOnlyNameAndIgnoresSuppliedIdAndCode() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(burial));
        when(repository.saveAndFlush(burial)).thenReturn(burial);
        mvc.perform(put("/api/ceremony-types/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":99,\"code\":\"CHANGED\",\"name\":\" Новое название \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.code").value("BURIAL"))
                .andExpect(jsonPath("$.name").value("Новое название"));
    }

    @Test
    void enableAndDisableDoNotDeleteType() throws Exception {
        when(repository.findById(1L)).thenReturn(Optional.of(burial));
        when(repository.saveAndFlush(burial)).thenReturn(burial);
        mvc.perform(post("/api/ceremony-types/1/disable")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/ceremony-types/1/enable")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
        verify(repository, never()).delete(any(CeremonyType.class));
    }

    @Test
    void duplicateCodeReturns409AfterNormalization() throws Exception {
        when(repository.existsByCode("BURIAL")).thenReturn(true);
        mvc.perform(post("/api/ceremony-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\" burial \",\"name\":\"Другой тип\"}"))
                .andExpect(status().isConflict());
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"code\":\"\",\"name\":\"Тип\"}",
            "{\"code\":\"NEW\",\"name\":\"   \"}", "{\"name\":\"Тип\"}"})
    void blankNameOrCodeReturns400(String body) throws Exception {
        mvc.perform(post("/api/ceremony-types").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void longNameOrCodeReturns400() throws Exception {
        mvc.perform(post("/api/ceremony-types").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + "A".repeat(51) + "\",\"name\":\"Тип\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/ceremony-types/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "Я".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownIdReturns404AndOutOfRangeIdReturns400() throws Exception {
        mvc.perform(get("/api/ceremony-types/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/ceremony-types/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ceremony-types/4294967297")).andExpect(status().isBadRequest());
    }
}
