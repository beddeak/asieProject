package com.asie.aegisvault.Department;

import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter 
@Setter
public class DepartmentCreate {
    @NotBlank(message = "부서 이름을 입력해주세요.")
    @Size(max = 255, message = "부서 이름은 255자 이하여야 합니다.")
    private String name;
    @Size(max = 2000, message = "부서 설명은 2,000자 이하여야 합니다.")
    private String description;
}
