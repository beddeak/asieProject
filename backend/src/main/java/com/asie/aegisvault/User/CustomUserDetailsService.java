package com.asie.aegisvault.User;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByNickname(username).orElseThrow(() -> new UsernameNotFoundException("유저 이름을 찾을 수 가 없습니다"));
        return org.springframework.security.core.userdetails.User.withUsername(user.getNickname()).password(user.getPassword()).roles(user.getPosition().name()).disabled(user.getAccountStatus() == AccountStatus.BAN).accountLocked(user.getAccountStatus() == AccountStatus.LOCKED).build();
    }
}   
