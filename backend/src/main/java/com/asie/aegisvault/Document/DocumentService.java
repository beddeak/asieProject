package com.asie.aegisvault.Document;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.security.UserAccessPolicy;
import com.asie.aegisvault.activity.DocumentAction;
import com.asie.aegisvault.activity.DocumentActivity;
import com.asie.aegisvault.activity.DocumentActivityRepository;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final UserAccessPolicy userAccessPolicy;
    private final DocumentActivityRepository activityRepository;

    @Transactional()
    public Document create(String title, String content, Position requiredPosition, Long authorId) {
        if(title == null || title.isBlank()) {
            throw new IllegalArgumentException("문서 제목을 입력하세요");
        }
        if(content == null || content.isBlank()) {
            throw new IllegalArgumentException("문서 내용을 입력하세요");
        }
        User author = findUser(authorId);
        userAccessPolicy.requireAssignablePosition(author, requiredPosition);

        Department department = author.getDepartment();
        if (department == null) {
            throw new IllegalArgumentException("부서를 찾을 수 가 없습니다");
        }
        Document document = new Document(author, department, requiredPosition);
        document = documentRepository.save(document);

        DocumentVersion version = new DocumentVersion(document,1,title,content);
        version.submitForReview();
        boolean automaticallyApproved = userAccessPolicy.isAdmin(author);
        if (automaticallyApproved) {
            version.approve(author);
        }
        documentVersionRepository.save(version);
        activityRepository.save(new DocumentActivity(author, version, DocumentAction.CREATED));
        if (automaticallyApproved) {
            activityRepository.save(new DocumentActivity(author, version, DocumentAction.APPROVED));
        }

        return document;
    }
    @Transactional
    public DocumentVersion documentdetail(Long documentId, Long viewerId) {
        User viewer = findUser(viewerId);
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        validateReadPermission(viewer, document);

        DocumentVersion version = documentVersionRepository.findFirstByDocumentOrderByVersionNumberDesc(document)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
        if (!userAccessPolicy.isAdmin(viewer) && version.getStatus() != DocumentStatus.APPROVED
                && !isAuthor(viewer, document)
                && !viewer.getPosition().isAtLeast(Position.MANAGER)) {
            throw new AccessDeniedException("승인 전·반려 문서는 작성자와 검토자만 열람할 수 있습니다.");
        }
        activityRepository.save(new DocumentActivity(viewer, version, DocumentAction.READ));
        return version;
    }

    public List<Position> assignablePositions(Long userId) {
        return userAccessPolicy.assignablePositions(findUser(userId));
    }

    public Page<DocumentVersion> accessibleDocuments(Long viewerId, int page, int size, String query) {
        User viewer = findUser(viewerId);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, 50)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        boolean admin = userAccessPolicy.isAdmin(viewer);
        Department department = viewer.getDepartment();
        if (!admin && (department == null || department.getId() == null || viewer.getPosition() == null)) {
            return Page.empty(pageRequest);
        }
        String titleQuery = query == null ? "" : query.strip();
        if (titleQuery.length() > 100) {
            titleQuery = titleQuery.substring(0, 100);
        }
        return documentVersionRepository.findAccessibleDocuments(admin,
                admin ? null : department.getId(), userAccessPolicy.assignablePositions(viewer), viewer.getId(),
                viewer.getPosition().isAtLeast(Position.MANAGER), DocumentStatus.APPROVED, titleQuery, pageRequest);
    }

    public Page<DocumentVersion> reviewQueue(Long reviewerId, int page) {
        User reviewer = findUser(reviewerId);
        userAccessPolicy.requireManagerOrAbove(reviewer);
        boolean admin = userAccessPolicy.isAdmin(reviewer);
        Department department = reviewer.getDepartment();
        if (!admin && (department == null || department.getId() == null)) {
            throw new AccessDeniedException("소속 부서가 배정되어야 검토할 수 있습니다.");
        }
        return documentVersionRepository.findReviewQueue(DocumentStatus.PENDING_REVIEW,
                admin ? null : department.getId(), userAccessPolicy.assignablePositions(reviewer),
                reviewer.getId(),
                PageRequest.of(Math.max(page, 0), 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @Transactional
    public DocumentVersion reviewDetail(Long versionId, Long reviewerId) {
        User reviewer = findUser(reviewerId);
        userAccessPolicy.requireManagerOrAbove(reviewer);
        DocumentVersion version = documentVersionRepository.findForDisplayById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
        validateReviewPermission(reviewer, version.getDocument());
        requirePendingLatestVersion(version);
        activityRepository.save(new DocumentActivity(reviewer, version, DocumentAction.REVIEW_OPENED));
        return version;
    }

    @Transactional
    public void approve(Long versionId, Long reviewerId) {
        review(versionId, reviewerId, true);
    }

    @Transactional
    public void reject(Long versionId, Long reviewerId) {
        review(versionId, reviewerId, false);
    }

    private void review(Long versionId, Long reviewerId, boolean approve) {
        User reviewer = findUser(reviewerId);
        userAccessPolicy.requireManagerOrAbove(reviewer);
        DocumentVersion version = documentVersionRepository.findForReviewById(versionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
        validateReviewPermission(reviewer, version.getDocument());
        requirePendingLatestVersion(version);
        if (approve) {
            version.approve(reviewer);
        } else {
            version.reject(reviewer);
        }
        activityRepository.save(new DocumentActivity(reviewer, version,
                approve ? DocumentAction.APPROVED : DocumentAction.REJECTED));
    }

    private void requirePendingLatestVersion(DocumentVersion version) {
        if (version.getStatus() != DocumentStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 처리되었거나 검토 대기 상태가 아닌 문서입니다.");
        }
        DocumentVersion latest = documentVersionRepository
                .findFirstByDocumentOrderByVersionNumberDesc(version.getDocument())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
        if (!version.getId().equals(latest.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "최신 버전만 검토할 수 있습니다.");
        }
    }

    private User findUser(Long userId) {
        if (userId == null) {
            throw new AccessDeniedException("사용자 정보를 확인할 수 없습니다.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 확인할 수 없습니다."));
        userAccessPolicy.requireActive(user);
        return user;
    }

    private boolean isAuthor(User user, Document document) {
        return document.getAuthor() != null && user.getId() != null
                && user.getId().equals(document.getAuthor().getId());
    }

    private void validateReviewPermission(User reviewer, Document document) {
        userAccessPolicy.requireManagerOrAbove(reviewer);
        validateReadPermission(reviewer, document);
        if (isAuthor(reviewer, document)) {
            throw new AccessDeniedException("자신이 작성한 문서는 다른 검토자가 처리해야 합니다.");
        }
    }

    private void validateReadPermission(User viewer, Document document) {

        if (userAccessPolicy.isAdmin(viewer)) {
            return;
        }

        Department viewerDepartment = viewer.getDepartment();
        Department documentDepartment = document.getDepartment();
        if (viewerDepartment == null || documentDepartment == null
                || viewerDepartment.getId() == null
                || !viewerDepartment.getId().equals(documentDepartment.getId())) {
            throw new AccessDeniedException("해당 문서를 열람할 권한이 없습니다.");
        }
        if (viewer.getPosition() == null || document.getRequiredPosition() == null
                || !viewer.getPosition().isAtLeast(document.getRequiredPosition())) {
            throw new AccessDeniedException("문서 열람에 필요한 직급을 충족하지 않습니다.");
        }
    }
}
