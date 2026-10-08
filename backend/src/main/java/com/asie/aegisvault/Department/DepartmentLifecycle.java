package com.asie.aegisvault.Department;

import com.asie.aegisvault.Document.DocumentRepository;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.notice.DepartmentNoticeRepository;
import com.asie.aegisvault.project.ProjectRepository;
import com.asie.aegisvault.security.CurrentUser;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentLifecycle {
  private final CurrentUser actors;
  private final DepartmentRepository departments;
  private final ProjectRepository projects;
  private final DocumentRepository documents;
  private final UserRepository users;
  private final DepartmentNoticeRepository notices;
  private final ApplicationEventPublisher events;

  @Transactional
  public void rename(String actor, Long id, Long revision, String name, String description) {
    User user = admin(actor);
    departments.lockDepartments(List.of(id));
    Department department = department(id);
    check(department, revision);
    if (name == null || name.isBlank()) throw new IllegalArgumentException("부서 이름을 입력해주세요.");
    if (!department.getName().equals(name.strip()) && departments.existsByName(name.strip()))
      throw new IllegalArgumentException("이미 사용 중인 부서 이름입니다.");
    department.update(name, description);
    events.publishEvent(
        AuditEvent.of(
            user,
            "DEPARTMENT_UPDATED",
            "DEPARTMENT",
            id,
            null,
            "부서 정보 변경: " + department.getName()));
  }

  @Transactional
  public void close(String actor, Long id, Long targetId, Long revision) {
    User user = admin(actor);
    if (Objects.equals(id, targetId)) throw new IllegalArgumentException("다른 이관 부서를 선택해주세요.");
    users.findAdministratorsForUpdate();
    departments.lockDepartments(targetId == null ? List.of(id) : List.of(id, targetId));
    Department source = department(id);
    check(source, revision);
    Department target = targetId == null ? null : department(targetId);
    if (target != null && target.isClosed())
      throw new IllegalArgumentException("운영 중인 이관 부서를 선택해주세요.");
    var projectRows = projects.lockDepartmentProjects(id);
    var documentRows = documents.lockDepartmentDocuments(id);
    var userRows = users.lockDepartmentUsers(id);
    var noticeRows = notices.lockDepartmentNotices(id);
    if (target == null
        && (!projectRows.isEmpty()
            || !documentRows.isEmpty()
            || !userRows.isEmpty()
            || !noticeRows.isEmpty()))
      throw new IllegalArgumentException("사용자·문서·프로젝트·공지가 있는 부서는 이관 부서를 지정해야 합니다.");
    if (target != null) {
      projectRows.forEach(p -> p.transfer(target));
      documentRows.forEach(d -> d.transfer(target));
      userRows.forEach(u -> u.assign(u.getPosition(), target));
      noticeRows.forEach(n -> n.transfer(target));
    }
    source.close();
    events.publishEvent(
        AuditEvent.of(
            user,
            "DEPARTMENT_CLOSED",
            "DEPARTMENT",
            id,
            null,
            "부서 폐쇄 · 이관 부서: "
                + targetId
                + " · 사용자 "
                + userRows.size()
                + ", 문서 "
                + documentRows.size()
                + ", 프로젝트 "
                + projectRows.size()
                + ", 공지 "
                + noticeRows.size()));
  }

  private void check(Department department, Long revision) {
    if (!Objects.equals(department.getRevision(), revision))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "부서 정보가 변경되었습니다. 새로고침해주세요.");
  }

  private Department department(Long id) {
    return departments
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private User admin(String actor) {
    User user = actors.get(actor);
    if (!user.getPosition().isAdmin()) throw new AccessDeniedException("부서 관리는 관리자만 할 수 있습니다.");
    return user;
  }
}
