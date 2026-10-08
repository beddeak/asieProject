package com.asie.aegisvault.Document;

import com.asie.aegisvault.security.CurrentUser;
import com.asie.aegisvault.security.SecurityClassification;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/document")
public class DocumentWorkflowController {
  private final DocumentService service;
  private final DocumentPage page;
  private final CurrentUser actors;
  private final com.asie.aegisvault.project.ProjectService projects;

  @GetMapping("/new")
  public String createForm(
      Principal actor, @ModelAttribute("form") DocumentForm form, Model model) {
    choices(actor, model);
    if (form.getProjectId() != null)
      model.addAttribute("project", projects.detail(actor.getName(), form.getProjectId()));
    return "documenteditor";
  }

  @PostMapping("/drafts")
  public String create(
      Principal actor,
      @Valid @ModelAttribute("form") DocumentForm form,
      BindingResult errors,
      @RequestParam(defaultValue = "false") boolean submit,
      Model model) {
    if (errors.hasErrors()) return createForm(actor, form, model);
    var document =
        service.create(
            new DocumentCommand(
                form.getTitle(),
                form.getContent(),
                form.getRequiredPosition(),
                form.getProjectId(),
                form.getCategory(),
                form.getClassification(),
                !submit),
            actors.get(actor.getName()).getId());
    return "redirect:/document/detail/" + document.getId();
  }

  @GetMapping("/versions/{id}")
  public String version(
      Principal actor,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int commentPage,
      @RequestParam(defaultValue = "0") int filePage,
      Model model) {
    var user = actors.get(actor.getName());
    page.fill(
        model, service.versionDetail(id, user.getId()), actor.getName(), commentPage, filePage);
    return "documentdetail";
  }

  @GetMapping("/versions/{id}/edit")
  public String edit(Principal actor, @PathVariable Long id, Model model) {
    Long actorId = actors.get(actor.getName()).getId();
    var version = service.versionDetail(id, actorId);
    if (!service.actions(id, actorId).edit())
      throw new org.springframework.security.access.AccessDeniedException("편집 가능한 최신 초안이 아닙니다.");
    DocumentForm form = new DocumentForm();
    form.setTitle(version.getTitle());
    form.setContent(version.getContent());
    form.setRevision(version.getRevision());
    model.addAttribute("form", form);
    model.addAttribute("versionId", id);
    choices(actor, model);
    return "documenteditor";
  }

  @PostMapping("/versions/{id}/edit")
  public String save(
      Principal actor,
      @PathVariable Long id,
      @Valid @ModelAttribute("form") DocumentForm form,
      BindingResult errors,
      @RequestParam(defaultValue = "false") boolean submit,
      Model model) {
    if (errors.hasErrors()) {
      model.addAttribute("versionId", id);
      choices(actor, model);
      return "documenteditor";
    }
    service.edit(
        id,
        actors.get(actor.getName()).getId(),
        form.getRevision(),
        form.getTitle(),
        form.getContent(),
        submit);
    return "redirect:/document/versions/" + id;
  }

  @PostMapping("/{id}/versions")
  public String next(Principal actor, @PathVariable Long id, @RequestParam Long expectedVersionId) {
    Long next = service.newVersion(id, actors.get(actor.getName()).getId(), expectedVersionId);
    return "redirect:/document/versions/" + next + "/edit";
  }

  @PostMapping("/{id}/archive")
  public String archive(
      Principal actor, @PathVariable Long id, @RequestParam Long expectedVersionId) {
    service.archive(id, actors.get(actor.getName()).getId(), expectedVersionId);
    return "redirect:/document/detail/" + id;
  }

  @GetMapping("/{id}/history")
  public String history(
      Principal actor,
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      Model model) {
    model.addAttribute("history", service.history(id, actors.get(actor.getName()).getId(), page));
    model.addAttribute("documentId", id);
    return "documenthistory";
  }

  @PostMapping("/versions/{id}/comment")
  public String comment(Principal actor, @PathVariable Long id, @RequestParam String content) {
    service.comment(id, actors.get(actor.getName()).getId(), content);
    return "redirect:/document/versions/" + id;
  }

  @PostMapping("/versions/{id}/security")
  public String security(
      Principal actor,
      @PathVariable Long id,
      @RequestParam boolean approved,
      @RequestParam(defaultValue = "") String reason) {
    service.review(id, actors.get(actor.getName()).getId(), approved, true, reason);
    return "redirect:/document/versions/" + id;
  }

  private void choices(Principal actor, Model model) {
    model.addAttribute(
        "availablePositions", service.assignablePositions(actors.get(actor.getName()).getId()));
    model.addAttribute("categories", DocumentCategory.values());
    model.addAttribute("classifications", SecurityClassification.values());
  }
}
