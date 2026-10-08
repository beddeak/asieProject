package com.asie.aegisvault.Document.dto;

import com.asie.aegisvault.User.Position;
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
                "시험 보고서", "시험 결과입니다.", Position.STAFF);

        assertTrue(validator.validate(request).isEmpty());
        assertEquals("시험 보고서", request.title());
        assertEquals("시험 결과입니다.", request.content());
        assertEquals(Position.STAFF, request.requiredPosition());
    }

    @Test
    void acceptsTitleAndContentAtLimits() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "가".repeat(255), "나".repeat(100_000), Position.STAFF);

        assertTrue(validator.validate(request).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankTitle(String title) {
        DocumentCreateRequest request = new DocumentCreateRequest(
                title, "본문", Position.STAFF);

        assertViolation(request, "title", "문서 제목을 입력해주세요");
    }

    @Test
    void rejectsTitleOver255Characters() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "가".repeat(256), "본문", Position.STAFF);

        assertViolation(request, "title", "문서 제목은 255자 이하여야 합니다");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void rejectsBlankContent(String content) {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "제목", content, Position.STAFF);

        assertViolation(request, "content", "문서 본문을 입력해주세요");
    }

    @Test
    void rejectsMissingRequiredPosition() {
        DocumentCreateRequest request = new DocumentCreateRequest(
                "제목", "본문", null);

        assertViolation(request, "requiredPosition", "열람 가능한 최소 직급을 선택해주세요");
    }

    private void assertViolation(DocumentCreateRequest request, String field, String message) {
        var violations = validator.validate(request);
        assertEquals(1, violations.size());
        var violation = violations.iterator().next();
        assertEquals(field, violation.getPropertyPath().toString());
        assertEquals(message, violation.getMessage());
    }
}
