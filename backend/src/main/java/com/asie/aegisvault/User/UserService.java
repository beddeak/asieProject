package com.asie.aegisvault.User;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final org.springframework.context.ApplicationEventPublisher events;

  @Transactional
  public User create(String nickname, String email, String password) {
    // Spring Security 로그인과 같은 규칙으로 아이디 앞뒤 공백을 제거합니다.
    nickname = nickname == null ? null : nickname.trim();
    if (nickname == null || nickname.isBlank()) {
      throw new IllegalArgumentException("아이디를 입력해주세요");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("비밀번호를 입력해주세요");
    }

    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("이미 사용중인 이메일입니다");
    }
    if (userRepository.existsByNickname(nickname)) {
      throw new IllegalArgumentException("이미 사용중인 닉네임입니다");
    }
    com.asie.aegisvault.account.PasswordPolicy.validate(password);
    String encodedPassword = passwordEncoder.encode(password);
    User user = new User(nickname, email, encodedPassword);

    userRepository.save(user);
    events.publishEvent(
        com.asie.aegisvault.audit.AuditEvent.of(
            user, "USER_REGISTERED", "USER", user.getId(), null, "회원 가입"));
    return user;
  }
}
