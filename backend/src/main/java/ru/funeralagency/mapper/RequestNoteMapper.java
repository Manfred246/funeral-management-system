package ru.funeralagency.mapper;

import ru.funeralagency.dto.RequestNoteResponseDto;
import ru.funeralagency.model.RequestNote;

import java.util.ArrayList;
import java.util.List;

public final class RequestNoteMapper {
    private RequestNoteMapper() {
    }

    public static RequestNoteResponseDto toDto(RequestNote note) {
        return new RequestNoteResponseDto(note.getUuid(), note.getAuthor().getUuid(), note.getAuthor().getName(),
                note.getText(), note.isPinned(), note.getCreatedAt(), note.getUpdatedAt());
    }

    public static List<RequestNoteResponseDto> toDtoList(List<RequestNote> notes) {
        List<RequestNoteResponseDto> result = new ArrayList<>();
        for (RequestNote note : notes) {
            result.add(toDto(note));
        }
        return result;
    }
}
