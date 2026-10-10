package com.asie.aegisvault.notification;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.security.SecurityClassification;
import com.asie.aegisvault.security.UserAccessPolicy;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeTaskService {
  private final DocumentService documents;
  private final UserAccessPolicy policy;

  public List<Queue> queues(User user) {
    policy.requireActive(user);
    List<Queue> result = new ArrayList<>();
    for (DocumentStatus status : List.of(DocumentStatus.DRAFT, DocumentStatus.REJECTED)) {
      DocumentFilter filter = new DocumentFilter();
      filter.setMine(true);
      filter.setStatus(status);
      boolean draft = status == DocumentStatus.DRAFT;
      result.add(
          queue(
              user,
              filter,
              draft ? "내 초안" : "반려 문서",
              draft ? "작성 중인 문서를 마무리하고 제출하세요." : "반려 사유를 확인하고 새 버전을 준비하세요.",
              draft ? "/tasks?tab=drafts" : "/tasks?tab=rejected"));
    }
    if (policy.canReviewDocuments(user)) {
      DocumentFilter review = new DocumentFilter();
      review.setReviewOnly(true);
      result.add(queue(user, review, "기술 검토", "검토 권한이 있는 대기 문서를 확인하세요.", "/tasks?tab=review"));
      if (policy.isAdmin(user) || user.getClearance() != SecurityClassification.INTERNAL) {
        DocumentFilter security = new DocumentFilter();
        security.setSecurityOnly(true);
        result.add(
            queue(user, security, "보안 검토", "보안 승인 대기 문서의 검토를 진행하세요.", "/tasks?tab=security"));
      }
    }
    return List.copyOf(result);
  }

  private Queue queue(
      User user, DocumentFilter filter, String title, String description, String href) {
    // Reuse the destination list's current visibility policy; fetch at most one summary row.
    long count = documents.documentList(user.getId(), filter, 0, 1).documents().getTotalElements();
    return new Queue(title, description, href, count);
  }

  public record Queue(String title, String description, String href, long count) {}
}
