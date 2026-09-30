package com.asie.aegisvault.Document;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.ColumnDefault;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.Position;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
