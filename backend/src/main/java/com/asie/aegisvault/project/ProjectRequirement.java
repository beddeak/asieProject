package com.asie.aegisvault.project;

import com.asie.aegisvault.Document.DocumentCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "project_requirement",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_project_requirement",
            columnNames = {"project_id", "category"}))
public class ProjectRequirement {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private DocumentCategory category;

  @Column(nullable = false)
  private int minimumCount;

  public ProjectRequirement(Project project, DocumentCategory category, int count) {
    if (category == null) throw new IllegalArgumentException("문서 분류를 선택해주세요.");
    this.project = project;
    this.category = category;
    changeCount(count);
  }

  public void changeCount(int count) {
    if (count < 1) throw new IllegalArgumentException("필수 문서 수는 1 이상이어야 합니다.");
    this.minimumCount = count;
  }
}
