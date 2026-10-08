package com.asie.aegisvault.notice;

import com.asie.aegisvault.User.User;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.*;
import com.asie.aegisvault.security.CurrentUser;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {
  private final CurrentUser actors;
  private final DepartmentNoticeRepository notices;
  private final ApplicationEventPublisher events;

  public Page<NoticeSummary> list(String actor, String keyword, int page) {
    actors.get(actor);
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        p -> notices.global(DepartmentNotice.Scope.GLOBAL, SearchText.contains(keyword), p));
  }

  public DepartmentNotice detail(String actor, Long id) {
    actors.get(actor);
    return notice(id);
  }

  @Transactional
  public Long create(String actor, String title, String content) {
    User user = admin(actor);
    DepartmentNotice notice = notices.save(DepartmentNotice.global(user, title, content));
    event(user, notice, "ANNOUNCEMENT_CREATED");
    return notice.getId();
  }

  @Transactional
  public void update(String actor, Long id, Long revision, String title, String content) {
    User user = admin(actor);
    DepartmentNotice notice = notice(id);
    revision(notice, revision);
    notice.updateText(title, content);
    event(user, notice, "ANNOUNCEMENT_UPDATED");
  }

  @Transactional
  public void delete(String actor, Long id, Long revision) {
    User user = admin(actor);
    DepartmentNotice notice = notice(id);
    revision(notice, revision);
    notices.delete(notice);
    event(user, notice, "ANNOUNCEMENT_DELETED");
  }

  private void revision(DepartmentNotice notice, Long revision) {
    if (!Objects.equals(revision, notice.getVersion()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "공지 내용이 변경되었습니다. 새로고침해주세요.");
  }

  private DepartmentNotice notice(Long id) {
    return notices
        .findByIdAndScope(id, DepartmentNotice.Scope.GLOBAL)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private User admin(String actor) {
    User user = actors.get(actor);
    if (!user.getPosition().isAdmin()) throw new AccessDeniedException("전체 공지는 관리자만 작성할 수 있습니다.");
    return user;
  }

  private void event(User user, DepartmentNotice notice, String action) {
    events.publishEvent(
        AuditEvent.of(
            user, action, "ANNOUNCEMENT", notice.getId(), null, "전체 공지 #" + notice.getId()));
  }
}
