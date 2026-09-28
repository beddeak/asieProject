package com.asie.aegisvault.Document;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

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
}
