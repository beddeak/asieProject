package com.asie.aegisvault.Department;

import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {
  private final DepartmentRepository departmentRepository;
  private final UserRepository userRepository;
  private final UserAccessPolicy accessPolicy;
  private final org.springframework.context.ApplicationEventPublisher events;

  public void requireAdmin(String actor) {
    if (actor == null || actor.isBlank()) {
      throw new AccessDeniedException("사용자를 확인할 수 없습니다.");
    }
    accessPolicy.requireAdmin(
        userRepository
            .findByNickname(actor)
            .orElseThrow(() -> new AccessDeniedException("사용자를 확인할 수 없습니다.")));
  }

  @Transactional
  public Department create(String name, String description, String actor) {
    requireAdmin(actor);
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("부서 이름을 입력하세요");
    }
    name = name.strip();
    description = description == null ? "" : description.strip();
    if (name.length() > 255 || description.length() > 2000) {
      throw new IllegalArgumentException("부서 이름은 255자, 설명은 2,000자 이하여야 합니다.");
    }
    if (departmentRepository.existsByName(name)) {
      throw new IllegalArgumentException("이미 있는 부서이름입니다");
    }
    Department department = new Department(name, description);

    departmentRepository.save(department);
    events.publishEvent(
        com.asie.aegisvault.audit.AuditEvent.of(
            userRepository.findByNickname(actor).orElseThrow(),
            "DEPARTMENT_CREATED",
            "DEPARTMENT",
            department.getId(),
            null,
            "부서 생성: " + department.getName()));
    return department;
  }
}
