package com.asie.aegisvault.project;

import com.asie.aegisvault.User.User;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class ProjectAccess {
  private final ProjectRepository projects;
  private final ProjectMemberRepository members;

  public Project read(User actor, Long id) {
    requireMember(actor, id);
    return projects
        .findForDisplayById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
  }

  public Project lock(User actor, Long id, ProjectRole... roles) {
    requireMember(actor, id);
    Project project =
        projects
            .lockById(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    requireRole(actor, id, roles);
    project.requireOpen();
    return project;
  }

  public void requireMember(User actor, Long projectId) {
    if (!actor.getPosition().isAdmin()
        && !members.existsByProjectIdAndUserId(projectId, actor.getId()))
      throw new AccessDeniedException("프로젝트 참여자만 접근할 수 있습니다.");
  }

  public void requireRole(User actor, Long projectId, ProjectRole... roles) {
    if (actor.getPosition().isAdmin()) return;
    ProjectRole role =
        members
            .role(projectId, actor.getId())
            .orElseThrow(() -> new AccessDeniedException("프로젝트 참여 권한이 없습니다."));
    if (roles.length > 0 && Arrays.stream(roles).noneMatch(candidate -> candidate == role))
      throw new AccessDeniedException("이 작업을 담당하는 프로젝트 역할이 필요합니다.");
  }

  public boolean hasRole(User actor, Long projectId, ProjectRole... roles) {
    if (actor.getPosition().isAdmin()) return true;
    return members
        .role(projectId, actor.getId())
        .map(role -> Arrays.asList(roles).contains(role))
        .orElse(false);
  }
}
