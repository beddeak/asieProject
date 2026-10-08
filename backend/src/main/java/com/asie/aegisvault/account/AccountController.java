package com.asie.aegisvault.account;

import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AccountController {
  private final AccountService service;

  @GetMapping("/account")
  public String account() {
    return "account";
  }

  @PostMapping("/account/password")
  public String change(
      Principal actor,
      @RequestParam String current,
      @RequestParam String password,
      @RequestParam String confirmation,
      HttpServletRequest request) {
    service.change(actor.getName(), current, password, confirmation);
    if (request.getSession(false) != null) request.getSession(false).invalidate();
    org.springframework.security.core.context.SecurityContextHolder.clearContext();
    return "redirect:/user/login?changed";
  }

  @GetMapping("/account/recovery")
  public String recovery() {
    return "recovery";
  }

  @PostMapping("/account/recovery")
  public String request(
      @RequestParam String nickname, @RequestParam String email, RedirectAttributes redirect) {
    service.request(nickname, email);
    redirect.addFlashAttribute(
        "successMessage", "정보가 일치하는 계정의 복구 요청을 접수했습니다. 관리자에게 본인 확인을 요청해주세요.");
    return "redirect:/account/recovery";
  }

  @GetMapping("/account/reset")
  public String reset(@RequestParam String token, Model model) {
    model.addAttribute("token", token);
    return "reset";
  }

  @PostMapping("/account/reset")
  public String resetPassword(
      @RequestParam String token,
      @RequestParam String password,
      @RequestParam String confirmation) {
    service.reset(token, password, confirmation);
    return "redirect:/user/login?changed";
  }

  @GetMapping("/admin/recovery")
  public String requests(Principal actor, @RequestParam(defaultValue = "0") int page, Model model) {
    model.addAttribute("requests", service.requests(actor.getName(), page));
    return "adminrecovery";
  }

  @PostMapping("/admin/recovery/{id}")
  public String issue(
      Principal actor,
      @PathVariable Long id,
      @RequestParam String verification,
      RedirectAttributes redirect) {
    redirect.addFlashAttribute(
        "recoveryLink", "/account/reset?token=" + service.issue(actor.getName(), id, verification));
    return "redirect:/admin/recovery";
  }
}
