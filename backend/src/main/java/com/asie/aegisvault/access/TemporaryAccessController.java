package com.asie.aegisvault.access;

import java.security.Principal;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/access")
public class TemporaryAccessController {
  private final TemporaryAccessService service;

  @GetMapping
  public String mine(Principal actor, @RequestParam(defaultValue = "0") int page, Model model) {
    model.addAttribute("requests", service.mine(actor.getName(), page));
    return "access";
  }

  @GetMapping("/document/{documentId}")
  public String requests(
      Principal actor,
      @PathVariable Long documentId,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("requests", service.requests(actor.getName(), documentId, page));
    model.addAttribute("documentId", documentId);
    return "access";
  }

  @PostMapping
  public String request(
      Principal actor,
      @RequestParam Long documentId,
      @RequestParam String reason,
      @RequestParam String until) {
    service.request(
        actor.getName(), documentId, reason, LocalDateTime.parse(until).toInstant(ZoneOffset.UTC));
    return "redirect:/access";
  }

  @PostMapping("/{id}/decide")
  public String decide(
      Principal actor,
      @PathVariable Long id,
      @RequestParam boolean approved,
      @RequestParam(required = false) String until,
      @RequestParam String reason,
      @RequestParam Long documentId) {
    service.decide(
        actor.getName(),
        id,
        approved,
        until == null || until.isBlank()
            ? null
            : LocalDateTime.parse(until).toInstant(ZoneOffset.UTC),
        reason);
    return "redirect:/access/document/" + documentId;
  }

  @PostMapping("/{id}/revoke")
  public String revoke(Principal actor, @PathVariable Long id, @RequestParam String reason) {
    service.revoke(actor.getName(), id, reason);
    return "redirect:/access";
  }
}
