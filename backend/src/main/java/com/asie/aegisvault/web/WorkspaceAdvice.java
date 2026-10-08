package com.asie.aegisvault.web;

import com.asie.aegisvault.audit.SecurityAudit;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
@RequiredArgsConstructor
public class WorkspaceAdvice {
  private final WorkspaceAccount accounts;
  private final SecurityAudit audit;

  @ModelAttribute
  public void account(Principal principal, Model model) {
    if (principal != null)
      model.addAttribute("workspaceAccount", accounts.view(principal.getName()));
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public String invalid(RuntimeException error, Model model) {
    model.addAttribute("errorMessage", error.getMessage());
    model.addAttribute("status", 400);
    return "error";
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public String conflict(Model model) {
    model.addAttribute("errorMessage", "다른 사용자가 먼저 변경했습니다. 새로고침 후 다시 시도해주세요.");
    model.addAttribute("status", 409);
    return "error";
  }

  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public String denied(
      AccessDeniedException error, Principal principal, HttpServletRequest request, Model model) {
    audit.record(
        principal == null ? null : principal.getName(), "ACCESS_DENIED", request.getRequestURI());
    model.addAttribute("errorMessage", error.getMessage());
    model.addAttribute("status", 403);
    return "error";
  }

  @ExceptionHandler(ResponseStatusException.class)
  public String response(
      ResponseStatusException error,
      jakarta.servlet.http.HttpServletResponse response,
      Model model) {
    response.setStatus(error.getStatusCode().value());
    model.addAttribute("status", error.getStatusCode().value());
    model.addAttribute("errorMessage", error.getReason());
    return "error";
  }
}
