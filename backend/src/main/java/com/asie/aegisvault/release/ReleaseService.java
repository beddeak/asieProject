package com.asie.aegisvault.release;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.security.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
public class ReleaseService {
  private final CurrentUser actors;
  private final ProjectAccess access;
  private final ProjectRepository projects;
  private final ReleaseGate gate;
  private final ReleaseRepository releases;
  private final ReleaseItemRepository items;
  private final ReleaseRecipientRepository recipients;
  private final UserRepository users;
  private final DocumentRepository documents;
  private final DocumentAccess documentAccess;
  private final DocumentService documentService;
  private final com.asie.aegisvault.attachment.AttachmentRepository attachments;
  private final ApplicationEventPublisher events;

  public ReleaseGate.Decision gate(String actor, Long projectId) {
    User user = actors.get(actor);
    Project project = access.read(user, projectId);
    documentAccess.requireScopes(user, documents.findByProjectId(projectId));
    ReleaseGate.Decision decision = gate.inspect(project, false);
    Set<Long> comparedVersions =
        Stream.of(decision.qualityReview(), decision.securityReview())
            .flatMap(review -> review.changes().stream())
            .flatMap(change -> Stream.of(change.previousVersionId(), change.currentVersionId()))
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    Map<Long, String> readableTitles =
        documentService.readableVersionTitles(user.getId(), comparedVersions);
    return new ReleaseGate.Decision(
        decision.issues(),
        decision.snapshot(),
        decision.qualityRunId(),
        decision.securityAssessmentId(),
        decision.qualityReview().forReader(readableTitles),
        decision.securityReview().forReader(readableTitles));
  }

  public Page<ReleaseSummary> list(String actor, Long projectId, int page) {
    access.read(actors.get(actor), projectId);
    return PageQueries.fetch(
        page,
        20,
        Sort.by(Sort.Direction.DESC, "releaseNumber"),
        p -> releases.summaries(projectId, p));
  }

  public Release detail(String actor, Long id) {
    Release release = release(id);
    User user = actors.get(actor);
    requireDetail(user, release, items.findByReleaseIdOrderById(id));
    return release;
  }

  public ReleaseDetail detailView(String actor, Long id, int page) {
    User user = actors.get(actor);
    Release release = release(id);
    var fixedItems = items.findByReleaseIdOrderById(id);
    requireDetail(user, release, fixedItems);
    var versionIds = fixedItems.stream().map(ReleaseItem::getVersionId).toList();
    var summaries =
        documentService.readableVersions(user.getId(), versionIds).stream()
            .collect(Collectors.toMap(v -> v.versionId(), v -> v));
    if (summaries.size() != versionIds.size())
      throw new AccessDeniedException("배포 문서의 현재 열람 권한을 확인해주세요.");
    var filesByVersion =
        attachments.manifest(versionIds).stream()
            .collect(Collectors.groupingBy(f -> f.getVersionId()));
    var manifest =
        versionIds.stream()
            .map(
                versionId ->
                    new ReleaseDetail.ReleasedDocument(
                        summaries.get(versionId),
                        ReleasePackagePaths.document(versionId),
                        filesByVersion.getOrDefault(versionId, List.of()).stream()
                            .map(
                                file ->
                                    new ReleaseDetail.ReleasedFile(
                                        file.getId(),
                                        file.getFilename(),
                                        file.getSize(),
                                        file.getSha256(),
                                        ReleasePackagePaths.attachment(
                                            versionId, file.getId(), file.getFilename())))
                            .toList()))
            .toList();
    boolean designated = recipients.existsByReleaseIdAndUserId(id, user.getId());
    return new ReleaseDetail(
        release,
        manifest,
        PageQueries.fetch(page, 20, Sort.by("id"), p -> recipients.findByReleaseId(id, p)),
        release.getRecalledAt() == null && (user.getPosition().isAdmin() || designated),
        release.getRecalledAt() == null
            && access.hasRole(user, release.getProjectId(), ProjectRole.OWNER),
        access.hasRole(user, release.getProjectId(), ProjectRole.values()));
  }

  private void requireDetail(User user, Release release, List<ReleaseItem> fixedItems) {
    if (!recipients.existsByReleaseIdAndUserId(release.getId(), user.getId()))
      access.read(user, release.getProjectId());
    documentAccess.requireScopes(
        user, documents.forVersions(fixedItems.stream().map(ReleaseItem::getVersionId).toList()));
  }

  public List<ReleaseItem> items(String actor, Long id) {
    detail(actor, id);
    return items.findByReleaseIdOrderById(id);
  }

  public Page<ReleaseRecipient> recipients(String actor, Long id, int page) {
    detail(actor, id);
    return PageQueries.fetch(page, 20, Sort.by("id"), p -> recipients.findByReleaseId(id, p));
  }

