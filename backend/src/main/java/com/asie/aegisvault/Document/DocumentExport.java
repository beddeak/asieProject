package com.asie.aegisvault.Document;

import com.asie.aegisvault.audit.AuditEvent;
import com.asie.aegisvault.security.*;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
public class DocumentExport {
  private final DocumentVersionRepository versions;
  private final DocumentAccess access;
  private final CurrentUser actors;
  private final ApplicationEventPublisher events;

  @Transactional
  public String html(String actor, Long versionId) {
    var user = actors.get(actor);
    var version =
        versions
            .findForDisplayById(versionId)
            .orElseThrow(
                () ->
                    new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));
    access.requireRead(user, version);
    var document = version.getDocument();
    String stamp =
        actor
            + " · "
            + Instant.now()
            + " · DOC #"
            + document.getId()
            + " v"
            + version.getVersionNumber()
            + " · "
            + document.getClassification().getLabel();
    events.publishEvent(
        AuditEvent.of(
            user,
            "DOCUMENT_EXPORTED",
            "DOCUMENT_VERSION",
            versionId,
            document.getProject() == null ? null : document.getProject().getId(),
            "워터마크가 포함된 문서 내보내기"));
    return "<!doctype html><html lang=ko><meta charset=utf-8><title>"
        + escape(version.getTitle())
        + "</title><style>body{font:16px/1.8"
        + " sans-serif;margin:3rem;overflow-wrap:anywhere}pre{white-space:pre-wrap;font:inherit}.watermark{position:fixed;top:45%;left:5%;right:5%;transform:rotate(-24deg);opacity:.13;font-size:28px;pointer-events:none}footer{border-top:1px"
        + " solid #aaa;font-size:12px}@media print{.watermark{position:fixed}}</style><div"
        + " class=watermark>"
        + escape(stamp)
        + "</div><h1>"
        + escape(version.getTitle())
        + "</h1><p>"
        + escape(stamp)
        + "</p><p>상태: "
        + version.getStatus()
        + "</p><pre>"
        + escape(version.getContent())
        + "</pre><footer>"
        + escape(stamp)
        + " · AegisVault</footer></html>";
  }

  private String escape(String text) {
    return HtmlUtils.htmlEscape(text);
  }
}
