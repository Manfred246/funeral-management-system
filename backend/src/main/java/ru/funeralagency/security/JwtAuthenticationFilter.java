package ru.funeralagency.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.funeralagency.model.Role;
import ru.funeralagency.model.User;
import ru.funeralagency.repository.UserRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/refresh", "/api/auth/logout");
    private final JwtService jwtService;
    private final UserRepository users;
    private final SecurityErrorHandler errors;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users, SecurityErrorHandler errors) {
        this.jwtService = jwtService;
        this.users = users;
        this.errors = errors;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Refresh/logout подтверждают право самим refresh token, даже если access JWT уже истёк.
        return "POST".equals(request.getMethod()) && PUBLIC_AUTH_PATHS.contains(path(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                throw new BadCredentialsException("Некорректная авторизация");
            }
            UUID uuid = jwtService.parseUserUuid(header.substring(7).trim());
            User user = users.findByUuid(uuid).orElseThrow(() -> new BadCredentialsException("Недействительный токен"));
            if (!user.isEnabled()) {
                throw new BadCredentialsException("Недействительный токен");
            }
            List<GrantedAuthority> authorities = new ArrayList<>();
            for (Role role : user.getRoles()) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
            }
            AuthenticatedUser principal = new AuthenticatedUser(user.getUuid(), user.getLogin(), user.isPasswordChangeRequired());
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            String path = path(request);
            if (user.isPasswordChangeRequired() && !"/api/auth/me".equals(path)
                    && !"/api/auth/change-password".equals(path)) {
                errors.write(request, response, HttpStatus.FORBIDDEN, "Необходимо сменить временный пароль");
                return;
            }
        } catch (JwtException | IllegalArgumentException | AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            errors.write(request, response, HttpStatus.UNAUTHORIZED, "Требуется действительный access token");
            return;
        } catch (DataAccessException exception) {
            SecurityContextHolder.clearContext();
            errors.write(request, response, HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выполнить операцию с базой данных");
            return;
        }
        chain.doFilter(request, response);
    }

    private String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
