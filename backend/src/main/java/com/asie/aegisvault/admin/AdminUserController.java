package com.asie.aegisvault.admin;

import com.asie.aegisvault.Department.DepartmentCreate;
import com.asie.aegisvault.Department.DepartmentService;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.activity.DocumentAction;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminUserController {
  private final AdminUserService service;
  private final DepartmentService departmentService;

  @GetMapping({"", "/", "/users"})
  public String users(
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(required = false) Long departmentId,
      @RequestParam(required = false) Position position,
      @RequestParam(required = false) AccountStatus status,
      @RequestParam(defaultValue = "0") int page,
      Model model,
      Principal principal) {
    String actor = principal.getName();
    var currentUser = service.requireAdmin(actor);
    model.addAttribute("currentUserId", currentUser.getId());
    model.addAttribute("currentUserName", currentUser.getNickname());
    model.addAttribute(
        "userPage", service.search(actor, keyword, departmentId, position, status, page));
    model.addAttribute("departments", service.departments(actor));
    model.addAttribute("summary", service.summary(actor));
    model.addAttribute("positions", Position.values());
    model.addAttribute("statuses", AccountStatus.values());
    model.addAttribute("keyword", keyword);
    model.addAttribute("departmentId", departmentId);
    model.addAttribute("position", position);
    model.addAttribute("status", status);
    if (!model.containsAttribute("departmentCreate")) {
      model.addAttribute("departmentCreate", new DepartmentCreate());
    }
    model.addAttribute("departmentFormAction", "/admin/departments");
    return "adminusers";
  }

  @PostMapping("/users/{id}/assignment")
  public String assign(
      @PathVariable Long id,
      @Valid @ModelAttribute UserAssignmentRequest request,
      BindingResult errors,
      Principal principal,
      RedirectAttributes redirect) {
    service.requireAdmin(principal.getName());
    if (errors.hasErrors()) {
      redirect.addFlashAttribute("errorMessage", "올바른 직급과 부서를 선택해주세요.");
    } else {
      service.assign(principal.getName(), id, request);
      redirect.addFlashAttribute("successMessage", "직급과 소속 부서를 변경했습니다.");
    }
    return "redirect:/admin/users";
  }

  @PostMapping("/users/{id}/clearance")
  public String clearance(
      @PathVariable Long id,
      @RequestParam com.asie.aegisvault.security.SecurityClassification clearance,
      Principal principal,
      RedirectAttributes redirect) {
    service.clearance(principal.getName(), id, clearance);
    redirect.addFlashAttribute("successMessage", "보안 등급을 변경했습니다.");
    return "redirect:/admin/users";
  }

  @PostMapping("/users/{id}/status")
  public String status(
      @PathVariable Long id,
      @RequestParam AccountStatus status,
      Principal principal,
      RedirectAttributes redirect) {
    service.changeStatus(principal.getName(), id, status);
    redirect.addFlashAttribute("successMessage", "계정 상태를 변경했습니다.");
    return "redirect:/admin/users";
  }

  @PostMapping("/users/{id}/delete")
  public String delete(@PathVariable Long id, Principal principal, RedirectAttributes redirect) {
    service.delete(principal.getName(), id);
    redirect.addFlashAttribute("successMessage", "계정을 삭제했습니다. 작성 문서와 활동 기록은 보존됩니다.");
    return "redirect:/admin/users";
  }

  @PostMapping("/departments")
  public String department(
      @Valid @ModelAttribute("departmentCreate") DepartmentCreate request,
      BindingResult errors,
      Principal principal,
      RedirectAttributes redirect) {
    service.requireAdmin(principal.getName());
    if (errors.hasErrors()) {
      redirect.addFlashAttribute(
          "errorMessage", errors.getAllErrors().getFirst().getDefaultMessage());
      redirect.addFlashAttribute("departmentCreate", request);
      redirect.addFlashAttribute("openDepartmentForm", true);
      return "redirect:/admin/users";
    }
    try {
      var department =
          departmentService.create(
              request.getName(), request.getDescription(), principal.getName());
      redirect.addFlashAttribute("createdDepartmentId", department.getId());
      redirect.addFlashAttribute("successMessage", "부서와 부서 탭이 준비되었습니다. 사용자에게 새 부서를 배정할 수 있습니다.");
    } catch (IllegalArgumentException | DataIntegrityViolationException e) {
      redirect.addFlashAttribute(
          "errorMessage",
          e instanceof IllegalArgumentException ? e.getMessage() : "이미 등록된 부서 이름입니다.");
      redirect.addFlashAttribute("departmentCreate", request);
      redirect.addFlashAttribute("openDepartmentForm", true);
    }
    return "redirect:/admin/users";
  }

  @GetMapping("/activity")
  public String activity(
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Long documentId,
      @RequestParam(required = false) DocumentAction action,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "0") int page,
      Principal principal,
      Model model) {
    model.addAttribute(
        "activityPage",
        service.activity(principal.getName(), userId, documentId, action, keyword, page));
    model.addAttribute("actions", DocumentAction.values());
    model.addAttribute("userId", userId);
    model.addAttribute("documentId", documentId);
    model.addAttribute("action", action);
    model.addAttribute("keyword", keyword);
    return "adminactivity";
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public String invalidAction(IllegalArgumentException exception, RedirectAttributes redirect) {
    redirect.addFlashAttribute("errorMessage", exception.getMessage());
    return "redirect:/admin/users";
  }
}
