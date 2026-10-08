package com.asie.aegisvault.security;

import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUser {
  private final UserRepository users;
  private final UserAccessPolicy policy;

  public User get(String nickname) {
    if (nickname == null || nickname.isBlank()) throw new AccessDeniedException("로그인이 필요합니다.");
    User user =
        users
            .findByNickname(nickname)
            .orElseThrow(() -> new AccessDeniedException("사용자를 확인할 수 없습니다."));
    policy.requireActive(user);
    if (user.getPosition() == null) throw new AccessDeniedException("직급을 확인할 수 없습니다.");
    return user;
  }
}