  @Transactional
  public Long publish(
      String actor,
      Long projectId,
      String expectedFingerprint,
      Set<String> recipientNames,
      String notes) {
    User user = actors.get(actor);
    Project project = access.lock(user, projectId, ProjectRole.OWNER);
    if (project.getStatus() != ProjectStatus.REVIEW)
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "프로젝트가 배포 검토 상태가 아닙니다. 현재 상태를 확인해주세요.");
    ReleaseGate.Decision decision = gate.inspect(project, true);
    if (!Objects.equals(expectedFingerprint, decision.snapshot().fingerprint()))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "배포 구성이 변경되었습니다. 다시 확인해주세요.");
    if (!decision.ready())
      throw new IllegalArgumentException(String.join(" / ", decision.blockers()));
    if (recipientNames == null || recipientNames.isEmpty())
      throw new IllegalArgumentException("배포 수신자를 선택해주세요.");
    List<User> targets = users.findByNicknameIn(recipientNames);
    if (targets.size() != recipientNames.size())
      throw new IllegalArgumentException("수신자를 찾을 수 없습니다.");
    var publishedDocuments =
        documents.forVersions(
            decision.snapshot().versions().stream().map(v -> v.versionId()).toList());
    documentAccess.requireScopes(user, publishedDocuments);
    for (User target : targets) documentAccess.requireScopes(target, publishedDocuments);
    int number =
        releases
            .findFirstByProjectIdOrderByReleaseNumberDesc(projectId)
            .map(r -> r.getReleaseNumber() + 1)
            .orElse(1);
    Release release =
        releases.save(
            new Release(
                projectId,
                number,
                decision.snapshot().fingerprint(),
                decision.qualityRunId(),
                decision.securityAssessmentId(),
                user.getNickname(),
                notes));
    items.saveAll(
        decision.snapshot().versions().stream()
            .map(v -> new ReleaseItem(release.getId(), v.versionId()))
            .toList());
    recipients.saveAll(
        targets.stream()
            .map(t -> new ReleaseRecipient(release.getId(), t.getId(), t.getNickname()))
            .toList());
    project.release();
    events.publishEvent(
        AuditEvent.of(
                user,
                "RELEASE_PUBLISHED",
                "RELEASE",
                release.getId(),
                projectId,
                "배포 #" + number + " 확정")
            .notify(targets.stream().map(User::getId).toList(), "/releases/" + release.getId()));
    return release.getId();
  }

  @Transactional
  public void recall(String actor, Long id, String reason) {
    User user = actors.get(actor);
    Release release = release(id);
    access.requireMember(user, release.getProjectId());
    Project project = projects.lockById(release.getProjectId()).orElseThrow();
    access.requireRole(user, project.getId(), ProjectRole.OWNER);
    release.recall(user.getNickname(), reason);
    if (project.getStatus() != ProjectStatus.CLOSED) project.markChanged();
    events.publishEvent(
        AuditEvent.of(
                user,
                "RELEASE_RECALLED",
                "RELEASE",
                id,
                project.getId(),
                "배포 #" + release.getReleaseNumber() + " 회수")
            .notify(
                recipients.findByReleaseId(id).stream().map(ReleaseRecipient::getUserId).toList(),
                "/releases/" + id));
  }

  public void requireAvailable(String actor, Long id) {
    User user = actors.get(actor);
    Release release = release(id);
    if (release.getRecalledAt() != null)
      throw new ResponseStatusException(HttpStatus.GONE, "회수된 배포는 다운로드할 수 없습니다.");
    if (!user.getPosition().isAdmin() && !recipients.existsByReleaseIdAndUserId(id, user.getId()))
      throw new AccessDeniedException("지정된 배포 수신자만 다운로드할 수 있습니다.");
  }

  @Transactional
  public List<Long> downloadVersions(String actor, Long id) {
    User user = actors.get(actor);
    Release release = release(id);
    if (release.getRecalledAt() != null)
      throw new ResponseStatusException(HttpStatus.GONE, "회수된 배포는 다운로드할 수 없습니다.");
    if (!user.getPosition().isAdmin() && !recipients.existsByReleaseIdAndUserId(id, user.getId()))
      throw new AccessDeniedException("지정된 배포 수신자만 다운로드할 수 있습니다.");
    List<Long> versionIds =
        items.findByReleaseIdOrderById(id).stream().map(ReleaseItem::getVersionId).toList();
    documentAccess.requireScopes(user, documents.forVersions(versionIds));
    events.publishEvent(
        AuditEvent.of(
            user,
            "RELEASE_DOWNLOAD_REQUESTED",
            "RELEASE",
            id,
            release.getProjectId(),
            "배포 #" + release.getReleaseNumber() + " 다운로드 요청"));
    return versionIds;
  }

  private Release release(Long id) {
    return releases
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "배포를 찾을 수 없습니다."));
  }
}
