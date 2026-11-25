package ru.funeralagency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.funeralagency.model.Tag;
import ru.funeralagency.repository.TagRepository;
import ru.funeralagency.service.TagService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TagControllerTest {
    @Mock private TagRepository tags;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TagController(new TagService(tags)))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @Test
    void createTrimsNameAndReturnsCreatedDto() throws Exception {
        when(tags.saveAndFlush(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            assertEquals("VIP", tag.getName());
            return new Tag(3L, tag.getName());
        });
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  VIP  \"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/tags/3"))
                .andExpect(jsonPath("$.id").value(3)).andExpect(jsonPath("$.name").value("VIP"));
        verify(tags).existsByNameIgnoreCase("VIP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void blankNameIs400(String body) throws Exception {
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tags);
    }

    @Test
    void longNameIs400() throws Exception {
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tags);
    }

    @Test
    void duplicateIgnoringCaseIs409() throws Exception {
        when(tags.existsByNameIgnoreCase("vip")).thenReturn(true);
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"vip\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Тег с таким именем уже существует"));
        verify(tags, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateUsesExistingSafe409Policy() throws Exception {
        when(tags.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("internal SQL"));
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"VIP\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Операция нарушает ограничения связанных данных"));
    }

    @Test
    void listsAndGetsTagsAsDtos() throws Exception {
        when(tags.findAll(Sort.by("id"))).thenReturn(List.of(new Tag(3L, "VIP")));
        when(tags.findById(3L)).thenReturn(Optional.of(new Tag(3L, "VIP")));
        mvc.perform(get("/api/tags")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3)).andExpect(jsonPath("$[0].name").value("VIP"));
        mvc.perform(get("/api/tags/3")).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("VIP"));
        mvc.perform(get("/api/tags/4")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "invalid", "9223372036854775808"})
    void invalidIdIs400(String id) throws Exception {
        mvc.perform(get("/api/tags/" + id)).andExpect(status().isBadRequest());
        verifyNoInteractions(tags);
    }
}
