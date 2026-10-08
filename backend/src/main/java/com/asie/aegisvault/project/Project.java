package com.asie.aegisvault.project;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "project",
    indexes = @Index(name = "idx_project_department", columnList = "department_id, status"))
public class Project {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 4000)
  private String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "department_id")
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "created_by")
  private User createdBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProjectStatus status;

  private LocalDate targetDate;
  @Version private Long revision;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public Project(
      String name, String description, Department department, User creator, LocalDate targetDate) {
    this.department = department;
    this.createdBy = creator;
    this.status = ProjectStatus.PLANNING;
    update(name, description, targetDate);
  }

  public void update(String name, String description, LocalDate targetDate) {
    requireOpen();
    if (name == null || name.isBlank() || name.length() > 150)
      throw new IllegalArgumentException("프로젝트 이름은 1~150자로 입력해주세요.");
    if (description != null && description.length() > 4000)
      throw new IllegalArgumentException("프로젝트 설명은 4,000자 이하여야 합니다.");
    this.name = name.strip();
    this.description = description == null ? "" : description.strip();
    this.targetDate = targetDate;
  }

  public void start() {
    if (status != ProjectStatus.PLANNING)
      throw new IllegalStateException("준비 중인 프로젝트만 시작할 수 있습니다.");
    status = ProjectStatus.ACTIVE;
  }

  public void requestReview() {
    if (status != ProjectStatus.ACTIVE)
      throw new IllegalStateException("진행 중인 프로젝트만 검토를 요청할 수 있습니다.");
    status = ProjectStatus.REVIEW;
  }

  public void markChanged() {
    requireOpen();
    if (status == ProjectStatus.RELEASED || status == ProjectStatus.REVIEW)
      status = ProjectStatus.ACTIVE;
  }

  public void release() {
    if (status != ProjectStatus.REVIEW) throw new IllegalStateException("검토 단계에서만 배포할 수 있습니다.");
    status = ProjectStatus.RELEASED;
  }

  public void close() {
    requireOpen();
    status = ProjectStatus.CLOSED;
  }

  public void requireOpen() {
    if (status == ProjectStatus.CLOSED) throw new IllegalStateException("종료된 프로젝트는 변경할 수 없습니다.");
  }

  public void transfer(Department target) {
    this.department = target;
  }
}
