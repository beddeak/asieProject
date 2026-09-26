package com.asie.aegisvault.Document;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 제목과 본문은 DocumentVersion에서 하는걸로 수정.
    public Document(User author, Department department) {
        if (author == null) {
            throw new IllegalArgumentException("문서 작성자가 필요합니다");
        }
        if (department == null) {
            throw new IllegalArgumentException("문서 담당 부서가 필요합니다");
        }

        this.author = author;
        this.department = department;
    }
}
