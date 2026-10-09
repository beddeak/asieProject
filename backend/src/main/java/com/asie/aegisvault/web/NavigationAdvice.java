package com.asie.aegisvault.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Shared navigation derives its location from the route, including nested detail pages. */
@ControllerAdvice
public class NavigationAdvice {
  @ModelAttribute("navigationPage")
  public String currentPage(HttpServletRequest request) {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.equals("/")) return "home";
    if (path.equals("/user/login")) return "login";
    if (path.equals("/user/signup")) return "signup";
    String[] segments = path.split("/", 3);
    String section = segments.length > 1 ? segments[1] : "";
    return switch (section) {
      case "document" -> "documents";
      case "projects", "releases" -> "projects";
      case "departments", "tasks", "announcements", "access", "account", "notifications" -> section;
      case "admin", "dep" -> "admin";
      default -> "";
    };
  }
}
