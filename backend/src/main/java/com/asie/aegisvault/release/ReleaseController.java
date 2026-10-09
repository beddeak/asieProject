package com.asie.aegisvault.release;

import com.asie.aegisvault.Document.DocumentExport;
import com.asie.aegisvault.attachment.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.Principal;
import java.util.zip.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Controller
@RequiredArgsConstructor
public class ReleaseController {
  private final ReleaseService service;
  private final DocumentExport export;
  private final AttachmentRepository attachments;
  private final AttachmentService files;
  private final FileStore store;

  @GetMapping("/releases/{id}")
  public String detail(
      Principal actor,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    var detail = service.detailView(actor.getName(), id, page);
    model.addAttribute("release", detail.release());
    model.addAttribute("manifest", detail.documents());
    model.addAttribute("recipients", detail.recipients());
    model.addAttribute("releaseActions", detail);
    return "releasedetail";
  }

  @PostMapping("/releases/{id}/recall")
  public String recall(Principal actor, @PathVariable Long id, @RequestParam String reason) {
    service.recall(actor.getName(), id, reason);
    return "redirect:/releases/" + id;
  }

  @GetMapping("/document/versions/{id}/export")
  public ResponseEntity<byte[]> document(Principal actor, @PathVariable Long id) {
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"document-" + id + ".html\"")
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.TEXT_HTML)
        .body(export.html(actor.getName(), id).getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/releases/{id}/download")
  public ResponseEntity<StreamingResponseBody> download(Principal actor, @PathVariable Long id) {
    String name = actor.getName();
    var versions = service.downloadVersions(name, id);
    StreamingResponseBody body =
        output -> {
          try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (Long versionId : versions) {
              // Re-check authorization and recall on each document; a partial
              // interrupted ZIP is
              // not a completed delivery.
              service.requireAvailable(name, id);
              zip.putNextEntry(new ZipEntry(ReleasePackagePaths.document(versionId)));
              zip.write(export.html(name, versionId).getBytes(StandardCharsets.UTF_8));
              zip.closeEntry();
              for (Attachment attached : attachments.findByVersionIdOrderById(versionId)) {
                Attachment checked = files.download(name, attached.getId());
                zip.putNextEntry(
                    new ZipEntry(
                        ReleasePackagePaths.attachment(
                            versionId, checked.getId(), checked.getFilename())));
                Files.copy(store.path(checked.getStorageKey()), zip);
                zip.closeEntry();
              }
            }
          }
        };
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"release-" + id + ".zip\"")
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.parseMediaType("application/zip"))
        .body(body);
  }
}
