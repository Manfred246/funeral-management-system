package ru.funeralagency.mapper;

import ru.funeralagency.dto.TagDto;
import ru.funeralagency.model.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class TagMapper {
    private TagMapper() {
    }

    public static TagDto toDto(Tag tag) {
        return new TagDto(tag.getId(), tag.getName());
    }

    public static List<TagDto> toDtoList(Collection<Tag> tags) {
        List<TagDto> result = new ArrayList<>();
        for (Tag tag : tags) {
            result.add(toDto(tag));
        }
        return result;
    }
}
