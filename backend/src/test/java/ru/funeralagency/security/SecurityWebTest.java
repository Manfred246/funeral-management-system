package ru.funeralagency.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.funeralagency.config.SecurityConfig;
import ru.funeralagency.controller.AuthController;
import ru.funeralagency.controller.CeremonyTypeController;
import ru.funeralagency.controller.TagController;
import ru.funeralagency.model.Tag;
import ru.funeralagency.service.TagService;
import ru.funeralagency.dto.AuthTokensDto;
import ru.funeralagency.dto.AuthUserDto;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.model.CeremonyType;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.service.AuthService;
import ru.funeralagency.service.CeremonyTypeService;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, JwtService.class, SecurityErrorHandler.class,
        CeremonyTypeController.class, TagController.class, SecurityWebTest.BusinessController.class})
class SecurityWebTest {
    @Autowired private MockMvc mvc;
    @Autowired private JwtService jwt;
    @Value("${app.jwt.secret}") private String secret;
    @MockitoBean private UserRepository users;
    @MockitoBean private AuthService auth;
    @MockitoBean private CeremonyTypeService ceremonyTypes;
    @MockitoBean private TagService tags;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setLogin("user");
        user.setName("Пользователь");
    }

    @Test
    void missingTokenReturnsJson401WithoutSession() throws Exception {
        mvc.perform(get("/api/requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.path").value("/api/requests"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @ParameterizedTest
    @CsvSource({
            "VIEWER,GET,/api/requests,200", "VIEWER,GET,/api/statistics,200",
            "VIEWER,GET,/api/clients,200", "VIEWER,POST,/api/requests,403",
            "VIEWER,PUT,/api/requests/example,403", "VIEWER,DELETE,/api/requests/example,403",
            "VIEWER,POST,/api/requests/example/restore,403", "VIEWER,DELETE,/api/requests/example/permanent,403",
            "MANAGER,GET,/api/requests,200", "MANAGER,POST,/api/requests,200",
            "MANAGER,PUT,/api/requests/example,200", "MANAGER,DELETE,/api/requests/example,200",
            "MANAGER,POST,/api/requests/example/restore,200", "MANAGER,DELETE,/api/requests/example/permanent,403",
            "MANAGER,POST,/api/clients,200", "ADMIN,GET,/api/requests,200",
            "ADMIN,POST,/api/requests,200", "ADMIN,PUT,/api/requests/example,200",
            "ADMIN,DELETE,/api/requests/example,200", "ADMIN,POST,/api/requests/example/restore,200",
            "ADMIN,DELETE,/api/requests/example/permanent,200"})
    void permissionsAreEnforcedByRealSecurityChain(Role role, String method, String path, int expected) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token(role)))
                .andExpect(status().is(expected));
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,GET,/api/ceremony-types,200", "MANAGER,GET,/api/ceremony-types,200",
            "ADMIN,GET,/api/ceremony-types,200", "VIEWER,GET,/api/ceremony-types/1,200",
            "MANAGER,GET,/api/ceremony-types/1,200", "ADMIN,GET,/api/ceremony-types/1,200",
            "VIEWER,POST,/api/ceremony-types,403", "MANAGER,POST,/api/ceremony-types,403",
            "ADMIN,POST,/api/ceremony-types,201", "VIEWER,PUT,/api/ceremony-types/1,403",
            "MANAGER,PUT,/api/ceremony-types/1,403", "ADMIN,PUT,/api/ceremony-types/1,200",
            "VIEWER,POST,/api/ceremony-types/1/enable,403", "MANAGER,POST,/api/ceremony-types/1/enable,403",
            "ADMIN,POST,/api/ceremony-types/1/enable,200", "VIEWER,POST,/api/ceremony-types/1/disable,403",
            "MANAGER,POST,/api/ceremony-types/1/disable,403", "ADMIN,POST,/api/ceremony-types/1/disable,200"})
    void ceremonyTypePermissionsUseExistingSecurityChain(Role role, String method, String path, int expected)
            throws Exception {
        CeremonyType type = new CeremonyType(1L, "BURIAL", "Захоронение", true);
        when(ceremonyTypes.getAll()).thenReturn(List.of(type));
        when(ceremonyTypes.getById(1L)).thenReturn(type);
        when(ceremonyTypes.create("BURIAL", "Захоронение")).thenReturn(type);
        when(ceremonyTypes.update(1L, "Захоронение")).thenReturn(type);
        when(ceremonyTypes.enable(1L)).thenReturn(type);
        when(ceremonyTypes.disable(1L)).thenReturn(type);
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BURIAL\",\"name\":\"Захоронение\"}"))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verifyNoInteractions(ceremonyTypes);
        }
    }

    @ParameterizedTest
    @CsvSource({"VIEWER,GET,/api/tags,200", "MANAGER,GET,/api/tags,200", "ADMIN,GET,/api/tags,200",
            "VIEWER,GET,/api/tags/3,200", "MANAGER,GET,/api/tags/3,200", "ADMIN,GET,/api/tags/3,200",
            "VIEWER,POST,/api/tags,403", "MANAGER,POST,/api/tags,201", "ADMIN,POST,/api/tags,201",
            "VIEWER,GET,/api/requests/example/tags,200", "MANAGER,GET,/api/requests/example/tags,200",
            "ADMIN,GET,/api/requests/example/tags,200", "VIEWER,POST,/api/requests/example/tags/3,403",
            "VIEWER,DELETE,/api/requests/example/tags/3,403", "MANAGER,POST,/api/requests/example/tags/3,200",
            "MANAGER,DELETE,/api/requests/example/tags/3,200", "ADMIN,POST,/api/requests/example/tags/3,200",
            "ADMIN,DELETE,/api/requests/example/tags/3,200"})
    void tagPermissionsUseExistingSecurityChain(Role role, String method, String path, int expected) throws Exception {
        Tag tag = new Tag(3L, "VIP");
        when(tags.getAll()).thenReturn(List.of(tag));
        when(tags.getById(3L)).thenReturn(tag);
        when(tags.create("VIP")).thenReturn(tag);
        mvc.perform(request(HttpMethod.valueOf(method), path).header("Authorization", "Bearer " + token(role))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"VIP\"}"))
                .andExpect(status().is(expected));
        if (expected == 403) {
            verifyNoInteractions(tags);
        }
    }

    @Test
    void tagEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/tags")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"VIP\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/requests/example/tags/3")).andExpect(status().isUnauthorized());
        verifyNoInteractions(tags);
    }

    @Test
    void jwtErrorsAllReturnSafe401() throws Exception {
        String expired = Jwts.builder().subject(user.getUuid().toString())
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        String invalidSignature = new JwtService("different-test-key-only-not-production-1234567890")
                .createAccessToken(user);
        String unsigned = Jwts.builder().subject(user.getUuid().toString()).compact();
        String missingExpiry = Jwts.builder().subject(user.getUuid().toString())
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        for (String value : List.of(expired, invalidSignature, unsigned, missingExpiry, "malformed", "")) {
            mvc.perform(get("/api/requests").header("Authorization", "Bearer " + value))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Требуется действительный access token"))
                    .andExpect(jsonPath("$.trace").doesNotExist());
        }
        verifyNoInteractions(users);
    }

    @Test
    void disabledUserIsRejectedDespiteValidJwt() throws Exception {
        String token = token(Role.ADMIN);
        user.setEnabled(false);
        mvc.perform(get("/api/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void currentDatabaseRolesOverrideRolesInAnOlderJwt() throws Exception {
        String token = token(Role.ADMIN);
        user.setRoles(Set.of(Role.VIEWER));
        mvc.perform(delete("/api/requests/example/permanent").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void temporaryPasswordAllowsMeAndChangeButBlocksBusinessApi() throws Exception {
        user.setPasswordChangeRequired(true);
        String token = token(Role.ADMIN);
        when(auth.me(user.getUuid())).thenReturn(userDto());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isNoContent());
        verify(auth).changePassword(user.getUuid(), "old-password", "new-password");
        mvc.perform(get("/api/requests").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Необходимо сменить временный пароль"));
    }

    @Test
    void credentialsErrorIs401AndDoesNotRevealUserExistence() throws Exception {
        when(auth.login("unknown", "wrong-password")).thenThrow(new BadCredentialsException("internal details"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"unknown\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверные учётные данные или недействительный токен"));
    }

    @Test
    void rejectedRefreshAndWrongCurrentPasswordReturn401() throws Exception {
        when(auth.refresh("revoked")).thenThrow(new BadCredentialsException("internal"));
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"revoked\"}"))
                .andExpect(status().isUnauthorized());
        String token = token(Role.VIEWER);
        doThrow(new BadCredentialsException("internal")).when(auth)
                .changePassword(user.getUuid(), "wrong-password", "new-password");
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Неверные учётные данные или недействительный токен"));
    }

    @Test
    void weakNewPasswordIsRejectedBeforeAuthService() throws Exception {
        String token = token(Role.VIEWER);
        mvc.perform(post("/api/auth/change-password").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old-password\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }

    @Test
    void refreshAndLogoutUseRefreshTokenWithoutAccessJwt() throws Exception {
        when(auth.refresh("opaque")).thenReturn(new AuthTokensDto("access", "opaque", "Bearer", 900, userDto()));
        mvc.perform(post("/api/auth/refresh").header("Authorization", "Bearer expired-or-malformed")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"refreshToken\":\"opaque\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").value("opaque"));
        mvc.perform(post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"opaque\"}"))
                .andExpect(status().isNoContent());
        verify(auth).logout("opaque");
        verifyNoInteractions(users);
    }

    private String token(Role role) {
        user.setRoles(Set.of(role));
        when(users.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        return jwt.createAccessToken(user);
    }

    private AuthUserDto userDto() {
        return new AuthUserDto(user.getUuid(), user.getLogin(), user.getName(), user.getRoles(),
                user.isEnabled(), user.isPasswordChangeRequired());
    }

    // Только HTTP-заглушки для проверки URL permissions; бизнес-правила проверяются прежними тестами.
    @RestController
    static class BusinessController {
        @GetMapping({"/api/requests", "/api/statistics", "/api/clients", "/api/requests/{uuid}/tags"})
        public String read() { return "ok"; }

        @PostMapping({"/api/requests", "/api/requests/{uuid}/restore", "/api/clients", "/api/requests/{uuid}/tags/{tagId}"})
        public String create() { return "ok"; }

        @PutMapping("/api/requests/{uuid}")
        public String update() { return "ok"; }

        @DeleteMapping({"/api/requests/{uuid}", "/api/requests/{uuid}/permanent", "/api/requests/{uuid}/tags/{tagId}"})
        public String delete() { return "ok"; }
    }
}
