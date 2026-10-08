package com.asie.aegisvault.notice;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@NoArgsConstructor
@Table(
    name = "department_notice",
    indexes =
        @Index(name = "IDX_department_notice_recent", columnList = "department_id, created_at, id"))
public class DepartmentNotice {
  public enum Scope {
    GLOBAL,
    DEPARTMENT
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @org.hibernate.annotations.ColumnDefault("'DEPARTMENT'")
  private Scope scope = Scope.DEPARTMENT;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "department_id")
  private Department department;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "author_id", nullable = false)
  private User author;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 10000)
  private String content;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version private Long version;

  public DepartmentNotice(Department department, User author, String title, String content) {
    if (department == null || author == null) {
      throw new IllegalArgumentException("공지의 부서와 작성자가 필요합니다.");
    }
    this.department = department;
    this.author = author;
    updateText(title, content);
  }

  public static DepartmentNotice global(User author, String title, String content) {
    DepartmentNotice notice = new DepartmentNotice();
    notice.author = java.util.Objects.requireNonNull(author);
    notice.scope = Scope.GLOBAL;
    notice.updateText(title, content);
    return notice;
  }

  public void updateText(String title, String content) {
    if (title == null || title.isBlank() || title.length() > 255) {
      throw new IllegalArgumentException("공지 제목은 1~255자로 입력해주세요.");
    }
    if (content == null || content.isBlank() || content.length() > 10000) {
      throw new IllegalArgumentException("공지 내용은 1~10,000자로 입력해주세요.");
    }
    this.title = title.strip();
    this.content = content;
  }

  public void transfer(Department target) {
    if (scope != Scope.DEPARTMENT || target == null || target.isClosed())
      throw new IllegalArgumentException("부서 공지는 운영 중인 부서로 이관해야 합니다.");
    this.department = target;
  }
}
