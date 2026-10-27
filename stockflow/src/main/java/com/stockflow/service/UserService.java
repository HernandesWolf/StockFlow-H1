package com.stockflow.service;

import com.stockflow.domain.AppUser;
import com.stockflow.exception.ConflictException;
import com.stockflow.repository.AppUserRepository;
import com.stockflow.web.dto.CreateUserRequest;
import com.stockflow.web.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new ConflictException("Já existe um usuário com o nome '" + username + "'.");
        }
        AppUser saved = users.save(new AppUser(username, passwordEncoder.encode(request.password()), request.role()));
        return UserResponse.from(saved);
    }

    public List<UserResponse> list() {
        return users.findAll().stream().map(UserResponse::from).toList();
    }
}
