package com.asie.aegisvault.Department;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentRepository departmentRepository;

    public Department create(String name,String description) {
        if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("부서 이름을 입력하세요");
        }
        if(departmentRepository.existsByName(name)) {
            throw new IllegalArgumentException("이미 있는 부서이름입니다");
        }
        Department department = new Department(name, description);

        return departmentRepository.save(department);
    }
}
