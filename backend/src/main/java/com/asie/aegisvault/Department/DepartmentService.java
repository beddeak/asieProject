package com.asie.aegisvault.Department;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.UserAccessPolicy;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final UserAccessPolicy accessPolicy;

    public void requireAdmin(String actor) {
        if (actor == null || actor.isBlank()) {
            throw new AccessDeniedException("사용자를 확인할 수 없습니다.");
        }
        accessPolicy.requireAdmin(userRepository.findByNickname(actor)
                .orElseThrow(() -> new AccessDeniedException("사용자를 확인할 수 없습니다.")));
    }

    @Transactional
    public Department create(String name, String description, String actor) {
        requireAdmin(actor);
        if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("부서 이름을 입력하세요");
        }
        name = name.strip();
        description = description == null ? "" : description.strip();
        if (name.length() > 255 || description.length() > 2000) {
            throw new IllegalArgumentException("부서 이름은 255자, 설명은 2,000자 이하여야 합니다.");
        }
        if(departmentRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 있는 부서이름입니다");
        }
        Department department = new Department(name, description);

        return departmentRepository.save(department);
    }
}
