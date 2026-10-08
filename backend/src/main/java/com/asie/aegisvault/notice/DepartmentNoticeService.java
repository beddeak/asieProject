package com.asie.aegisvault.notice;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.security.UserAccessPolicy;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentNoticeService {
  private static final int PAGE_SIZE = 20;
  private final DepartmentRepository departments;
  private final DepartmentNoticeRepository notices;
  private final UserRepository users;
  private final UserAccessPolicy accessPolicy;
  private final org.springframework.context.ApplicationEventPublisher events;

  public DepartmentWorkspace workspace(String actor, Long departmentId, int page) {
    User user = actor(actor);
    boolean admin = accessPolicy.isAdmin(user);
    List<DepartmentWorkspace.Tab> tabs =
        admin
            ? departments.findAll(Sort.by("name", "id")).stream().map(this::tab).toList()
            : user.getDepartment() == null ? List.of() : List.of(tab(user.getDepartment()));
    Long selectedId = departmentId;
    if (selectedId == null && !tabs.isEmpty()) {
      selectedId =
          user.getDepartment() != null ? user.getDepartment().getId() : tabs.getFirst().id();
    }
    var sort = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    if (selectedId == null) {
      return new DepartmentWorkspace(
          tabs, null, "", Page.empty(PageRequest.of(0, PAGE_SIZE, sort)), false, admin);
    }
    Department department = readableDepartment(user, selectedId);
    Page<NoticeSummary> noticePage =
        PageQueries.fetch(
            page, PAGE_SIZE, sort, pageable -> notices.findSummaries(department.getId(), pageable));
    return new DepartmentWorkspace(
        tabs,
        tab(department),
        department.getDescription(),
        noticePage,
        canManage(user) && !department.isClosed(),
        admin);
  }

  public NoticeDetail detail(String actor, Long departmentId, Long noticeId) {
    User user = actor(actor);
    readableDepartment(user, departmentId);
    DepartmentNotice notice = notice(departmentId, noticeId);
    return new NoticeDetail(
        notice.getId(),
        departmentId,
        notice.getDepartment().getName(),
        notice.getTitle(),
        notice.getContent(),
        notice.getAuthor().getNickname(),
        notice.getCreatedAt(),
        notice.getUpdatedAt(),
        notice.getVersion(),
        canManage(user) && !notice.getDepartment().isClosed());
  }

  public void requireManager(String actor, Long departmentId) {
    User user = actor(actor);
    requireOpen(readableDepartment(user, departmentId));
    requireManager(user);
  }

  @Transactional
  public Long create(String actor, Long departmentId, NoticeForm form) {
    User user = actor(actor);
    Department department = writableDepartment(user, departmentId);
    DepartmentNotice notice =
        notices.saveAndFlush(
            new DepartmentNotice(department, user, form.getTitle(), form.getContent()));
    event(user, notice.getId(), "DEPARTMENT_NOTICE_CREATED");
    return notice.getId();
  }

  @Transactional
  public void update(String actor, Long departmentId, Long noticeId, NoticeForm form) {
    User user = actor(actor);
    writableDepartment(user, departmentId);
    DepartmentNotice notice = notice(departmentId, noticeId);
    requireCurrentVersion(notice, form.getVersion());
    notice.updateText(form.getTitle(), form.getContent());
    event(user, noticeId, "DEPARTMENT_NOTICE_UPDATED");
    notices.flush();
  }

  @Transactional
  public void delete(String actor, Long departmentId, Long noticeId, Long version) {
    User user = actor(actor);
    writableDepartment(user, departmentId);
    DepartmentNotice notice = notice(departmentId, noticeId);
    requireCurrentVersion(notice, version);
    notices.delete(notice);
    event(user, noticeId, "DEPARTMENT_NOTICE_DELETED");
    notices.flush();
  }

  private void requireOpen(Department department) {
    if (department.isClosed()) throw new IllegalArgumentException("폐쇄된 부서의 공지는 변경할 수 없습니다.");
  }

  private Department writableDepartment(User user, Long departmentId) {
    // Notice changes and department transfers must serialize on the same department lock.
    departments.lockDepartments(List.of(departmentId));
    Department department = readableDepartment(user, departmentId);
    requireOpen(department);
    requireManager(user);
    return department;
  }

  private void event(User actor, Long id, String action) {
    events.publishEvent(
        com.asie.aegisvault.audit.AuditEvent.of(
            actor, action, "DEPARTMENT_NOTICE", id, null, "부서 공지 #" + id));
  }

  private User actor(String nickname) {
    if (nickname == null || nickname.isBlank()) {
      throw new AccessDeniedException("사용자를 확인할 수 없습니다.");
    }
    User user =
        users
            .findByNickname(nickname)
            .orElseThrow(() -> new AccessDeniedException("사용자를 확인할 수 없습니다."));
    accessPolicy.requireActive(user);
    if (user.getPosition() == null) {
      throw new AccessDeniedException("사용자 직급을 확인할 수 없습니다.");
    }
    return user;
  }

  private Department readableDepartment(User user, Long departmentId) {
    if (!accessPolicy.isAdmin(user)
        && (user.getDepartment() == null
            || !Objects.equals(user.getDepartment().getId(), departmentId))) {
      throw new AccessDeniedException("소속 부서의 공지만 이용할 수 있습니다.");
    }
    return departments
        .findById(departmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "부서를 찾을 수 없습니다."));
  }

  private DepartmentNotice notice(Long departmentId, Long noticeId) {
    return notices
        .findByIdAndDepartmentId(noticeId, departmentId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "공지를 찾을 수 없습니다."));
  }

  private boolean canManage(User user) {
    return user.getPosition().isAtLeast(Position.MANAGER);
  }

  private void requireManager(User user) {
    if (!canManage(user)) {
      throw new AccessDeniedException("관리자 또는 소속 부서의 과장 이상만 공지를 관리할 수 있습니다.");
    }
  }

  private void requireCurrentVersion(DepartmentNotice notice, Long version) {
    if (version == null || !version.equals(notice.getVersion())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "공지 내용이 변경되었습니다. 새로고침한 뒤 다시 시도해주세요.");
    }
  }

  private DepartmentWorkspace.Tab tab(Department department) {
    return new DepartmentWorkspace.Tab(department.getId(), department.getName());
  }
}
