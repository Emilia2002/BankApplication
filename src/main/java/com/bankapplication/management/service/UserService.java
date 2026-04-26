package com.bankapplication.management.service;
import com.bankapplication.management.dto.LoginRequest;
import com.bankapplication.management.dto.RegisterRequest;
import com.bankapplication.management.dto.UserResponse;
import com.bankapplication.management.entity.Users;
import com.bankapplication.management.repository.JDBCUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

    private final JDBCUserRepository UserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(JDBCUserRepository UserRepository, PasswordEncoder passwordEncoder) {
        this.UserRepository = UserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public void register(RegisterRequest request) {
        if (UserRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already in use");
        }

        Users newUser = new Users();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setFullName(request.getFullName());

        UserRepository.save(newUser);
    }

    public UserResponse login(LoginRequest request) {
        Users user = UserRepository.findByEmail(request.getEmail());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName()
        );
    }

    public Users findById(Long userId) {
        return UserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
