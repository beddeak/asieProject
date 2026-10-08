package com.asie.aegisvault.Document;

import com.asie.aegisvault.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 모든 버전·첨부·검토 변경이 프로젝트 → 문서 순서로 잠금을 획득합니다. */
@Component
@RequiredArgsConstructor
public class DocumentLocks {
  private final ProjectRepository projects;
  private final DocumentRepository documents;

  @Transactional(propagation = Propagation.MANDATORY)
  public Document lock(Long id) {
    documents
        .projectId(id)
        .ifPresent(
            projectId ->
                projects
                    .lockById(projectId)
                    .orElseThrow(
                        () ->
                            new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다.")));
    return documents
        .lockById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
  }
}
