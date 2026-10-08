package com.asie.aegisvault.attachment;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.security.*;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttachmentService {
  private final CurrentUser actors;
  private final DocumentAccess access;
  private final DocumentLocks locks;
  private final DocumentVersionRepository versions;
  private final AttachmentRepository files;
  private final FileStore store;
  private final ApplicationEventPublisher events;

  public Page<Attachment> list(String actor, Long versionId, int page) {
    access.requireRead(actors.get(actor), version(versionId));
    return PageQueries.fetch(page, 20, Sort.by("id"), p -> files.findByVersionId(versionId, p));
  }

  @Transactional
  public Long upload(String actor, Long versionId, MultipartFile file) {
    User user = actors.get(actor);
    DocumentVersion version = editable(user, versionId);
    FileStore.Stored stored = store.put(file);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) store.delete(stored.key());
          }
        });
    Attachment attached =
        files.save(
            new Attachment(
                versionId,
                stored.key(),
                stored.name(),
                stored.type(),
                stored.size(),
                stored.hash(),
                user.getId()));
    changed(version);
    event(user, version, "ATTACHMENT_UPLOADED", attached.getId());
    return attached.getId();
  }

  @Transactional
  public void delete(String actor, Long id) {
    Attachment file = file(id);
    User user = actors.get(actor);
    DocumentVersion version = editable(user, file.getVersionId());
    files.delete(file);
    changed(version);
    event(user, version, "ATTACHMENT_REMOVED", id);
    // Blobs are immutable and may be shared by previous versions. Retention cleanup handles
    // unreferenced blobs.
  }

  @Transactional
  public Attachment download(String actor, Long id) {
    Attachment file = file(id);
    User user = actors.get(actor);
    DocumentVersion version = version(file.getVersionId());
    access.requireRead(user, version);
    if (!store.intact(file))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "첨부파일 무결성 확인에 실패했습니다. 관리자에게 문의해주세요.");
    event(user, version, "ATTACHMENT_DOWNLOADED", id);
    return file;
  }

  private DocumentVersion editable(User actor, Long id) {
    Long documentId =
        versions
            .documentId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    Document document = locks.lock(documentId);
    access.requireEdit(actor, document);
    DocumentVersion version = version(id);
    if (document.isArchived()
        || version.getStatus() != DocumentStatus.DRAFT
        || !Objects.equals(
            versions.findFirstByDocumentOrderByVersionNumberDesc(document).orElseThrow().getId(),
            id))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "최신 초안에서만 첨부파일을 변경할 수 있습니다.");
    return version;
  }

  private void changed(DocumentVersion version) {
    if (version.getDocument().getProject() != null)
      version.getDocument().getProject().markChanged();
  }

  private Attachment file(Long id) {
    return files
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "첨부파일을 찾을 수 없습니다."));
  }

  private DocumentVersion version(Long id) {
    return versions
        .findForDisplayById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "버전을 찾을 수 없습니다."));
  }

  private void event(User actor, DocumentVersion v, String action, Long id) {
    events.publishEvent(
        AuditEvent.of(
            actor,
            action,
            "ATTACHMENT",
            id,
            v.getDocument().getProject() == null ? null : v.getDocument().getProject().getId(),
            "문서 #" + v.getDocument().getId() + " v" + v.getVersionNumber() + " 첨부 #" + id));
  }
}
