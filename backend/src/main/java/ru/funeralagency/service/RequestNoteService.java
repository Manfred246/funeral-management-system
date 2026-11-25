package ru.funeralagency.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.funeralagency.dto.RequestNoteResponseDto;
import ru.funeralagency.exception.BusinessException;
import ru.funeralagency.exception.EntityNotFoundException;
import ru.funeralagency.exception.ValidationException;
import ru.funeralagency.mapper.RequestNoteMapper;
import ru.funeralagency.model.FuneralRequest;
import ru.funeralagency.model.RequestNote;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.RequestNoteRepository;
import ru.funeralagency.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class RequestNoteService {
    private final RequestNoteRepository notes;
    private final FuneralRequestService requests;
    private final UserRepository users;

    public RequestNoteService(RequestNoteRepository notes, FuneralRequestService requests, UserRepository users) {
        this.notes = notes;
        this.requests = requests;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<RequestNoteResponseDto> getAll(UUID requestUuid) {
        FuneralRequest request = requests.getByUuid(requestUuid);
        return RequestNoteMapper.toDtoList(notes.findAllByRequestIdOrderByPinnedDescCreatedAtDescUuidAsc(request.getId()));
    }

    @Transactional
    public RequestNoteResponseDto create(UUID requestUuid, UUID authorUuid, String text, boolean pinned) {
        FuneralRequest request = findEditableRequest(requestUuid);
        String normalizedText = normalizeText(text);
        if (authorUuid == null) {
            throw new BadCredentialsException("Требуется авторизация");
        }
        User author = users.findByUuid(authorUuid)
                .orElseThrow(() -> new BadCredentialsException("Недействительный пользователь"));
        RequestNote note = new RequestNote();
        note.setRequest(request);
        note.setAuthor(author);
        note.setText(normalizedText);
        note.setPinned(pinned);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        return RequestNoteMapper.toDto(notes.saveAndFlush(note));
    }

    @Transactional
    public RequestNoteResponseDto update(UUID requestUuid, UUID noteUuid, String text, boolean pinned) {
        FuneralRequest request = findEditableRequest(requestUuid);
        RequestNote note = findNote(noteUuid, request.getId());
        note.setText(normalizeText(text));
        note.setPinned(pinned);
        note.setUpdatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        return RequestNoteMapper.toDto(notes.saveAndFlush(note));
    }

    @Transactional
    public void delete(UUID requestUuid, UUID noteUuid) {
        FuneralRequest request = findEditableRequest(requestUuid);
        RequestNote note = findNote(noteUuid, request.getId());
        notes.delete(note);
        notes.flush();
    }

    private FuneralRequest findEditableRequest(UUID uuid) {
        FuneralRequest request = requests.getByUuid(uuid);
        if (request.isArchived()) {
            throw new BusinessException("Нельзя изменять заметки архивной заявки");
        }
        return request;
    }

    private RequestNote findNote(UUID uuid, Long requestId) {
        if (uuid == null) {
            throw new ValidationException("UUID заметки обязателен");
        }
        return notes.findByUuidAndRequestId(uuid, requestId)
                .orElseThrow(() -> new EntityNotFoundException("Заметка в указанной заявке не найдена"));
    }

    private String normalizeText(String text) {
        if (text == null || text.isBlank() || text.trim().isEmpty()) {
            throw new ValidationException("Текст заметки обязателен");
        }
        return text.trim();
    }
}
