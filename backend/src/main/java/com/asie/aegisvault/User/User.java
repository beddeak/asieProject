package com.asie.aegisvault.User;

import com.asie.aegisvault.Department.Department;
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
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100, unique = true)
  private String nickname;

  @Column(nullable = false, length = 255, unique = true)
  private String email;

  @Column(nullable = false, length = 255)
  private String password;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Position position;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus accountStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @ColumnDefault("'INTERNAL'")
  private SecurityClassification clearance = SecurityClassification.INTERNAL;

  @Column(nullable = false)
  @ColumnDefault("0")
  private long credentialsVersion;

  @Version
  @ColumnDefault("0")
  private Long revision;

  public void changeClearance(SecurityClassification clearance) {
    if (clearance == null) throw new IllegalArgumentException("보안 등급을 선택해주세요.");
    this.clearance = clearance;
  }

  public void changePassword(String encodedPassword) {
    if (encodedPassword == null || encodedPassword.isBlank())
      throw new IllegalArgumentException("비밀번호를 입력해주세요.");
    this.password = encodedPassword;
    credentialsVersion++;
  }

  @ManyToOne(fetch = FetchType.LAZY, optional = true)
  @JoinColumn(
      name = "department",
      nullable = true,
      foreignKey = @ForeignKey(name = "FK_department"))
  private Department department;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public void assign(Position position, Department department) {
    if (position == null) {
      throw new IllegalArgumentException("직급을 선택해주세요.");
    }
    this.position = position;
    this.department = department;
  }

  public void changeAccountStatus(AccountStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("계정 상태를 선택해주세요.");
    }
    this.accountStatus = status;
  }

  public User(String nickname, String email, String password) {
    this.nickname = nickname;
    this.email = email;
    this.password = password;
    this.department = null;
    this.position = Position.STAFF;
    this.accountStatus = AccountStatus.ACTIVE;
  }
}
