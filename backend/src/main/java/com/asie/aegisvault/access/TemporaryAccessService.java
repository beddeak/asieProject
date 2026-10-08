package com.asie.aegisvault.access;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.security.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TemporaryAccessService {
  private final CurrentUser actors;
  private final TemporaryAccessRepository grants;
  private final DocumentRepository documents;
  private final DocumentLocks locks;
  private final ProjectMemberRepository members;
  private final DocumentAccess access;
  private final UserRepository users;
  private final ApplicationEventPublisher events;

  public Page<TemporaryAccess> mine(String actor, int page) {
    User user = actors.get(actor);
    return PageQueries.fetch(
        page, 20, Sort.by(Sort.Direction.DESC, "id"), p -> grants.findByUserId(user.getId(), p));
  }

  public Page<TemporaryAccess> requests(String actor, Long documentId, int page) {
    User user = actors.get(actor);
    Document document = document(documentId);
    manager(user, document);
    return PageQueries.fetch(
        page, 20, Sort.by(Sort.Direction.DESC, "id"), p -> grants.findByDocumentId(documentId, p));
  }

  @Transactional
  public void request(String actor, Long documentId, String reason, Instant until) {
    User user = actors.get(actor);
    Document document = locks.lock(documentId);
    if (document.isArchived()
        || document.getDepartment().isClosed()
        || document.getProject() != null
            && document.getProject().getStatus() == ProjectStatus.CLOSED)
      throw new IllegalArgumentException("종료 또는 보관된 문서는 새 접근 요청을 받을 수 없습니다.");
    eligibility(user, document);
    if (grants.existsByDocumentIdAndUserIdAndStatus(
        documentId, user.getId(), TemporaryAccess.Status.REQUESTED))
      throw new IllegalArgumentException("이미 처리 대기 중인 요청이 있습니다.");
    TemporaryAccess grant =
        grants.save(
            new TemporaryAccess(documentId, user.getId(), user.getNickname(), reason, until));
    List<Long> recipients =
        document.getProject() == null
            ? users.reviewers(
                document.getDepartment().getId(),
                Arrays.stream(Position.values())
                    .filter(p -> p.isAtLeast(Position.MANAGER))
                    .toList(),
                List.of(SecurityClassification.values()))
            : members.recipients(document.getProject().getId(), List.of(ProjectRole.OWNER));
    events.publishEvent(
        event(user, document, grant, "TEMPORARY_ACCESS_REQUESTED")
            .notify(recipients, "/access/document/" + documentId));
  }

  @Transactional
  public void decide(String actor, Long id, boolean approved, Instant until, String reason) {
    TemporaryAccess lookup = grant(id);
    Document document = locks.lock(lookup.getDocumentId());
    TemporaryAccess grant = grants.lockById(id).orElseThrow();
    User user = actors.get(actor);
    manager(user, document);
    if (approved) eligibility(users.findById(grant.getUserId()).orElseThrow(), document);
    grant.decide(user.getId(), approved, until, reason);
    events.publishEvent(
        event(user, document, grant, "TEMPORARY_ACCESS_DECIDED")
            .notify(List.of(grant.getUserId()), "/access"));
  }

  @Transactional
  public void revoke(String actor, Long id, String reason) {
    TemporaryAccess lookup = grant(id);
    Document document = locks.lock(lookup.getDocumentId());
    TemporaryAccess grant = grants.lockById(id).orElseThrow();
    User user = actors.get(actor);
    if (!Objects.equals(user.getId(), grant.getUserId())) manager(user, document);
    grant.revoke(reason);
    events.publishEvent(
        event(user, document, grant, "TEMPORARY_ACCESS_REVOKED")
            .notify(List.of(grant.getUserId()), "/access"));
  }

  private void manager(User user, Document document) {
    access.requireScope(user, document);
    if (user.getPosition().isAdmin()) return;
    if (document.getProject() != null) {
      if (members.role(document.getProject().getId(), user.getId()).orElse(null)
          != ProjectRole.OWNER) throw new AccessDeniedException("프로젝트 책임자만 접근 요청을 처리할 수 있습니다.");
    } else if (!access.canReview(user, document))
      throw new AccessDeniedException("부서 검토자만 접근 요청을 처리할 수 있습니다.");
  }

  private void eligibility(User user, Document document) {
    if (user.getAccountStatus() != AccountStatus.ACTIVE)
      throw new AccessDeniedException("활성 계정만 접근을 요청할 수 있습니다.");
    if (!user.getPosition().isAdmin()
        && (!user.getPosition().isAtLeast(document.getRequiredPosition())
            || !user.getClearance().permits(document.getClassification())))
      throw new AccessDeniedException("직급과 보안 등급은 임시 접근으로 우회할 수 없습니다.");
  }

  private Document document(Long id) {
    return documents
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private TemporaryAccess grant(Long id) {
    return grants.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }

  private AuditEvent event(User user, Document document, TemporaryAccess grant, String action) {
    return AuditEvent.of(
        user,
        action,
        "TEMPORARY_ACCESS",
        grant.getId(),
        document.getProject() == null ? null : document.getProject().getId(),
        "문서 #" + document.getId() + " 접근 요청 #" + grant.getId() + ": " + grant.getStatus());
  }
}
