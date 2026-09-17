package com.asie.aegisvault.User;



import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.asie.aegisvault.Department.Department;

import lombok.NoArgsConstructor;

@NoArgsConstructor
@Service
public class UserService {
    private Long id;
    private String nickname;
    private String email;
    private String password;
    private Position position;
    private AccountStatus accountStatus;
    private Department department;
}
