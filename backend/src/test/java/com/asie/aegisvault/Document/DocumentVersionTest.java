package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentVersionTest {
    @Test
    void constructorSetsFieldsAndStartsAsDraft() {
        Document document = createDocument();
        String content = "첫 번째 문단\n\n두 번째 문단";

        DocumentVersion version = new DocumentVersion(document, 1, "시험 보고서", content);

        assertSame(document, version.getDocument());
        assertEquals(1, version.getVersionNumber());
        assertEquals("시험 보고서", version.getTitle());
        assertEquals(content, version.getContent());
        assertEquals(DocumentStatus.DRAFT, version.getStatus());
        // ID와 생성 시각은 저장 시 채워집니다.
        assertNull(version.getId());
        assertNull(version.getCreatedAt());
    }

    @Test
    void constructorAcceptsLaterVersionAndLongContent() {
        String title = "가".repeat(255);
        String content = "긴 문서 본문\n".repeat(10000);

        DocumentVersion version = new DocumentVersion(createDocument(), 2, title, content);

        assertEquals(2, version.getVersionNumber());
        assertEquals(title, version.getTitle());
        assertEquals(content, version.getContent());
        assertEquals(DocumentStatus.DRAFT, version.getStatus());
    }

    @Test
    void constructorRejectsMissingDocument() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DocumentVersion(null, 1, "제목", "본문"));

        assertEquals("버전이 속할 문서가 필요합니다", exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructorRejectsNonPositiveVersionNumber(int versionNumber) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DocumentVersion(createDocument(), versionNumber, "제목", "본문"));

        assertEquals("버전 번호는 1 이상이어야 합니다", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void constructorRejectsBlankTitle(String title) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DocumentVersion(createDocument(), 1, title, "본문"));

        assertEquals("문서 제목을 입력해주세요", exception.getMessage());
    }

    @Test
    void constructorRejectsTitleOver255Characters() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DocumentVersion(createDocument(), 1, "가".repeat(256), "본문"));

        assertEquals("문서 제목은 255자 이하여야 합니다", exception.getMessage());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void constructorRejectsBlankContent(String content) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new DocumentVersion(createDocument(), 1, "제목", content));

        assertEquals("문서 본문을 입력해주세요", exception.getMessage());
    }

    private Document createDocument() {
        User author = new User("writer", "writer@example.com", "test-password-hash");
        Department department = new Department("연구개발본부", "기술 문서 작성");
        return new Document(author, department);
    }
}
