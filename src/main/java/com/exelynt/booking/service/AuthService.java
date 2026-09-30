package com.exelynt.booking.service;

import com.exelynt.booking.dto.AuthRequest;
import com.exelynt.booking.dto.AuthResponse;
import com.exelynt.booking.dto.RegisterRequest;
import com.exelynt.booking.entity.Role;
import com.exelynt.booking.entity.User;
import com.exelynt.booking.exception.BadRequestException;
import com.exelynt.booking.repository.UserRepository;
import com.exelynt.booking.security.JwtTokenProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(AuthenticationManager authenticationManager,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("User not found"));

        return new AuthResponse(jwt, user.getUsername(), user.getRole().name(), tokenProvider.getJwtExpirationInMs());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered");
        }

        // Public registration is strictly restricted to ROLE_USER to prevent privilege escalation
        User user = new User(
                request.getUsername(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                Role.ROLE_USER
        );

        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole().name());
        return new AuthResponse(jwt, user.getUsername(), user.getRole().name(), tokenProvider.getJwtExpirationInMs());
    }

    @Transactional
    public AuthResponse createPrivilegedUser(String username, String email, String password, Role role) {
        if (userRepository.existsByUsername(username)) {
            throw new BadRequestException("Username is already taken");
        }

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered");
        }

        User user = new User(
                username,
                email,
                passwordEncoder.encode(password),
                role != null ? role : Role.ROLE_USER
        );

        userRepository.save(user);

        String jwt = tokenProvider.generateToken(user.getUsername(), user.getId(), user.getRole().name());
        return new AuthResponse(jwt, user.getUsername(), user.getRole().name(), tokenProvider.getJwtExpirationInMs());
    }
}
