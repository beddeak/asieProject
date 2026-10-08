package com.asie.aegisvault.audit;

import com.asie.aegisvault.admin.AdminUserService;
import com.asie.aegisvault.common.PageQueries;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/audit")
public class AuditController {
  private final AdminUserService admins;
  private final AuditRecordRepository records;
  private final AuditChain chain;

  @GetMapping
  public String list(
      Principal principal,
      @RequestParam(required = false) String actor,
      @RequestParam(required = false) String action,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    admins.requireAdmin(principal.getName());
    model.addAttribute(
        "records",
        PageQueries.fetch(
            page,
            30,
            Sort.by(Sort.Direction.DESC, "id"),
            p -> records.search(null, blank(actor), blank(action), p)));
    model.addAttribute("actor", actor);
    model.addAttribute("action", action);
    return "audit";
  }

  @PostMapping("/verify")
  public String verify(Principal actor, RedirectAttributes redirect) {
    admins.requireAdmin(actor.getName());
    redirect.addFlashAttribute("verification", chain.verify());
    return "redirect:/admin/audit";
  }

  private String blank(String text) {
    return text == null || text.isBlank() ? null : text;
  }
}
