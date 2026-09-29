package com.asie.aegisvault.Document;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.UserAccessPolicy;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final UserAccessPolicy userAccessPolicy;

    @Transactional()
    public Document create(int versionNumber,String title,String content,Long authorId) {
        if(title == null || title.isBlank()) {
            throw new IllegalArgumentException("문서 제목을 입력하세요");
        }
        if(content == null || content.isBlank()) {
            throw new IllegalArgumentException("문서 내용을 입력하세요");
        }
        User author = userRepository.findById(authorId).orElseThrow(() -> new IllegalArgumentException("유저를 찾을수가 없습니다"));

        Department department = author.getDepartment();
        if (department == null) {
            throw new IllegalArgumentException("부서를 찾을 수 가 없습니다");
        }
        Document document = new Document(author,department);
        document = documentRepository.save(document);

        DocumentVersion version = new DocumentVersion(document,1,title,content);
        documentVersionRepository.save(version);

        return document;
    }
    public DocumentVersion documentdetail(Long documentId, Long viewerId) {
        if (viewerId == null) {
            throw new AccessDeniedException("사용자 정보를 확인할 수 없습니다.");
        }

        User viewer = userRepository.findById(viewerId)
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 확인할 수 없습니다."));
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        validateReadPermission(viewer, document);

        return documentVersionRepository.findFirstByDocumentOrderByVersionNumberDesc(document)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
    }

    private void validateReadPermission(User viewer, Document document) {

        if (userAccessPolicy.isAdmin(viewer)) {
            return;
        }

        userAccessPolicy.requireManagerOrAbove(viewer);

        Department viewerDepartment = viewer.getDepartment();
        Department documentDepartment = document.getDepartment();
        if (viewerDepartment == null || documentDepartment == null
                || viewerDepartment.getId() == null
                || !viewerDepartment.getId().equals(documentDepartment.getId())) {
            throw new AccessDeniedException("해당 문서를 열람할 권한이 없습니다.");
        }
    }
}
