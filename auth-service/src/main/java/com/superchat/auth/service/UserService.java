package com.superchat.auth.service;

import com.superchat.auth.domain.User;
import com.superchat.auth.repo.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    public User registerUser(String phone, String password, String alias) {
        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        User user = new User();
        user.setPhone(phone);
        user.setAlias(alias);
        user.setPasswordHash(passwordEncoder.encode(password));
        
        return userRepository.save(user);
    }

    public Optional<User> findByPhone(String phone) {
        return userRepository.findByPhone(phone);
    }

    public boolean validatePassword(String rawPassword, String hash) {
        return passwordEncoder.matches(rawPassword, hash);
    }
}
