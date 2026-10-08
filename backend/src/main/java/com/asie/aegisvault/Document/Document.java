package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.project.Project;
import com.asie.aegisvault.security.SecurityClassification;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Getter
@NoArgsConstructor
public class Document {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "department_id", nullable = false)
  private Department department;

  @Enumerated(EnumType.STRING)
  @Column(name = "required_position", nullable = false, length = 30)
  @ColumnDefault("'STAFF'")
  private Position requiredPosition = Position.STAFF;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id")
  private Project project;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  @ColumnDefault("'OTHER'")
  private DocumentCategory category = DocumentCategory.OTHER;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @ColumnDefault("'INTERNAL'")
  private SecurityClassification classification = SecurityClassification.INTERNAL;

  @Column(nullable = false)
  @ColumnDefault("false")
  private boolean archived;

  @Version
  @ColumnDefault("0")
  private Long revision;

  public void organize(
      Project project, DocumentCategory category, SecurityClassification classification) {
    if (id != null) throw new IllegalStateException("등록된 문서의 소속과 등급은 변경할 수 없습니다.");
    if (category == null || classification == null)
      throw new IllegalArgumentException("문서 분류와 보안 등급을 선택해주세요.");
    this.project = project;
    this.category = category;
    this.classification = classification;
  }

  public void archive() {
    archived = true;
  }

  public void reopen() {
    archived = false;
  }

  public void transfer(Department target) {
    this.department = target;
  }

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public Document(User author, Department department) {
    this(author, department, Position.STAFF);
  }

  public Document(User author, Department department, Position requiredPosition) {
    if (author == null) {
      throw new IllegalArgumentException("문서 작성자가 필요합니다");
    }
    if (department == null) {
      throw new IllegalArgumentException("문서 담당 부서가 필요합니다");
    }
    if (requiredPosition == null) {
      throw new IllegalArgumentException("열람 가능한 최소 직급을 선택해주세요");
    }

    this.author = author;
    this.department = department;
    this.requiredPosition = requiredPosition;
  }
}
