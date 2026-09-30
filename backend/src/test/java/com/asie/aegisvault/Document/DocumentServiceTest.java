package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.UserAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DocumentServiceTest {
    private DocumentVersionRepository versionRepository;
    private DocumentRepository documentRepository;
    private UserRepository userRepository;
    private DocumentService service;
    private User viewer;
    private Document document;

    @BeforeEach
    void setUp() {
        versionRepository = mock(DocumentVersionRepository.class);
        documentRepository = mock(DocumentRepository.class);
        userRepository = mock(UserRepository.class);
        service = new DocumentService(versionRepository, documentRepository, userRepository, new UserAccessPolicy());

        viewer = mock(User.class);
        document = mock(Document.class);
        when(viewer.getPosition()).thenReturn(Position.MANAGER);
        when(viewer.getId()).thenReturn(7L);
        when(document.getRequiredPosition()).thenReturn(Position.STAFF);
        when(viewer.getDepartment()).thenReturn(department(2000L));
        when(document.getDepartment()).thenReturn(department(2000L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(viewer));
        when(documentRepository.findById(42L)).thenReturn(Optional.of(document));
    }

    @ParameterizedTest
    @EnumSource(value = Position.class, names = {
            "MANAGER", "DEPUTY_GENERAL_MANAGER", "GENERAL_MANAGER", "EXECUTIVE"
    })
    void sameDepartmentManagerOrAboveCanRead(Position position) {
        when(viewer.getPosition()).thenReturn(position);
        assertLatestVersionReturned();
    }

    @ParameterizedTest
    @EnumSource(value = Position.class, names = {"STAFF", "ASSISTANT_MANAGER"})
    void sameDepartmentBelowManagerCanReadApprovedStaffDocument(Position position) {
        when(viewer.getPosition()).thenReturn(position);
        assertLatestVersionReturned();
    }

    @ParameterizedTest
    @EnumSource(value = Position.class, names = {
            "MANAGER", "DEPUTY_GENERAL_MANAGER", "GENERAL_MANAGER", "EXECUTIVE"
    })
    void higherPositionDoesNotBypassDepartmentRestriction(Position position) {
        when(viewer.getPosition()).thenReturn(position);
        when(viewer.getDepartment()).thenReturn(department(3000L));
        assertReadDeniedBeforeLoadingContent();
    }

    @Test
    void adminCanReadAnotherDepartmentsDocument() {
        when(viewer.getPosition()).thenReturn(Position.ADMIN);
        when(viewer.getDepartment()).thenReturn(department(3000L));
        assertLatestVersionReturned();
    }

    @Test
    void adminCanReadWithoutDepartmentAssignment() {
        when(viewer.getPosition()).thenReturn(Position.ADMIN);
        when(viewer.getDepartment()).thenReturn(null);
        assertLatestVersionReturned();
    }

    @Test
    void userWithoutDepartmentCannotRead() {
        when(viewer.getDepartment()).thenReturn(null);
        assertReadDeniedBeforeLoadingContent();
    }

    @Test
    void missingDepartmentIdDoesNotGrantAccess() {
        when(viewer.getDepartment()).thenReturn(department(null));
        when(document.getDepartment()).thenReturn(department(null));
        assertReadDeniedBeforeLoadingContent();
    }

    @Test
    void missingDocumentDepartmentDoesNotGrantAccess() {
        when(document.getDepartment()).thenReturn(null);
        assertReadDeniedBeforeLoadingContent();
    }

    @Test
    void userWithoutPositionCannotRead() {
        when(viewer.getPosition()).thenReturn(null);
        assertReadDeniedBeforeLoadingContent();
    }

    @Test
    void missingViewerIsDeniedWithoutLookingUpDocument() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        assertReadDeniedBeforeLoadingContent();
        verifyNoInteractions(documentRepository);
    }

    @Test
    void missingViewerIdIsDeniedWithoutDatabaseAccess() {
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(42L, null));
        verifyNoInteractions(userRepository, documentRepository, versionRepository);
    }

    @Test
    void missingDocumentReturnsNotFound() {
        when(documentRepository.findById(42L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.documentdetail(42L, 7L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verifyNoInteractions(versionRepository);
    }

    @Test
    void missingVersionReturnsNotFound() {
        when(versionRepository.findFirstByDocumentOrderByVersionNumberDesc(document)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.documentdetail(42L, 7L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    private void assertReadDeniedBeforeLoadingContent() {
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(42L, 7L));
        verifyNoInteractions(versionRepository);
    }

    private void assertLatestVersionReturned() {
        DocumentVersion version = mock(DocumentVersion.class);
        when(version.getStatus()).thenReturn(DocumentStatus.APPROVED);
        when(versionRepository.findFirstByDocumentOrderByVersionNumberDesc(document)).thenReturn(Optional.of(version));
        assertSame(version, service.documentdetail(42L, 7L));
        verify(versionRepository).findFirstByDocumentOrderByVersionNumberDesc(document);
    }

    private Department department(Long id) {
        Department department = new Department("연구개발본부", "테스트 부서");
        ReflectionTestUtils.setField(department, "id", id);
        return department;
    }

    @Test
    void documentGradeStillBlocksLowerRank() {
        when(document.getRequiredPosition()).thenReturn(Position.EXECUTIVE);
        assertReadDeniedBeforeLoadingContent();
    }

    @Test
    void pendingDocumentIsHiddenFromUnrelatedStaff() {
        when(viewer.getPosition()).thenReturn(Position.STAFF);
        DocumentVersion version = mock(DocumentVersion.class);
        when(version.getStatus()).thenReturn(DocumentStatus.PENDING_REVIEW);
        when(versionRepository.findFirstByDocumentOrderByVersionNumberDesc(document)).thenReturn(Optional.of(version));
        assertThrows(AccessDeniedException.class, () -> service.documentdetail(42L, 7L));
    }
}
