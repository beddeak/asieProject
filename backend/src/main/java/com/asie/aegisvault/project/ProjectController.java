package com.asie.aegisvault.project;

import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.Document.*;
import com.asie.aegisvault.security.CurrentUser;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects")
public class ProjectController {
  private final ProjectService service;
  private final DepartmentRepository departments;
  private final DocumentService documents;
  private final CurrentUser actors;
  private final ProjectProgressService progress;

  @GetMapping
  public String list(
      Principal actor,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) ProjectStatus status,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute(
        "projects", service.list(actor.getName(), keyword, status, departmentId, page));
    model.addAttribute("keyword", keyword);
    model.addAttribute("selectedStatus", status);
    model.addAttribute("statuses", ProjectStatus.values());
    model.addAttribute("canCreateProject", service.canCreate(actor.getName()));
    return "projects";
  }

  @GetMapping("/new")
  public String createForm(Principal actor, @ModelAttribute("form") ProjectForm form, Model model) {
    service.requireCreator(actor.getName());
    model.addAttribute(
        "departments", departments.findAll(org.springframework.data.domain.Sort.by("name")));
    return "projectform";
  }

  @PostMapping
  public String create(
      Principal actor,
      @Valid @ModelAttribute("form") ProjectForm form,
      BindingResult errors,
      Model model) {
    if (errors.hasErrors()) return createForm(actor, form, model);
    return "redirect:/projects/" + service.create(actor.getName(), form);
  }

  @GetMapping("/{id}")
  public String detail(
      Principal actor,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("project", service.detail(actor.getName(), id));
    model.addAttribute("projectProgress", progress.view(actor.getName(), id));
    DocumentFilter filter = new DocumentFilter();
    filter.setProjectId(id);
    model.addAttribute(
        "documents",
        documents.documentList(actors.get(actor.getName()).getId(), filter, page).documents());
    model.addAttribute("categories", DocumentCategory.values());
    return "projectdetail";
  }

  @GetMapping("/{id}/guide")
  public String guide(Principal actor, @PathVariable Long id, Model model) {
    model.addAttribute("project", service.detail(actor.getName(), id));
    model.addAttribute("projectProgress", progress.view(actor.getName(), id));
    return "projectguide";
  }

  @GetMapping("/{id}/edit")
  public String edit(Principal actor, @PathVariable Long id, Model model) {
    var detail = service.detail(actor.getName(), id);
    if (!detail.canManage())
      throw new org.springframework.security.access.AccessDeniedException("프로젝트 책임자만 수정할 수 있습니다.");
    ProjectForm form = new ProjectForm();
    form.setName(detail.name());
    form.setDescription(detail.description());
    form.setTargetDate(detail.targetDate());
    form.setRevision(detail.revision());
    model.addAttribute("form", form);
    model.addAttribute("project", detail);
    model.addAttribute("projectProgress", progress.view(actor.getName(), id));
    return "projectform";
  }

  @PostMapping("/{id}/edit")
  public String update(
      Principal actor,
      @PathVariable Long id,
      @Valid @ModelAttribute("form") ProjectForm form,
      BindingResult errors,
      Model model) {
    if (errors.hasErrors()) {
      model.addAttribute("project", service.detail(actor.getName(), id));
      model.addAttribute("projectProgress", progress.view(actor.getName(), id));
      return "projectform";
    }
    service.update(actor.getName(), id, form);
    return "redirect:/projects/" + id;
  }

  @PostMapping("/{id}/status")
  public String status(Principal actor, @PathVariable Long id, @RequestParam String action) {
    service.changeStatus(actor.getName(), id, action);
    return "redirect:/projects/" + id;
  }

  @GetMapping("/{id}/members")
  public String members(
      Principal actor,
      @PathVariable Long id,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int candidatePage,
      Model model) {
    var project = service.detail(actor.getName(), id);
    model.addAttribute("project", project);
    model.addAttribute("projectProgress", progress.view(actor.getName(), id));
    model.addAttribute("members", service.members(actor.getName(), id, page));
    model.addAttribute("roles", ProjectRole.values());
    model.addAttribute("keyword", keyword);
    if (project.canManage())
      model.addAttribute(
          "candidates", service.candidates(actor.getName(), id, keyword, candidatePage));
    return "projectmembers";
  }

  @PostMapping("/{id}/members")
  public String assign(
      Principal actor,
      @PathVariable Long id,
      @RequestParam Long userId,
      @RequestParam ProjectRole role) {
    service.assignMember(actor.getName(), id, userId, role);
    return "redirect:/projects/" + id + "/members";
  }

  @PostMapping("/{id}/members/{userId}/remove")
  public String remove(Principal actor, @PathVariable Long id, @PathVariable Long userId) {
    service.removeMember(actor.getName(), id, userId);
    return "redirect:/projects/" + id + "/members";
  }

  @PostMapping("/{id}/requirements")
  public String requirements(
      Principal actor,
      @PathVariable Long id,
      @RequestParam DocumentCategory category,
      @RequestParam int count) {
    service.requirement(actor.getName(), id, category, count);
    return "redirect:/projects/" + id;
  }

  @GetMapping("/{id}/timeline")
  public String timeline(
      Principal actor,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("project", service.detail(actor.getName(), id));
    model.addAttribute("projectProgress", progress.view(actor.getName(), id));
    model.addAttribute("records", service.timeline(actor.getName(), id, page));
    return "projecttimeline";
  }
}
