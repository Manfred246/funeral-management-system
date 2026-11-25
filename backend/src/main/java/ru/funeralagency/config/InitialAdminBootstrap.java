package ru.funeralagency.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import ru.funeralagency.service.AuthService;

@Component
public class InitialAdminBootstrap implements ApplicationRunner {
    private final AuthService authService;
    private final String login;
    private final String password;
    private final String name;

    public InitialAdminBootstrap(AuthService authService, @Value("${app.admin.login}") String login,
                                 @Value("${app.admin.password}") String password, @Value("${app.admin.name}") String name) {
        this.authService = authService;
        this.login = login;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        authService.createInitialAdmin(login, password, name);
    }
}
