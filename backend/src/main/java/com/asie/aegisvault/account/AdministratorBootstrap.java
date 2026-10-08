package com.asie.aegisvault.account;

import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.security.SecurityClassification;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdministratorBootstrap {
  private final UserRepository users;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;

  @Transactional
  public void initialize(String nickname, String email, String password) {
    if (nickname == null || nickname.isBlank() || nickname.length() > 100)
      throw new IllegalArgumentException("관리자 아이디를 100자 이내로 지정해주세요.");
    PasswordPolicy.validate(password);
    if (!users.findByPosition(Position.ADMIN).isEmpty())
      throw new IllegalStateException("이미 관리자가 있습니다. 기존 관리자 계정으로 사용자 관리 화면을 이용해주세요.");
    User account = users.findByNickname(nickname.strip()).orElse(null);
    if (account == null) {
      if (email == null
          || email.isBlank()
          || email.length() > 255
          || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        throw new IllegalArgumentException("새 관리자 계정의 이메일을 지정해주세요.");
      if (users.existsByEmail(email)) throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
      account = new User(nickname.strip(), email, passwords.encode(password));
    } else if (account.getAccountStatus() != AccountStatus.ACTIVE
        || !passwords.matches(password, account.getPassword()))
      throw new IllegalArgumentException("기존 계정의 비밀번호 또는 상태를 확인해주세요.");
    account.assign(Position.ADMIN, account.getDepartment());
    account.changeClearance(SecurityClassification.RESTRICTED);
    users.saveAndFlush(account);
    events.publishEvent(
        AuditEvent.of(
            account,
            "FIRST_ADMINISTRATOR_CREATED",
            "USER",
            account.getId(),
            null,
            "로컬 초기 설정으로 최초 관리자 등록"));
  }
}
