package com.asie.aegisvault.project;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.Document.DocumentCategory;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.*;
import com.asie.aegisvault.common.*;
import com.asie.aegisvault.security.CurrentUser;
import java.util.List;
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
public class ProjectService {
  private final CurrentUser actors;
  private final ProjectAccess access;
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;
  private final ProjectRequirementRepository requirements;
  private final DepartmentRepository departments;
  private final UserRepository users;
  private final AuditRecordRepository audit;
  private final ApplicationEventPublisher events;

  public Page<ProjectSummary> list(
      String actor, String keyword, ProjectStatus status, Long departmentId, int page) {
    User user = actors.get(actor);
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        pageable ->
            projects.search(
                user.getPosition().isAdmin(),
                user.getId(),
                SearchText.contains(keyword),
                status,
                departmentId,
                pageable));
  }

  public ProjectDetail detail(String actor, Long id) {
    User user = actors.get(actor);
    Project project = access.read(user, id);
    ProjectRole role = members.role(id, user.getId()).orElse(null);
    boolean admin = user.getPosition().isAdmin();
    boolean open = project.getStatus() != ProjectStatus.CLOSED;
    return new ProjectDetail(
        id,
        project.getName(),
        project.getDescription(),
        project.getDepartment().getName(),
        project.getDepartment().getId(),
        project.getStatus(),
        project.getTargetDate(),
        project.getRevision(),
        requirements.findByProjectIdOrderByCategory(id).stream()
            .map(r -> new ProjectDetail.Requirement(r.getCategory(), r.getMinimumCount()))
            .toList(),
        open && (admin || role == ProjectRole.OWNER),
        open && (admin || role != null && role.canWrite()),
        open && (admin || role == ProjectRole.ENGINEERING || role == ProjectRole.OWNER),
        open && (admin || role == ProjectRole.QUALITY),
        open && (admin || role == ProjectRole.SECURITY));
  }

  public Page<MemberSummary> members(String actor, Long id, int page) {
    access.read(actors.get(actor), id);
    return PageQueries.fetch(page, 20, Sort.unsorted(), p -> members.members(id, p));
  }

  public Page<Candidate> candidates(String actor, Long id, String keyword, int page) {
    User user = actors.get(actor);
    access.read(user, id);
    access.requireRole(user, id, ProjectRole.OWNER);
    return PageQueries.fetch(
        page,
        20,
        Sort.by("nickname", "id"),
        p -> users.candidates(SearchText.contains(keyword), p));
  }

  public Page<AuditRecord> timeline(String actor, Long id, int page) {
    access.read(actors.get(actor), id);
    return PageQueries.fetch(
        page,
        30,
        Sort.by(Sort.Direction.DESC, "id"),
        pageable -> audit.search(id, null, null, pageable));
  }

  @Transactional
  public Long create(String actor, ProjectForm form) {
    User user = actors.get(actor);
    if (!user.getPosition().isAtLeast(Position.MANAGER))
      throw new AccessDeniedException("과장 이상만 프로젝트를 생성할 수 있습니다.");
    Long departmentId =
        user.getPosition().isAdmin()
            ? form.getDepartmentId()
            : user.getDepartment() == null ? null : user.getDepartment().getId();
    if (departmentId == null) throw new IllegalArgumentException("담당 부서를 선택해주세요.");
    departments.lockDepartments(List.of(departmentId));
    Department department =
        departments
            .findById(departmentId)
            .orElseThrow(() -> new IllegalArgumentException("담당 부서를 찾을 수 없습니다."));
    if (department.isClosed()) throw new IllegalArgumentException("폐쇄된 부서에는 프로젝트를 생성할 수 없습니다.");
    Project project =
        projects.save(
            new Project(
                form.getName(), form.getDescription(), department, user, form.getTargetDate()));
    members.save(new ProjectMember(project, user, ProjectRole.OWNER));
    for (DocumentCategory category :
        List.of(
            DocumentCategory.DESIGN,
            DocumentCategory.TEST_REPORT,
            DocumentCategory.SECURITY_REVIEW))
      requirements.save(new ProjectRequirement(project, category, 1));
    events.publishEvent(
        AuditEvent.of(
            user,
            "PROJECT_CREATED",
            "PROJECT",
            project.getId(),
            project.getId(),
            "프로젝트 생성: " + project.getName()));
    return project.getId();
  }

  @Transactional
  public void update(String actor, Long id, ProjectForm form) {
    User user = actors.get(actor);
    Project project = access.lock(user, id, ProjectRole.OWNER);
    if (!Objects.equals(project.getRevision(), form.getRevision()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "프로젝트가 변경되었습니다. 새로고침 후 다시 수정해주세요.");
    project.update(form.getName(), form.getDescription(), form.getTargetDate());
    events.publishEvent(AuditEvent.of(user, "PROJECT_UPDATED", "PROJECT", id, id, "프로젝트 정보 변경"));
  }

  @Transactional
  public void changeStatus(String actor, Long id, String action) {
    User user = actors.get(actor);
    Project project = access.lock(user, id, ProjectRole.OWNER);
    switch (action) {
      case "start" -> project.start();
      case "review" -> project.requestReview();
      case "close" -> project.close();
      default -> throw new IllegalArgumentException("지원하지 않는 프로젝트 상태 변경입니다.");
    }
    events.publishEvent(
        AuditEvent.of(
            user,
            "PROJECT_STATUS_CHANGED",
            "PROJECT",
            id,
            id,
            "프로젝트 상태: " + project.getStatus().getLabel()));
  }

  @Transactional
  public void assignMember(String actor, Long id, Long userId, ProjectRole role) {
    User user = actors.get(actor);
    Project project = access.lock(user, id, ProjectRole.OWNER);
    User target =
        users.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    if (target.getAccountStatus() != AccountStatus.ACTIVE)
      throw new IllegalArgumentException("정상 계정만 참여자로 배정할 수 있습니다.");
    ProjectMember member = members.findByProjectIdAndUserId(id, userId).orElse(null);
    if (member == null) members.save(new ProjectMember(project, target, role));
    else {
      protectOwner(member, role);
      member.changeRole(role);
    }
    events.publishEvent(
        AuditEvent.of(
                user,
                "PROJECT_MEMBER_ASSIGNED",
                "USER",
                userId,
                id,
                target.getNickname() + " 참여 역할: " + role.getLabel())
            .notify(List.of(userId), "/projects/" + id));
  }

  @Transactional
  public void removeMember(String actor, Long id, Long userId) {
    User user = actors.get(actor);
    access.lock(user, id, ProjectRole.OWNER);
    ProjectMember member =
        members
            .findByProjectIdAndUserId(id, userId)
            .orElseThrow(() -> new IllegalArgumentException("참여자를 찾을 수 없습니다."));
    protectOwner(member, null);
    members.delete(member);
    events.publishEvent(
        AuditEvent.of(
            user,
            "PROJECT_MEMBER_REMOVED",
            "USER",
            userId,
            id,
            "프로젝트 참여 해제: " + member.getUser().getNickname()));
  }

  @Transactional
  public void requirement(String actor, Long id, DocumentCategory category, int count) {
    User user = actors.get(actor);
    Project project = access.lock(user, id, ProjectRole.OWNER);
    if (category == null || count < 0) throw new IllegalArgumentException("올바른 문서 분류와 수량을 입력해주세요.");
    var existing = requirements.findByProjectIdAndCategory(id, category);
    if (count == 0) existing.ifPresent(requirements::delete);
    else if (existing.isPresent()) existing.get().changeCount(count);
    else requirements.save(new ProjectRequirement(project, category, count));
    project.markChanged();
    events.publishEvent(
        AuditEvent.of(
            user,
            "PROJECT_REQUIREMENT_CHANGED",
            "PROJECT",
            id,
            id,
            category.getLabel() + " 필수 수량: " + count));
  }

  private void protectOwner(ProjectMember member, ProjectRole next) {
    if (member.getRole() == ProjectRole.OWNER
        && next != ProjectRole.OWNER
        && member.getUser().getAccountStatus() == AccountStatus.ACTIVE
        && members.activeOwners(member.getProject().getId()) <= 1)
      throw new IllegalArgumentException("정상 상태의 프로젝트 책임자가 최소 한 명 필요합니다.");
  }
}
