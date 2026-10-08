package com.asie.aegisvault.Department;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/departments")
public class DepartmentAdminController {
  private final DepartmentService service;
  private final DepartmentLifecycle lifecycle;
  private final DepartmentRepository departments;

  @GetMapping
  public String list(Principal actor, Model model) {
    service.requireAdmin(actor.getName());
    model.addAttribute(
        "departments", departments.findAll(org.springframework.data.domain.Sort.by("name", "id")));
    return "admindepartments";
  }

  @PostMapping("/{id}")
  public String rename(
      Principal actor,
      @PathVariable Long id,
      @RequestParam Long revision,
      @RequestParam String name,
      @RequestParam String description) {
    lifecycle.rename(actor.getName(), id, revision, name, description);
    return "redirect:/admin/departments";
  }

  @PostMapping("/{id}/close")
  public String close(
      Principal actor,
      @PathVariable Long id,
      @RequestParam Long revision,
      @RequestParam(required = false) Long targetId) {
    lifecycle.close(actor.getName(), id, targetId, revision);
    return "redirect:/admin/departments";
  }
}
