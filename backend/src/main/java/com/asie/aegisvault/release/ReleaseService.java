package com.asie.aegisvault.release;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.*;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.project.*;
import com.asie.aegisvault.security.*;
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
  private final ApplicationEventPublisher events;

  public ReleaseGate.Decision gate(String actor, Long projectId) {
    return gate.inspect(access.read(actors.get(actor), projectId), false);
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
    if (!recipients.existsByReleaseIdAndUserId(id, user.getId()))
      access.read(user, release.getProjectId());
    documentAccess.requireScopes(user,documents.forVersions(items.findByReleaseIdOrderById(id).stream().map(ReleaseItem::getVersionId).toList()));
    return release;
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
    documentAccess.requireScopes(user,publishedDocuments);
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
