package com.asie.aegisvault.account;

import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.security.CurrentUser;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {
  private final CurrentUser actors;
  private final UserRepository users;
  private final PasswordRecoveryRepository recoveries;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;

  @Transactional
  public void change(String actor, String current, String next, String confirmation) {
    User user = actors.get(actor);
    user = users.lockById(user.getId()).orElseThrow();
    if (current == null || !passwords.matches(current, user.getPassword()))
      throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
    update(user, next, confirmation);
    events.publishEvent(
        AuditEvent.of(user, "PASSWORD_CHANGED", "USER", user.getId(), null, "본인 비밀번호 변경"));
  }

  @Transactional
  public void request(String nickname, String email) {
    User user = users.findByNickname(nickname == null ? "" : nickname.strip()).orElse(null);
    if (user == null
        || user.getAccountStatus() != AccountStatus.ACTIVE
        || !Objects.equals(user.getEmail(), email)) return;
    users.lockById(user.getId());
    if (recoveries.findByUserIdAndCompletedAtIsNull(user.getId()).isEmpty()) {
      recoveries.save(new PasswordRecovery(user.getId(), user.getNickname()));
      events.publishEvent(
          AuditEvent.of(
              user, "PASSWORD_RECOVERY_REQUESTED", "USER", user.getId(), null, "계정 복구 요청"));
    }
  }

  public Page<PasswordRecovery> requests(String actor, int page) {
    admin(actor);
    return PageQueries.fetch(
        page, 20, Sort.by("requestedAt", "id"), p -> recoveries.findByCompletedAtIsNull(p));
  }

  @Transactional
  public String issue(String actor, Long requestId, String verification) {
    User admin = admin(actor);
    PasswordRecovery request =
        recoveries
            .findById(requestId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    User target = users.lockById(request.getUserId()).orElseThrow();
    if (target.getAccountStatus() != AccountStatus.ACTIVE)
      throw new IllegalArgumentException("활성 계정만 복구할 수 있습니다.");
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    request.issue(
        hash(token), Instant.now().plus(Duration.ofMinutes(30)), admin.getNickname(), verification);
    events.publishEvent(
        AuditEvent.of(
            admin,
            "PASSWORD_RECOVERY_ISSUED",
            "USER",
            target.getId(),
            null,
            "본인 확인 후 일회용 복구 링크 발급"));
    return token;
  }

  @Transactional
  public void reset(String token, String next, String confirmation) {
    if (token == null || token.length() != 43) invalid();
    PasswordRecovery request =
        recoveries
            .findByTokenHash(hash(token))
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 복구 링크입니다."));
    User target = users.lockById(request.getUserId()).orElseThrow();
    // Another request may have consumed or rotated the token while this transaction waited for the
    // account lock.
    request =
        recoveries
            .findByTokenHash(hash(token))
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 복구 링크입니다."));
    if (!request.usable() || target.getAccountStatus() != AccountStatus.ACTIVE) invalid();
    update(target, next, confirmation);
    events.publishEvent(
        AuditEvent.of(
            target, "PASSWORD_RESET", "USER", target.getId(), null, "일회용 복구 링크로 비밀번호 재설정"));
  }

  private void update(User user, String next, String confirmation) {
    PasswordPolicy.validate(next);
    if (!Objects.equals(next, confirmation))
      throw new IllegalArgumentException("새 비밀번호 확인이 일치하지 않습니다.");
    if (passwords.matches(next, user.getPassword()))
      throw new IllegalArgumentException("현재와 다른 비밀번호를 입력해주세요.");
    user.changePassword(passwords.encode(next));
    recoveries.findByUserIdAndCompletedAtIsNull(user.getId()).forEach(PasswordRecovery::complete);
  }

  private User admin(String actor) {
    User user = actors.get(actor);
    if (!user.getPosition().isAdmin()) throw new AccessDeniedException("관리자만 계정을 복구할 수 있습니다.");
    return user;
  }

  private void invalid() {
    throw new IllegalArgumentException("유효하지 않거나 만료된 복구 링크입니다.");
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
