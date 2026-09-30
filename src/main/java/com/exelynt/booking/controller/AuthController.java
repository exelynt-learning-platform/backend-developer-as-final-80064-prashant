package com.exelynt.booking.controller;

import com.exelynt.booking.dto.AuthRequest;
import com.exelynt.booking.dto.AuthResponse;
import com.exelynt.booking.dto.RegisterRequest;
import com.exelynt.booking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Authentication", description = "Endpoints for user login and registration")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping({"/auth/login", "/api/auth/login"})
    @Operation(summary = "Authenticate user and generate JWT token", description = "Validates user credentials and returns a signed JWT token.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/auth/register", "/api/auth/register"})
    @Operation(summary = "Register a new user", description = "Creates a new user account with specified role (default ROLE_USER).")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
