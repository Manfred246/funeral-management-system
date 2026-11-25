package ru.funeralagency.controller;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.dto.AuthTokensDto;
import ru.funeralagency.dto.AuthUserDto;
import ru.funeralagency.dto.ChangePasswordDto;
import ru.funeralagency.dto.LoginDto;
import ru.funeralagency.dto.RefreshTokenDto;
import ru.funeralagency.security.AuthenticatedUser;
import ru.funeralagency.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    public AuthTokensDto login(@Valid @RequestBody LoginDto dto) {
        return authService.login(dto.login(), dto.password());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    public AuthTokensDto refresh(@Valid @RequestBody RefreshTokenDto dto) {
        return authService.refresh(dto.refreshToken());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @ApiResponse(responseCode = "204", description = "Операция выполнена", content = @Content)
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenDto dto) {
        authService.logout(dto.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AuthUserDto me(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user) {
        return authService.me(user.uuid());
    }

    @PostMapping("/change-password")
    @ApiResponse(responseCode = "204", description = "Операция выполнена", content = @Content)
    public ResponseEntity<Void> changePassword(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
                                              @Valid @RequestBody ChangePasswordDto dto) {
        authService.changePassword(user.uuid(), dto.currentPassword(), dto.newPassword());
        return ResponseEntity.noContent().build();
    }
}
