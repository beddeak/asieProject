package com.asie.aegisvault.notice;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/announcements")
public class AnnouncementController {
  private final AnnouncementService service;

  @GetMapping
  public String list(
      Principal actor,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("announcements", service.list(actor.getName(), keyword, page));
    model.addAttribute("keyword", keyword);
    return "announcements";
  }

  @GetMapping("/{id}")
  public String detail(Principal actor, @PathVariable Long id, Model model) {
    model.addAttribute("notice", service.detail(actor.getName(), id));
    return "announcementdetail";
  }

  @PostMapping
  public String create(Principal actor, @RequestParam String title, @RequestParam String content) {
    return "redirect:/announcements/" + service.create(actor.getName(), title, content);
  }

  @PostMapping("/{id}")
  public String update(
      Principal actor,
      @PathVariable Long id,
      @RequestParam Long revision,
      @RequestParam String title,
      @RequestParam String content) {
    service.update(actor.getName(), id, revision, title, content);
    return "redirect:/announcements/" + id;
  }

  @PostMapping("/{id}/delete")
  public String delete(Principal actor, @PathVariable Long id, @RequestParam Long revision) {
    service.delete(actor.getName(), id, revision);
    return "redirect:/announcements";
  }
}
