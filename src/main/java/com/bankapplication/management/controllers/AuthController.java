package com.bankapplication.management.controllers;

import com.bankapplication.management.dto.CreateBankAccRequest;
import com.bankapplication.management.dto.LoginRequest;
import com.bankapplication.management.dto.RegisterRequest;
import com.bankapplication.management.dto.UserResponse;
import com.bankapplication.management.entity.Users;
import com.bankapplication.management.repository.JDBCUserRepository;
import com.bankapplication.management.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/auth")
@Tag(name = "User Authentication and registration", description = "Operations related to user authentication and registration")
public class AuthController {

    @Autowired
    private final UserService userService;

    @Autowired
    private JDBCUserRepository userRepository;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("register")
    public ResponseEntity<?> registerUser(@RequestBody Users newUser) {
        userRepository.save(newUser);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest request) {
        UserResponse user = userService.login(request);
        return ResponseEntity.ok(user);
    }
}
