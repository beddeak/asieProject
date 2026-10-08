package com.asie.aegisvault.Department;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "department")
public class Department {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 255, unique = true)
  private String name;

  @Column(nullable = false, length = 2000)
  private String description;

  @Column(nullable = false)
  @ColumnDefault("false")
  private boolean closed;

  @Version
  @ColumnDefault("0")
  private Long revision;

  public void update(String name, String description) {
    if (closed) throw new IllegalStateException("폐쇄된 부서는 수정할 수 없습니다.");
    if (name == null || name.isBlank() || name.length() > 255)
      throw new IllegalArgumentException("부서 이름은 1~255자로 입력해주세요.");
    if (description != null && description.length() > 2000)
      throw new IllegalArgumentException("부서 설명은 2,000자 이하여야 합니다.");
    this.name = name.strip();
    this.description = description == null ? "" : description.strip();
  }

  public void close() {
    if (closed) throw new IllegalStateException("이미 폐쇄된 부서입니다.");
    closed = true;
  }

  public Department(String name, String description) {
    this.name = name;
    this.description = description;
  }
}
