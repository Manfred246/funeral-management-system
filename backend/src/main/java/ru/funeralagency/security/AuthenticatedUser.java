package ru.funeralagency.security;

import java.util.UUID;

public record AuthenticatedUser(UUID uuid, String login, boolean passwordChangeRequired) {
}
