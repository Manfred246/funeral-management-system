package ru.funeralagency.dto;

public record AuthTokensDto(String accessToken, String refreshToken, String tokenType,
                            long expiresIn, AuthUserDto user) {
}
