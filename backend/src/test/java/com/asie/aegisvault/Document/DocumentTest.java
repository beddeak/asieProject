package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentTest {
    @Test
    void constructorSetsAuthorAndDepartment() {
        User author = new User("writer", "writer@example.com", "test-password-hash");
        Department department = new Department("연구개발본부", "기술 문서 작성");

        Document document = new Document(author, department);

        assertSame(author, document.getAuthor());
        assertSame(department, document.getDepartment());
        // ID와 생성 시각은 DB 저장 시 채워집니다.
        assertNull(document.getId());
        assertNull(document.getCreatedAt());
    }

    @Test
    void constructorRejectsMissingAuthor() {
        Department department = new Department("연구개발본부", "기술 문서 작성");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Document(null, department));

        assertEquals("문서 작성자가 필요합니다", exception.getMessage());
    }

    @Test
    void constructorRejectsMissingDepartment() {
        User author = new User("writer", "writer@example.com", "test-password-hash");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new Document(author, null));

        assertEquals("문서 담당 부서가 필요합니다", exception.getMessage());
    }
}
