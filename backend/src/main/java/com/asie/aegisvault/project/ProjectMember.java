package com.asie.aegisvault.project;

import com.asie.aegisvault.User.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
    name = "project_member",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_project_member",
            columnNames = {"project_id", "user_id"}),
    indexes = @Index(name = "idx_membership_user", columnList = "user_id, project_id"))
public class ProjectMember {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ProjectRole role;

  public ProjectMember(Project project, User user, ProjectRole role) {
    this.project = project;
    this.user = user;
    changeRole(role);
  }

  public void changeRole(ProjectRole role) {
    if (role == null) throw new IllegalArgumentException("참여 역할을 선택해주세요.");
    this.role = role;
  }
}
