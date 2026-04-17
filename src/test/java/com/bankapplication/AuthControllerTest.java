package com.bankapplication;

import com.bankapplication.management.controllers.AuthController;
import com.bankapplication.management.dto.LoginRequest;
import com.bankapplication.management.dto.RegisterRequest;
import com.bankapplication.management.dto.UserResponse;
import com.bankapplication.management.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

// tests for registration and login functionalities
class AuthControllerTest {

    private UserService userService;
    private AuthController authController;

    @BeforeEach
    void setUp() {
        userService = Mockito.mock(UserService.class);
        authController = new AuthController(userService);
    }

    @Test
    void registerUser_returnsSuccessMessage() {
        RegisterRequest request = new RegisterRequest(); // create new register request
        // set credentials for the new user
        request.setEmail("test@example.com");
        request.setPassword("password");
        request.setFullName("Test User");

        Mockito.doNothing().when(userService).register(any(RegisterRequest.class));
        ResponseEntity<?> response = authController.registerUser(request); // try to register user
        assertEquals(200, response.getStatusCode().value());
        assertEquals("User registered successfully", response.getBody());
    }

    @Test
    void loginUser_returnsUserResponse() {
        LoginRequest request = new LoginRequest(); // create new login request
        request.setEmail("test@example.com");
        request.setPassword("password");

        UserResponse expectedResponse = new UserResponse(1L, "test@example.com", "Test User");
        Mockito.when(userService.login(any(LoginRequest.class))).thenReturn(expectedResponse);
        ResponseEntity<?> response = authController.loginUser(request);
        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody() instanceof UserResponse);
        UserResponse actual = (UserResponse) response.getBody();
        assertEquals(1L, actual.getId());
        assertEquals("test@example.com", actual.getEmail());
        assertEquals("Test User", actual.getFullName());
    }
}
