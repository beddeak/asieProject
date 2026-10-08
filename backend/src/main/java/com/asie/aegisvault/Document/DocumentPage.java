package com.asie.aegisvault.Document;

import com.asie.aegisvault.attachment.AttachmentRepository;
import com.asie.aegisvault.common.PageQueries;
import com.asie.aegisvault.security.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;

@Component
@RequiredArgsConstructor
public class DocumentPage {
  private final DocumentService documents;
  private final AttachmentRepository attachments;
  private final ReviewNoteRepository notes;
  private final CurrentUser actors;
  private final DocumentAccess access;

  @Transactional(readOnly = true)
  public void fill(
      Model model, DocumentVersion version, String actor, int commentPage, int filePage) {
    var user = actors.get(actor);
    access.requireRead(user, version);
    model.addAttribute("documentVersion", version);
    model.addAttribute("actions", documents.actions(user, version));
    model.addAttribute(
        "comments",
        PageQueries.fetch(
            commentPage,
            20,
            Sort.by(Sort.Direction.DESC, "id"),
            p -> notes.findByVersionId(version.getId(), p)));
    model.addAttribute(
        "attachments",
        PageQueries.fetch(
            filePage, 20, Sort.by("id"), p -> attachments.findByVersionId(version.getId(), p)));
    model.addAttribute(
        "watermark",
        actor
            + " · "
            + Instant.now()
            + " · DOC #"
            + version.getDocument().getId()
            + " v"
            + version.getVersionNumber());
  }
}
