package ru.funeralagency.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import ru.funeralagency.config.SecurityConfig;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestNote;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.RequestNoteRepository;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.SecurityErrorHandler;
import ru.funeralagency.service.FuneralRequestService;
import ru.funeralagency.service.RequestNoteService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RequestNoteController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class, RequestNoteService.class})
class RequestNoteControllerTest {
    private static final UUID REQUEST_UUID = UUID.fromString("8d44b5ce-45e2-487d-81d8-b714004db0d7");
    private static final UUID NOTE_UUID = UUID.fromString("ec742d94-20f4-48c7-a944-e2e21c8b117d");
    private static final String PATH = "/api/requests/" + REQUEST_UUID + "/notes";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 1, 12, 0);
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @MockitoBean private RequestNoteRepository notes;
    @MockitoBean private FuneralRequestService requests;
    @MockitoBean private UserRepository users;
    private User currentUser;
    private FuneralRequest request;
    private RequestNote stored;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(7L);
        currentUser.setLogin("manager");
        currentUser.setName("Менеджер");
        currentUser.setPasswordHash("never-return-this-hash");
        request = new FuneralRequest();
        request.setId(5L);
        request.setUuid(REQUEST_UUID);
        stored = new RequestNote();
        ReflectionTestUtils.setField(stored, "uuid", NOTE_UUID);
        stored.setRequest(request);
        stored.setAuthor(currentUser);
        stored.setText("Исходный текст");
        stored.setCreatedAt(CREATED_AT);
        stored.setUpdatedAt(CREATED_AT);
        when(requests.getByUuid(REQUEST_UUID)).thenReturn(request);
        when(notes.findAllByRequestIdOrderByPinnedDescCreatedAtDescUuidAsc(5L)).thenReturn(List.of(stored));
        when(notes.findByUuidAndRequestId(NOTE_UUID, 5L)).thenReturn(Optional.of(stored));
        when(notes.saveAndFlush(any(RequestNote.class))).thenAnswer(invocation -> {
            RequestNote note = invocation.getArgument(0);
            if (note.getUuid() == null) {
                ReflectionTestUtils.setField(note, "uuid", NOTE_UUID);
            }
            return note;
        });
    }

    private String token(Role role) {
        currentUser.setRoles(Set.of(role));
        when(users.findByUuid(currentUser.getUuid())).thenReturn(Optional.of(currentUser));
        return "Bearer " + jwt.createAccessToken(currentUser);
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,GET,200", "MANAGER,GET,200", "ADMIN,GET,200",
            "VIEWER,POST,403", "MANAGER,POST,201", "ADMIN,POST,201",
            "VIEWER,PUT,403", "MANAGER,PUT,200", "ADMIN,PUT,200",
            "VIEWER,DELETE,403", "MANAGER,DELETE,204", "ADMIN,DELETE,204"})
    void roleMatrixUsesRealSecurityChainAndService(Role role, String method, int expected) throws Exception {
        String path = PATH + (("PUT".equals(method) || "DELETE".equals(method)) ? "/" + NOTE_UUID : "");
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", token(role))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Текст\",\"pinned\":true}"))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verifyNoInteractions(notes, requests);
        }
    }

    @Test
    void createsTrimmedTextWithAuthenticatedAuthorIgnoringSpoofedBodyFields() throws Exception {
        mvc.perform(post(PATH).header("Authorization", token(Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  Позвонить клиенту  \",\"pinned\":true,\"author\":999,\"authorUuid\":\""
                                + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", PATH + "/" + NOTE_UUID))
                .andExpect(jsonPath("$.uuid").value(NOTE_UUID.toString()))
                .andExpect(jsonPath("$.authorUuid").value(currentUser.getUuid().toString()))
                .andExpect(jsonPath("$.authorName").value("Менеджер"))
                .andExpect(jsonPath("$.text").value("Позвонить клиенту"))
                .andExpect(jsonPath("$.pinned").value(true))
                .andExpect(jsonPath("$.author").doesNotExist()).andExpect(jsonPath("$.request").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        ArgumentCaptor<RequestNote> saved = ArgumentCaptor.forClass(RequestNote.class);
        verify(notes).saveAndFlush(saved.capture());
        assertSame(currentUser, saved.getValue().getAuthor());
        assertSame(request, saved.getValue().getRequest());
        assertEquals(saved.getValue().getCreatedAt(), saved.getValue().getUpdatedAt());
        assertEquals(0, saved.getValue().getCreatedAt().getNano() % 1000);
    }

    @Test
    void updateCanEditAnotherAuthorsNoteAndPreservesAuthorUuidAndCreationTime() throws Exception {
        User originalAuthor = new User();
        originalAuthor.setId(8L);
        originalAuthor.setName("Автор");
        stored.setAuthor(originalAuthor);
        stored.setPinned(true);
        mvc.perform(put(PATH + "/" + NOTE_UUID).header("Authorization", token(Role.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  Новый текст  \",\"pinned\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.text").value("Новый текст"))
                .andExpect(jsonPath("$.pinned").value(false))
                .andExpect(jsonPath("$.authorUuid").value(originalAuthor.getUuid().toString()));
        assertSame(originalAuthor, stored.getAuthor());
        assertEquals(NOTE_UUID, stored.getUuid());
        assertEquals(CREATED_AT, stored.getCreatedAt());
        assertTrue(stored.getUpdatedAt().isAfter(CREATED_AT));
        verify(notes).saveAndFlush(stored);
    }

    @Test
    void deleteOnlyDeletesScopedNote() throws Exception {
        mvc.perform(delete(PATH + "/" + NOTE_UUID).header("Authorization", token(Role.ADMIN)))
                .andExpect(status().isNoContent());
        verify(notes).findByUuidAndRequestId(NOTE_UUID, 5L);
        verify(notes).delete(stored);
        verify(notes).flush();
        verify(users, never()).delete(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"text\":null}", "{\"text\":\"\"}", "{\"text\":\"   \",\"pinned\":true}"})
    void blankTextOnCreateAndUpdateIs400(String body) throws Exception {
        String authorization = token(Role.MANAGER);
        mvc.perform(post(PATH).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PATH + "/" + NOTE_UUID).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notes, requests);
    }

    @Test
    void archivedNotesReadableAndAllMutationsAre409() throws Exception {
        request.setArchived(true);
        String authorization = token(Role.ADMIN);
        mvc.perform(get(PATH).header("Authorization", authorization)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Исходный текст"));
        for (String method : new String[]{"POST", "PUT", "DELETE"}) {
            String path = PATH + ("POST".equals(method) ? "" : "/" + NOTE_UUID);
            mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Текст\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Нельзя изменять заметки архивной заявки"));
        }
        verify(notes, never()).saveAndFlush(any());
        verify(notes, never()).delete(any());
    }

    @Test
    void absentNoteOrNoteFromAnotherRequestIs404() throws Exception {
        UUID otherNote = UUID.randomUUID();
        String authorization = token(Role.MANAGER);
        mvc.perform(put(PATH + "/" + otherNote).header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Текст\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/" + otherNote).header("Authorization", authorization))
                .andExpect(status().isNotFound());
        verify(notes, times(2)).findByUuidAndRequestId(otherNote, 5L);
        verify(notes, never()).findById(any());
        verify(notes, never()).saveAndFlush(any());
        verify(notes, never()).delete(any());
    }

    @Test
    void unknownRequestIs404ForEveryOperation() throws Exception {
        UUID otherRequest = UUID.randomUUID();
        when(requests.getByUuid(otherRequest)).thenThrow(new EntityNotFoundException("Заявка не найдена"));
        String authorization = token(Role.ADMIN);
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
            String path = "/api/requests/" + otherRequest + "/notes"
                    + (("PUT".equals(method) || "DELETE".equals(method)) ? "/" + NOTE_UUID : "");
            mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Текст\"}"))
                    .andExpect(status().isNotFound());
        }
        verifyNoInteractions(notes);
    }

    @Test
    void malformedPathsAre400() throws Exception {
        String authorization = token(Role.ADMIN);
        mvc.perform(get("/api/requests/bad/notes").header("Authorization", authorization))
                .andExpect(status().isBadRequest());
        mvc.perform(put(PATH + "/bad").header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Текст\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(PATH + "/bad").header("Authorization", authorization)).andExpect(status().isBadRequest());
        verifyNoInteractions(notes, requests);
    }

    @Test
    void everyOperationRequiresAuthentication() throws Exception {
        for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
            String path = PATH + (("PUT".equals(method) || "DELETE".equals(method)) ? "/" + NOTE_UUID : "");
            mvc.perform(request(HttpMethod.valueOf(method), path)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"Текст\"}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(notes, requests, users);
    }
}
