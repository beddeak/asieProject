package com.asie.aegisvault.notification;

import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.security.CurrentUser;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class NotificationController {
  private final NotificationService service;
  private final DocumentService documents;
  private final CurrentUser actors;
  private final com.asie.aegisvault.security.UserAccessPolicy policy;

  @GetMapping("/notifications")
  public String list(
      Principal actor,
      @RequestParam(defaultValue = "true") boolean unread,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("notifications", service.list(actor.getName(), unread, page));
    model.addAttribute("unread", unread);
    return "notifications";
  }

  @PostMapping("/notifications/{id}/read")
  public String read(Principal actor, @PathVariable Long id) {
    return "redirect:" + service.read(actor.getName(), id);
  }

  @PostMapping("/notifications/read-all")
  public String readAll(Principal actor) {
    service.readAll(actor.getName());
    return "redirect:/notifications";
  }

  @GetMapping("/tasks")
  public String tasks(
      Principal actor,
      @RequestParam(defaultValue = "drafts") String tab,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    var user = actors.get(actor.getName());
    if (tab.equals("review") || tab.equals("security")) policy.requireDocumentReviewer(user);
    DocumentFilter filter = new DocumentFilter();
    switch (tab) {
      case "drafts" -> {
        filter.setMine(true);
        filter.setStatus(DocumentStatus.DRAFT);
      }
      case "review" -> filter.setReviewOnly(true);
      case "security" -> filter.setSecurityOnly(true);
      case "rejected" -> {
        filter.setMine(true);
        filter.setStatus(DocumentStatus.REJECTED);
      }
      default -> throw new IllegalArgumentException("업무 탭을 찾을 수 없습니다.");
    }
    model.addAttribute("documents", documents.documentList(user.getId(), filter, page).documents());
    model.addAttribute("tab", tab);
    return "tasks";
  }
}
