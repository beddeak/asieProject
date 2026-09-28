package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.Document.DocumentStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentCreateRequestTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void acceptsValidRequest() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "시험 보고서", "시험 결과입니다.", 1, DocumentStatus.DRAFT);

        assertTrue(validator.validate(request).isEmpty());
        assertEquals("시험 보고서", request.title());
        assertEquals("시험 결과입니다.", request.content());
        assertEquals(1, request.versionNumber());
        assertEquals(DocumentStatus.DRAFT, request.status());
    }

    @Test
    void acceptsTitleOf50CharactersAndLongContent() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "가".repeat(50), "본문\n".repeat(10000), 1, DocumentStatus.DRAFT);

        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankTitle(String title) {
        DocumentCreateRequest request = new DocumentCreateRequest(
                title, "본문", 1, DocumentStatus.DRAFT);

        assertViolation(request, "title", "문서 제목을 입력해주세요");
    }

    @Test
    void rejectsTitleOver50Characters() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "가".repeat(51), "본문", 1, DocumentStatus.DRAFT);

        assertViolation(request, "title", "문서 제목은 50자 이하여야 합니다");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankContent(String content) {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "제목", content, 1, DocumentStatus.DRAFT);

        assertViolation(request, "content", "문서 본문을 입력해주세요");
    }

    @Test
    void rejectsMissingVersionNumber() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "제목", "본문", null, DocumentStatus.DRAFT);

        assertViolation(request, "versionNumber", "버전 번호를 입력해주세요");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveVersionNumber(int versionNumber) {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "제목", "본문", versionNumber, DocumentStatus.DRAFT);

        assertViolation(request, "versionNumber", "버전 번호는 1 이상이어야 합니다");
    }

    @Test
    void rejectsMissingStatus() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "제목", "본문", 1, null);

        assertViolation(request, "status", "문서 상태를 선택해주세요");
    }

    private void assertViolation(DocumentCreateRequest request, String field, String message) {
        var violations = validator.validate(request);
        assertEquals(1, violations.size());
        var violation = violations.iterator().next();
        assertEquals(field, violation.getPropertyPath().toString());
        assertEquals(message, violation.getMessage());
    }
}
