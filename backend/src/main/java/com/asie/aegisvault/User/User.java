package com.asie.aegisvault.User;

import com.asie.aegisvault.Department.Department;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.cglib.core.Local;

import lombok.NoArgsConstructor;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
        name = "department",
        nullable = true,
        foreignKey = @ForeignKey(name = "FK_department")
    )
    private Department department;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(String nickname,String email,String password) {
        this.nickname = nickname;
        this.email = email;
        this.password = password;
        this.department = null;
        this.position = Position.STAFF;
        this.accountStatus = AccountStatus.ACTIVE;
    }
}
