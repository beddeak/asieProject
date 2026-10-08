package com.asie.aegisvault.project;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class ProjectForm {
  @NotBlank(message = "프로젝트 이름을 입력해주세요.")
  @Size(max = 150)
  private String name;

  @Size(max = 4000)
  private String description;

  private Long departmentId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate targetDate;

  private Long revision;
}
