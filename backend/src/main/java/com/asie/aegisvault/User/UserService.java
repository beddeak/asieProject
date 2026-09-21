package com.asie.aegisvault.User;


import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.config.SecurityConfig;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor 
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User create(String nickname,String email,String password) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("이미 사용중인 이메일입니다");
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new IllegalArgumentException("이미 사용중인 닉네임입니다");
        }
        String encodedPassword = passwordEncoder.encode(password);
        User user = new User(nickname, email, encodedPassword);

        return userRepository.save(user);
    }
}
