package com.bankapplication.management.controllers;

import com.bankapplication.management.dto.LoginRequest;
import com.bankapplication.management.dto.UserResponse;
import com.bankapplication.management.entity.Users;
import com.bankapplication.management.repository.JDBCUserRepository;
import com.bankapplication.management.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder; // IMPORT NOU
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/auth")
@Tag(name = "User Authentication and registration")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JDBCUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // IMPORTĂM ENCODER-UL

    @PostMapping("register")
    public ResponseEntity<?> registerUser(@RequestBody Users newUser) {

        if (newUser.getPassword() != null && !newUser.getPassword().startsWith("$2a$")) {
            newUser.setPassword(passwordEncoder.encode(newUser.getPassword()));
        }

        userRepository.save(newUser);

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        UserResponse user = userService.login(request);
        return ResponseEntity.ok(user);
    }
}