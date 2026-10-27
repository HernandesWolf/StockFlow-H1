package com.stockflow.service;

import com.stockflow.domain.AppUser;
import com.stockflow.repository.AppUserRepository;
import com.stockflow.security.JwtService;
import com.stockflow.web.dto.LoginRequest;
import com.stockflow.web.dto.TokenResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, AppUserRepository users, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
    }

    /** Valida usuário e senha (BCrypt). Se estiverem errados, o Spring lança AuthenticationException (-> 401). */
    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = users.findByUsername(request.username()).orElseThrow();
        String token = jwtService.generateToken(user.getUsername(), user.getRole());
        return new TokenResponse(token, "Bearer", jwtService.getExpiration().toSeconds(), user.getRole());
    }
}
