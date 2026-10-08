package com.asie.aegisvault.attachment;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequiredArgsConstructor
public class AttachmentController {
  private final AttachmentService service;
  private final FileStore store;

  @PostMapping("/document/versions/{versionId}/files")
  public String upload(
      Principal actor, @PathVariable Long versionId, @RequestParam MultipartFile file) {
    service.upload(actor.getName(), versionId, file);
    return "redirect:/document/versions/" + versionId;
  }

  @PostMapping("/files/{id}/delete")
  public String delete(Principal actor, @PathVariable Long id, @RequestParam Long versionId) {
    service.delete(actor.getName(), id);
    return "redirect:/document/versions/" + versionId;
  }

  @GetMapping("/files/{id}")
  public ResponseEntity<FileSystemResource> download(Principal actor, @PathVariable Long id) {
    var file = service.download(actor.getName(), id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename(file.getFilename(), StandardCharsets.UTF_8)
                .build()
                .toString())
        .header("X-Content-Type-Options", "nosniff")
        .cacheControl(CacheControl.noStore())
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(file.getSize())
        .body(new FileSystemResource(store.path(file.getStorageKey())));
  }
}
