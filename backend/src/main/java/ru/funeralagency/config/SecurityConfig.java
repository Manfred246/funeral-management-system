package ru.funeralagency.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import ru.funeralagency.repository.UserRepository;
import ru.funeralagency.security.JwtAuthenticationFilter;
import ru.funeralagency.security.JwtService;
import ru.funeralagency.security.SecurityErrorHandler;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, UserRepository users,
                                                   SecurityErrorHandler errors) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/change-password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/ceremony-types", "/api/ceremony-types/*")
                                .hasAnyRole("VIEWER", "MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/ceremony-types", "/api/ceremony-types/*/enable",
                                "/api/ceremony-types/*/disable").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/ceremony-types/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/requests/*/permanent").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/requests", "/api/requests/**", "/api/statistics",
                                "/api/clients", "/api/clients/**").hasAnyRole("VIEWER", "MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/requests", "/api/requests/*/restore", "/api/clients")
                                .hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/requests/*", "/api/clients/*").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/requests/*", "/api/clients/*").hasAnyRole("MANAGER", "ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwt, users, errors), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
