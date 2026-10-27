package com.stockflow.config;

import com.stockflow.domain.AppUser;
import com.stockflow.domain.Role;
import com.stockflow.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Na primeira execução (banco sem usuários) cria o administrador inicial. */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminSeeder(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       @Value("${stockflow.admin.username}") String username,
                       @Value("${stockflow.admin.password}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        users.save(new AppUser(username, passwordEncoder.encode(password), Role.ADMIN));
        log.warn("Usuário administrador inicial '{}' criado. Troque a senha padrão (variável ADMIN_PASSWORD) em produção!",
                username);
    }
}
