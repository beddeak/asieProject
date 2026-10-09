package com.asie.aegisvault.Document;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Document.dto.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.activity.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.*;
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
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DocumentService {
  private final DocumentVersionRepository versions;
  private final DocumentRepository documents;
  private final UserRepository users;
  private final UserAccessPolicy accounts;
  private final DocumentActivityRepository activities;
  private final DocumentAccess access;
  private final DocumentLocks locks;
  private final ProjectAccess projects;
  private final ProjectMemberRepository members;
  private final ReviewNoteRepository notes;
  private final ApplicationEventPublisher events;
  private final com.asie.aegisvault.attachment.AttachmentRepository attachments;
  private final com.asie.aegisvault.Department.DepartmentRepository departments;

  @Transactional
  public Document create(String title, String content, Position position, Long authorId) {
    return create(
        new DocumentCommand(
            title,
            content,
            position,
            null,
            DocumentCategory.OTHER,
            SecurityClassification.INTERNAL,
            false),
        authorId);
  }

  @Transactional
  public Document create(DocumentCommand command, Long authorId) {
    User actor = user(authorId);
    accounts.requireAssignablePosition(actor, command.requiredPosition());
    if (command.classification() == null
        || !accounts.isAdmin(actor) && !actor.getClearance().permits(command.classification()))
      throw new AccessDeniedException("보유한 보안 등급 범위에서 문서를 생성해주세요.");
    if (command.projectId() == null && actor.getDepartment() != null)
      departments.lockDepartments(List.of(actor.getDepartment().getId()));
    Project project =
        command.projectId() == null
            ? null
            : projects.lock(
                actor,
                command.projectId(),
                ProjectRole.OWNER,
                ProjectRole.ENGINEERING,
                ProjectRole.CONTRIBUTOR);
    Department department = project == null ? actor.getDepartment() : project.getDepartment();
    if (department == null || department.isClosed())
      throw new IllegalArgumentException("운영 중인 담당 부서가 필요합니다.");
    Document document = new Document(actor, department, command.requiredPosition());
    document.organize(project, command.category(), command.classification());
    documents.save(document);
    DocumentVersion version =
        DocumentVersion.draft(document, 1, command.title(), command.content(), actor);
    versions.save(version);
    changed(document);
    activities.save(new DocumentActivity(actor, version, DocumentAction.CREATED));
    if (!command.draft()) submit(version, actor);
    event(
        actor,
        version,
        command.draft() ? "DOCUMENT_DRAFTED" : "DOCUMENT_SUBMITTED",
        version.getStatus() == DocumentStatus.PENDING_REVIEW ? reviewers(document) : List.of());
    return document;
  }

  @Transactional
  public DocumentVersion documentdetail(Long documentId, Long viewerId) {
    User actor = user(viewerId);
    Document document = document(documentId);
    access.requireScope(actor, document);
    return read(actor, latest(document));
  }

  @Transactional
  public DocumentVersion versionDetail(Long versionId, Long viewerId) {
    return read(user(viewerId), version(versionId));
  }

  private DocumentVersion read(User actor, DocumentVersion version) {
    access.requireRead(actor, version);
    activities.save(new DocumentActivity(actor, version, DocumentAction.READ));
    event(actor, version, "DOCUMENT_READ", List.of());
    return version;
  }

  public List<Position> assignablePositions(Long userId) {
    return accounts.assignablePositions(user(userId));
  }

  public DocumentListResult documentList(Long viewerId, int page) {
    return documentList(viewerId, new DocumentFilter(), page);
  }

  public DocumentListResult documentList(Long viewerId, DocumentFilter filter, int page) {
    return documentList(viewerId, filter, page, 20);
  }

  public DocumentListResult documentList(Long viewerId, DocumentFilter filter, int page, int size) {
    User actor = user(viewerId);
    boolean admin = accounts.isAdmin(actor);
    boolean assigned = actor.getDepartment() != null;
    Page<DocumentListItem> result =
        PageQueries.fetch(
            page,
            Math.clamp(size, 1, 50),
            filter.ordering(),
            pageable ->
                versions.findVisibleLatestVersions(
                    admin,
                    departmentId(actor),
                    accounts.assignablePositions(actor),
                    actor.getPosition().isAtLeast(Position.MANAGER),
                    securityReviewer(actor),
                    actor.getId(),
                    clearances(actor),
                    Instant.now(),
                    SearchText.contains(filter.getKeyword()),
                    SearchText.contains(filter.getAuthor()),
                    filter.getStatus(),
                    filter.getCategory(),
                    filter.getProjectId(),
                    filter.getDepartmentId(),
                    filter.isArchived(),
                    filter.isApprovedOnly(),
                    filter.isMine(),
                    filter.isReviewOnly(),
                    filter.isSecurityOnly(),
                    pageable));
    return new DocumentListResult(
        result,
        admin ? "전체 부서" : assigned ? actor.getDepartment().getName() : "소속 부서 미배정",
        !admin && !assigned,
        assigned,
        actor.getPosition().isAtLeast(Position.MANAGER));
  }

  public Page<DocumentListItem> accessibleDocuments(
      Long viewerId, int page, int size, String query) {
    DocumentFilter filter = new DocumentFilter();
    filter.setKeyword(query == null ? "" : query.strip());
    return documentList(viewerId, filter, page, size).documents();
  }

  public Page<DocumentListItem> history(Long documentId, Long viewerId, int page) {
    User actor = user(viewerId);
    access.requireScope(actor, document(documentId));
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "versionNumber"),
        pageable -> visibleHistory(documentId, actor, pageable));
  }

  public DocumentHistorySummary historySummary(Long documentId, Long viewerId) {
    User actor = user(viewerId);
    Document document = document(documentId);
    access.requireScope(actor, document);
    var latestVisible =
        visibleHistory(
            documentId, actor, PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "versionNumber")));
    var approved = versions.approvedHistory(documentId, PageRequest.of(0, 1));
    return new DocumentHistorySummary(
        latestVisible.isEmpty() ? null : latestVisible.getContent().getFirst(),
        approved.isEmpty() ? null : approved.getFirst(),
        document.isArchived());
  }

  private Page<DocumentListItem> visibleHistory(Long documentId, User actor, Pageable pageable) {
    return versions.history(
        documentId,
        accounts.isAdmin(actor),
        departmentId(actor),
        accounts.assignablePositions(actor),
        actor.getPosition().isAtLeast(Position.MANAGER),
        securityReviewer(actor),
        actor.getId(),
        clearances(actor),
        Instant.now(),
        pageable);
  }

  /** Batch lookup using the same visibility policy as the document list and history. */
  public List<DocumentListItem> readableVersions(Long actorId, Collection<Long> versionIds) {
    User actor = user(actorId);
    if (versionIds.isEmpty()) return List.of();
    return versions.findVisibleVersions(
        versionIds,
        accounts.isAdmin(actor),
        departmentId(actor),
        accounts.assignablePositions(actor),
        actor.getPosition().isAtLeast(Position.MANAGER),
        securityReviewer(actor),
        actor.getId(),
        clearances(actor),
        Instant.now());
  }

  public Map<Long, String> readableVersionTitles(Long actorId, Collection<Long> versionIds) {
    Map<Long, String> titles = new HashMap<>();
    readableVersions(actorId, versionIds).forEach(v -> titles.put(v.versionId(), v.title()));
    return Map.copyOf(titles);
  }

  @Transactional
  public Long newVersion(Long documentId, Long actorId, Long expectedVersionId) {
    User actor = user(actorId);
    Document document = locks.lock(documentId);
    access.requireEdit(actor, document);
    DocumentVersion previous = latest(document);
    if (!Objects.equals(previous.getId(), expectedVersionId)) conflict("다른 버전이 생성되었습니다. 새로고침해주세요.");
    if (previous.getStatus() == DocumentStatus.DRAFT
        || previous.getStatus() == DocumentStatus.PENDING_REVIEW
        || previous.getStatus() == DocumentStatus.PENDING_SECURITY_APPROVAL)
      conflict("현재 초안 또는 검토를 먼저 완료해주세요.");
    document.reopen();
    DocumentVersion next =
        versions.save(
            DocumentVersion.draft(
                document,
                previous.getVersionNumber() + 1,
                previous.getTitle(),
                previous.getContent(),
                actor));
    attachments.saveAll(
        attachments.findByVersionIdOrderById(previous.getId()).stream()
            .map(a -> a.copy(next.getId()))
            .toList());
    changed(document);
    event(actor, next, "DOCUMENT_VERSION_CREATED", List.of());
    return next.getId();
  }

  @Transactional
  public void edit(
      Long versionId, Long actorId, Long revision, String title, String content, boolean submit) {
    User actor = user(actorId);
    DocumentVersion version = lockedVersion(versionId);
    access.requireEdit(actor, version.getDocument());
    requireLatest(version);
    if (!Objects.equals(revision, version.getRevision()))
      conflict("문서가 변경되었습니다. 새로고침 후 다시 저장해주세요.");
    version.editDraft(title, content, actor);
    if (submit) submit(version, actor);
    changed(version.getDocument());
    event(
        actor,
        version,
        submit ? "DOCUMENT_SUBMITTED" : "DOCUMENT_DRAFT_UPDATED",
        submit && version.getStatus() == DocumentStatus.PENDING_REVIEW
            ? reviewers(version.getDocument())
            : List.of());
  }

  private void submit(DocumentVersion version, User actor) {
    version.submitForReview();
    if (!accounts.isAdmin(actor)) return;
    version.approve(actor);
    notes.save(new ReviewNote(version, actor, "AUTO_APPROVED", "관리자 작성 문서 즉시 승인 정책에 따라 승인되었습니다."));
    activities.save(new DocumentActivity(actor, version, DocumentAction.APPROVED));
    event(actor, version, "DOCUMENT_AUTO_APPROVED", List.of());
  }

  @Transactional
  public void archive(Long documentId, Long actorId, Long expectedVersionId) {
    User actor = user(actorId);
    Document document = locks.lock(documentId);
    access.requireEdit(actor, document);
    DocumentVersion latest = latest(document);
    if (document.isArchived() || !Objects.equals(latest.getId(), expectedVersionId))
      conflict("문서 상태가 변경되었습니다. 새로고침해주세요.");
    latest.archive();
    document.archive();
    changed(document);
    event(actor, latest, "DOCUMENT_ARCHIVED", List.of());
  }

  public Page<ReviewQueueItem> reviewQueue(Long reviewerId, int page) {
    User actor = user(reviewerId);
    accounts.requireManagerOrAbove(actor);
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "createdAt", "id"),
        pageable ->
            versions.findReviewQueue(
                DocumentStatus.PENDING_REVIEW,
                departmentId(actor),
                accounts.assignablePositions(actor),
                actor.getId(),
                accounts.isAdmin(actor),
                clearances(actor),
                pageable));
  }

  @Transactional
  public DocumentVersion reviewDetail(Long versionId, Long reviewerId) {
    User actor = user(reviewerId);
    DocumentVersion version = version(versionId);
    access.requireReview(actor, version, false);
    requirePending(version, DocumentStatus.PENDING_REVIEW);
    activities.save(new DocumentActivity(actor, version, DocumentAction.REVIEW_OPENED));
    return version;
  }

  @Transactional
  public void approve(Long versionId, Long reviewerId) {
    review(versionId, reviewerId, true, false, "");
  }

  @Transactional
  public void reject(Long versionId, Long reviewerId, String reason) {
    review(versionId, reviewerId, false, false, reason);
  }

  @Transactional
  public void review(
      Long versionId, Long actorId, boolean approved, boolean security, String reason) {
    User actor = user(actorId);
    DocumentVersion version = lockedVersion(versionId);
    access.requireReview(actor, version, security);
    requirePending(
        version,
        security ? DocumentStatus.PENDING_SECURITY_APPROVAL : DocumentStatus.PENDING_REVIEW);
    if (!approved && (reason == null || reason.isBlank()))
      throw new IllegalArgumentException("반려 사유를 입력해주세요.");
    if (security) version.securityDecision(actor, approved);
    else if (!approved) version.reject(actor);
    else if (version.getDocument().getClassification() != SecurityClassification.INTERNAL)
      version.sendToSecurity(actor);
    else version.approve(actor);
    notes.save(
        new ReviewNote(
            version,
            actor,
            (security ? "SECURITY_" : "") + (approved ? "APPROVED" : "REJECTED"),
            reason));
    activities.save(
        new DocumentActivity(
            actor, version, approved ? DocumentAction.APPROVED : DocumentAction.REJECTED));
    var recipients = new ArrayList<Long>();
    recipients.add(version.getDocument().getAuthor().getId());
    if (version.getEditorId() != null) recipients.add(version.getEditorId());
    if (version.getStatus() == DocumentStatus.PENDING_SECURITY_APPROVAL
        && version.getDocument().getProject() != null)
      recipients.addAll(
          members.recipients(
              version.getDocument().getProject().getId(), List.of(ProjectRole.SECURITY)));
    changed(version.getDocument());
    event(actor, version, security ? "DOCUMENT_SECURITY_DECIDED" : "DOCUMENT_REVIEWED", recipients);
  }

  public Page<ReviewNote> comments(Long versionId, Long actorId, int page) {
    DocumentVersion version = version(versionId);
    access.requireRead(user(actorId), version);
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "id"),
        pageable -> notes.findByVersionId(versionId, pageable));
  }

  @Transactional
  public void comment(Long versionId, Long actorId, String content) {
    User actor = user(actorId);
    DocumentVersion version = lockedVersion(versionId);
    access.requireRead(actor, version);
    if (content == null || content.isBlank()) throw new IllegalArgumentException("검토 의견을 입력해주세요.");
    notes.save(new ReviewNote(version, actor, "COMMENT", content));
    event(actor, version, "DOCUMENT_COMMENTED", List.of(version.getDocument().getAuthor().getId()));
  }

  public Actions actions(Long versionId, Long actorId) {
    User actor = user(actorId);
    DocumentVersion version = version(versionId);
    access.requireRead(actor, version);
    return actions(actor, version);
  }

  public Actions actions(User actor, DocumentVersion version) {
    Long actorId = actor.getId();
    Document document = version.getDocument();
    boolean open =
        !document.getDepartment().isClosed()
            && (document.getProject() == null
                || document.getProject().getStatus() != ProjectStatus.CLOSED);
    boolean current =
        Objects.equals(versions.latestId(document.getId()).orElse(null), version.getId());
    boolean independent =
        !version.writtenBy(actorId) && !Objects.equals(document.getAuthor().getId(), actorId);
    boolean edit = open && current && access.canEdit(actor, document);
    return new Actions(
        edit && version.getStatus() == DocumentStatus.DRAFT,
        edit
            && (version.getStatus() == DocumentStatus.APPROVED
                || version.getStatus() == DocumentStatus.REJECTED
                || version.getStatus() == DocumentStatus.ARCHIVED),
        edit
            && !document.isArchived()
            && version.getStatus() != DocumentStatus.PENDING_REVIEW
            && version.getStatus() != DocumentStatus.PENDING_SECURITY_APPROVAL,
        open
            && current
            && independent
            && access.canReview(actor, document)
            && version.getStatus() == DocumentStatus.PENDING_REVIEW,
        open
            && current
            && independent
            && access.canSecure(actor, document)
            && version.getStatus() == DocumentStatus.PENDING_SECURITY_APPROVAL
            && (version.getReviewedBy() == null
                || !Objects.equals(actorId, version.getReviewedBy().getId())));
  }

  public record Actions(
      boolean edit, boolean newVersion, boolean archive, boolean review, boolean security) {}

  private User user(Long id) {
    if (id == null) throw new AccessDeniedException("사용자 정보를 확인할 수 없습니다.");
    User user =
        users.findById(id).orElseThrow(() -> new AccessDeniedException("사용자 정보를 확인할 수 없습니다."));
    accounts.requireActive(user);
    return user;
  }

  private Document document(Long id) {
    return documents
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));
  }

  private DocumentVersion version(Long id) {
    return versions
        .findForDisplayById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
  }

  private DocumentVersion latest(Document document) {
    return versions
        .findFirstByDocumentOrderByVersionNumberDesc(document)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
  }

  private DocumentVersion lockedVersion(Long id) {
    Long documentId =
        versions
            .documentId(id)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서 버전을 찾을 수 없습니다."));
    locks.lock(documentId);
    return version(id);
  }

  private void requireLatest(DocumentVersion version) {
    if (!Objects.equals(latest(version.getDocument()).getId(), version.getId()))
      conflict("최신 버전에서 처리해주세요.");
  }

  private void requirePending(DocumentVersion version, DocumentStatus expected) {
    if (version.getStatus() != expected || version.getDocument().isArchived())
      conflict("이미 처리되었거나 검토 대기 상태가 아닙니다.");
    requireLatest(version);
  }

  private void changed(Document document) {
    if (document.getProject() != null) document.getProject().markChanged();
  }

  private void event(User actor, DocumentVersion version, String action, List<Long> recipients) {
    Document d = version.getDocument();
    events.publishEvent(
        AuditEvent.of(
                actor,
                action,
                "DOCUMENT_VERSION",
                version.getId(),
                d.getProject() == null ? null : d.getProject().getId(),
                "문서 #"
                    + d.getId()
                    + " v"
                    + version.getVersionNumber()
                    + " · "
                    + version.getStatus())
            .notify(recipients, "/document/versions/" + version.getId()));
  }

  private List<Long> reviewers(Document document) {
    if (document.getProject() != null)
      return members.recipients(
          document.getProject().getId(), List.of(ProjectRole.OWNER, ProjectRole.ENGINEERING));
    return users.reviewers(
        document.getDepartment().getId(),
        Arrays.stream(Position.values())
            .filter(
                p -> p.isAtLeast(Position.MANAGER) && p.isAtLeast(document.getRequiredPosition()))
            .toList(),
        Arrays.stream(SecurityClassification.values())
            .filter(c -> c.permits(document.getClassification()))
            .toList());
  }

  private static Long departmentId(User user) {
    return user.getDepartment() == null ? null : user.getDepartment().getId();
  }

  private boolean securityReviewer(User actor) {
    return accounts.isAdmin(actor)
        || actor.getPosition().isAtLeast(Position.MANAGER)
            && actor.getClearance() != SecurityClassification.INTERNAL;
  }

  private List<SecurityClassification> clearances(User user) {
    return Arrays.stream(SecurityClassification.values())
        .filter(c -> accounts.isAdmin(user) || user.getClearance().permits(c))
        .toList();
  }

  private static void conflict(String message) {
    throw new ResponseStatusException(HttpStatus.CONFLICT, message);
  }
}
