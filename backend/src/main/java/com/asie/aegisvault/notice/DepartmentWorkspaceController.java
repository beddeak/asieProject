package com.asie.aegisvault.notice;

import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/departments")
public class DepartmentWorkspaceController {
  private final DepartmentNoticeService service;
  private final com.asie.aegisvault.Document.DocumentService documents;
  private final com.asie.aegisvault.project.ProjectService projects;
  private final com.asie.aegisvault.security.CurrentUser actors;

  @GetMapping({"", "/{departmentId}"})
  public String workspace(
      @PathVariable(required = false) Long departmentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int documentPage,
      @RequestParam(defaultValue = "0") int projectPage,
      Model model,
      Principal principal) {
    var workspace = service.workspace(principal.getName(), departmentId, page);
    model.addAttribute("workspace", workspace);
    if (workspace.selected() != null) {
      var filter = new com.asie.aegisvault.Document.DocumentFilter();
      filter.setDepartmentId(workspace.selected().id());
      model.addAttribute(
          "departmentDocuments",
          documents
              .documentList(actors.get(principal.getName()).getId(), filter, documentPage)
              .documents());
      model.addAttribute(
          "departmentProjects",
          projects.list(principal.getName(), null, null, workspace.selected().id(), projectPage));
    }
    return "departmentworkspace";
  }

  @GetMapping("/{departmentId}/notices/new")
  public String newNotice(
      @PathVariable Long departmentId,
      @ModelAttribute("noticeForm") NoticeForm form,
      Model model,
      Principal principal) {
    service.requireManager(principal.getName(), departmentId);
    formModel(principal.getName(), departmentId, null, model);
    return "departmentnoticeform";
  }

  @PostMapping("/{departmentId}/notices")
  public String create(
      @PathVariable Long departmentId,
      @Valid @ModelAttribute("noticeForm") NoticeForm form,
      BindingResult errors,
      Model model,
      Principal principal,
      RedirectAttributes redirect) {
    service.requireManager(principal.getName(), departmentId);
    if (errors.hasErrors()) {
      formModel(principal.getName(), departmentId, null, model);
      return "departmentnoticeform";
    }
    Long id = service.create(principal.getName(), departmentId, form);
    redirect.addFlashAttribute("successMessage", "부서 공지를 등록했습니다.");
    return "redirect:/departments/" + departmentId + "/notices/" + id;
  }

  @GetMapping("/{departmentId}/notices/{noticeId}")
  public String detail(
      @PathVariable Long departmentId,
      @PathVariable Long noticeId,
      Model model,
      Principal principal) {
    model.addAttribute("notice", service.detail(principal.getName(), departmentId, noticeId));
    return "departmentnoticedetail";
  }

  @GetMapping("/{departmentId}/notices/{noticeId}/edit")
  public String edit(
      @PathVariable Long departmentId,
      @PathVariable Long noticeId,
      Model model,
      Principal principal) {
    service.requireManager(principal.getName(), departmentId);
    var notice = service.detail(principal.getName(), departmentId, noticeId);
    NoticeForm form = new NoticeForm();
    form.setTitle(notice.title());
    form.setContent(notice.content());
    form.setVersion(notice.version());
    model.addAttribute("noticeForm", form);
    formModel(principal.getName(), departmentId, noticeId, model);
    return "departmentnoticeform";
  }

  @PostMapping("/{departmentId}/notices/{noticeId}/edit")
  public String update(
      @PathVariable Long departmentId,
      @PathVariable Long noticeId,
      @Valid @ModelAttribute("noticeForm") NoticeForm form,
      BindingResult errors,
      Model model,
      Principal principal,
      RedirectAttributes redirect) {
    service.requireManager(principal.getName(), departmentId);
    // Also check the target on validation errors; never render an edit form for another
    // department's notice.
    service.detail(principal.getName(), departmentId, noticeId);
    if (errors.hasErrors()) {
      formModel(principal.getName(), departmentId, noticeId, model);
      return "departmentnoticeform";
    }
    service.update(principal.getName(), departmentId, noticeId, form);
    redirect.addFlashAttribute("successMessage", "부서 공지를 수정했습니다.");
    return "redirect:/departments/" + departmentId + "/notices/" + noticeId;
  }

  @PostMapping("/{departmentId}/notices/{noticeId}/delete")
  public String delete(
      @PathVariable Long departmentId,
      @PathVariable Long noticeId,
      @RequestParam Long version,
      Principal principal,
      RedirectAttributes redirect) {
    service.delete(principal.getName(), departmentId, noticeId, version);
    redirect.addFlashAttribute("successMessage", "부서 공지를 삭제했습니다.");
    return "redirect:/departments/" + departmentId;
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  @ResponseBody
  public String concurrentUpdate() {
    return "공지 내용이 변경되었습니다. 새로고침한 뒤 다시 시도해주세요.";
  }

  private void formModel(String actor, Long departmentId, Long noticeId, Model model) {
    model.addAttribute("workspace", service.workspace(actor, departmentId, 0));
    model.addAttribute("editing", noticeId != null);
    model.addAttribute(
        "formAction",
        "/departments/"
            + departmentId
            + "/notices"
            + (noticeId == null ? "" : "/" + noticeId + "/edit"));
  }
}
