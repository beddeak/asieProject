package com.asie.aegisvault.security;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.access.TemporaryAccessRepository;
import com.asie.aegisvault.project.*;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocumentAccess {
  private final ProjectMemberRepository members;
  private final TemporaryAccessRepository grants;
  private final UserAccessPolicy accounts;

  public void requireScope(User user, Document document) {
    requireScopes(user, java.util.List.of(document));
  }

  /**
   * Batch operations reuse membership checks while still validating each document's grade and
   * clearance.
   */
  public void requireScopes(User user, java.util.Collection<Document> documents) {
    accounts.requireActive(user);
    if (accounts.isAdmin(user)) return;
    java.util.Map<Long, Boolean> membership = new java.util.HashMap<>();
    Instant now = Instant.now();
    for (Document document : documents) {
      if (user.getPosition() == null
          || document.getRequiredPosition() == null
          || !user.getPosition().isAtLeast(document.getRequiredPosition()))
        throw new AccessDeniedException("문서 열람에 필요한 직급을 충족하지 않습니다.");
      if (user.getClearance() == null
          || document.getClassification() == null
          || !user.getClearance().permits(document.getClassification()))
        throw new AccessDeniedException("문서의 보안 등급에 대한 접근 권한이 없습니다.");
      boolean scope =
          document.getProject() == null
              ? user.getDepartment() != null
                  && document.getDepartment() != null
                  && user.getDepartment().getId() != null
                  && Objects.equals(user.getDepartment().getId(), document.getDepartment().getId())
              : membership.computeIfAbsent(
                  document.getProject().getId(),
                  id -> members.existsByProjectIdAndUserId(id, user.getId()));
      if (!scope && !grants.effective(document.getId(), user.getId(), now))
        throw new AccessDeniedException("문서의 소속 또는 임시 접근 권한이 없습니다.");
    }
  }

  public void requireRead(User user, DocumentVersion version) {
    Document document = version.getDocument();
    requireScope(user, document);
    if (accounts.isAdmin(user)) return;
    boolean author =
        Objects.equals(document.getAuthor().getId(), user.getId())
            || version.writtenBy(user.getId());
    if (version.getStatus() == DocumentStatus.DRAFT
        && !author
        && !canEdit(user, document)
        && !canReview(user, document)) throw new AccessDeniedException("초안은 작성 담당자만 열람할 수 있습니다.");
    boolean published =
        version.getStatus() == DocumentStatus.APPROVED
            || version.getStatus() == DocumentStatus.ARCHIVED
                && version.getArchivedFrom() == DocumentStatus.APPROVED;
    if (!published
        && !author
        && !canEdit(user, document)
        && !canReview(user, document)
        && !canSecure(user, document))
      throw new AccessDeniedException("검토 중인 문서는 작성자와 담당 검토자만 열람할 수 있습니다.");
  }

  public boolean canEdit(User user, Document document) {
    if (accounts.isAdmin(user)) return true;
    if (document.getProject() != null)
      return members
          .role(document.getProject().getId(), user.getId())
          .map(ProjectRole::canWrite)
          .orElse(false);
    return user.getId() != null
        && Objects.equals(user.getId(), document.getAuthor().getId())
        && user.getDepartment() != null
        && Objects.equals(user.getDepartment().getId(), document.getDepartment().getId());
  }

  public void requireEdit(User user, Document document) {
    requireScope(user, document);
    if (!canEdit(user, document)) throw new AccessDeniedException("문서를 수정할 권한이 없습니다.");
    if (document.getDepartment().isClosed())
      throw new IllegalStateException("폐쇄된 부서의 문서는 변경할 수 없습니다.");
    if (document.getProject() != null) document.getProject().requireOpen();
  }

  public boolean canReview(User user, Document document) {
    if (accounts.isAdmin(user)) return true;
    if (!user.getPosition().isAtLeast(Position.MANAGER)) return false;
    return document.getProject() == null
        ? user.getDepartment() != null
            && Objects.equals(user.getDepartment().getId(), document.getDepartment().getId())
        : members
            .role(document.getProject().getId(), user.getId())
            .map(role -> role == ProjectRole.ENGINEERING || role == ProjectRole.OWNER)
            .orElse(false);
  }

  public boolean canSecure(User user, Document document) {
    if (accounts.isAdmin(user)) return true;
    return user.getPosition().isAtLeast(Position.MANAGER)
        && user.getClearance() != SecurityClassification.INTERNAL
        && document.getProject() != null
        && members.role(document.getProject().getId(), user.getId()).orElse(null)
            == ProjectRole.SECURITY;
  }

  public void requireReview(User user, DocumentVersion version, boolean security) {
    requireScope(user, version.getDocument());
    if (version.getDocument().getProject() != null)
      version.getDocument().getProject().requireOpen();
    if (version.writtenBy(user.getId())
        || Objects.equals(version.getDocument().getAuthor().getId(), user.getId()))
      throw new AccessDeniedException("자신이 작성한 문서는 다른 검토자가 처리해야 합니다.");
    if (security
        ? !canSecure(user, version.getDocument())
        : !canReview(user, version.getDocument()))
      throw new AccessDeniedException("담당 검토 권한이 필요합니다.");
    if (security
        && version.getReviewedBy() != null
        && Objects.equals(version.getReviewedBy().getId(), user.getId()))
      throw new AccessDeniedException("기술 검토자와 보안 검토자는 달라야 합니다.");
  }
}
