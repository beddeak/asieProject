package com.asie.aegisvault.workflow;

import com.asie.aegisvault.project.ProjectService;
import com.asie.aegisvault.release.ReleaseService;
import java.security.Principal;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/work")
public class WorkflowController {
  private final WorkflowService service;
  private final ProjectService projects;
  private final ReleaseService releases;
  private final com.asie.aegisvault.Document.DocumentService documents;
  private final com.asie.aegisvault.security.CurrentUser actors;

  @GetMapping
  public String work(
      Principal actor,
      @PathVariable Long projectId,
      @RequestParam(defaultValue = "changes") String tab,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "0") int documentPage,
      @RequestParam(required = false) String documentKeyword,
      @RequestParam(defaultValue = "0") int memberPage,
      Model model) {
    String name = actor.getName();
    model.addAttribute("project", projects.detail(name, projectId));
    model.addAttribute("tab", tab);
    switch (tab) {
      case "changes" -> {
        model.addAttribute("changes", service.changes(name, projectId, page));
        var filter = new com.asie.aegisvault.Document.DocumentFilter();
        filter.setProjectId(projectId);
        filter.setStatus(com.asie.aegisvault.Document.DocumentStatus.APPROVED);
        filter.setKeyword(documentKeyword);
        model.addAttribute(
            "documentChoices",
            documents.documentList(actors.get(name).getId(), filter, documentPage).documents());
        model.addAttribute("documentKeyword", documentKeyword);
        model.addAttribute("memberChoices", projects.members(name, projectId, memberPage));
      }
      case "quality" -> {
        model.addAttribute("checks", service.checklist(name, projectId));
        model.addAttribute("runs", service.runs(name, projectId, page));
        model.addAttribute("fingerprint", service.fingerprint(name, projectId));
      }
      case "defects" -> model.addAttribute("defects", service.defects(name, projectId, page));
      case "security" -> {
        model.addAttribute("assessments", service.assessments(name, projectId, page));
        model.addAttribute("fingerprint", service.fingerprint(name, projectId));
      }
      case "release" -> {
        model.addAttribute("gate", releases.gate(name, projectId));
        model.addAttribute("releases", releases.list(name, projectId, page));
      }
      default -> throw new IllegalArgumentException("업무 탭을 찾을 수 없습니다.");
    }
    return "projectwork";
  }

  @PostMapping("/changes")
  public String request(
      Principal actor,
      @PathVariable Long projectId,
      @RequestParam String title,
      @RequestParam String description,
      @RequestParam Long baseVersionId) {
    service.requestChange(actor.getName(), projectId, title, description, baseVersionId);
    return back(projectId, "changes");
  }

  @PostMapping("/changes/{id}/assign")
  public String assign(
      Principal actor,
      @PathVariable Long projectId,
      @PathVariable Long id,
      @RequestParam Long userId) {
    service.assignChange(actor.getName(), projectId, id, userId);
    return back(projectId, "changes");
  }

  @PostMapping("/changes/{id}/resolve")
  public String resolve(
      Principal actor,
      @PathVariable Long projectId,
      @PathVariable Long id,
      @RequestParam(required = false) Long versionId,
      @RequestParam String reason,
      @RequestParam(defaultValue = "false") boolean reject) {
    service.resolveChange(actor.getName(), projectId, id, versionId, reason, reject);
    return back(projectId, "changes");
  }

  @PostMapping("/checks")
  public String check(
      Principal actor, @PathVariable Long projectId, @RequestParam String criterion) {
    service.addCheck(actor.getName(), projectId, criterion);
    return back(projectId, "quality");
  }

  @PostMapping("/checks/{id}/retire")
  public String retire(Principal actor, @PathVariable Long projectId, @PathVariable Long id) {
    service.retireCheck(actor.getName(), projectId, id);
    return back(projectId, "quality");
  }

  @PostMapping("/tests")
  public String test(
      Principal actor,
      @PathVariable Long projectId,
      @RequestParam String fingerprint,
      @RequestParam(defaultValue = "") Set<Long> passedChecks,
      @RequestParam String evidence,
      @RequestParam(required = false) Long retestOf) {
    service.test(actor.getName(), projectId, fingerprint, passedChecks, evidence, retestOf);
    return back(projectId, "quality");
  }

  @GetMapping("/tests/{runId}")
  public String results(
      Principal actor, @PathVariable Long projectId, @PathVariable Long runId, Model model) {
    model.addAttribute("project", projects.detail(actor.getName(), projectId));
    model.addAttribute("results", service.results(actor.getName(), projectId, runId));
    return "qualityresults";
  }

  @PostMapping("/defects/{id}/close")
  public String close(
      Principal actor,
      @PathVariable Long projectId,
      @PathVariable Long id,
      @RequestParam Long passingRunId,
      @RequestParam String correctiveAction) {
    service.closeDefect(actor.getName(), projectId, id, passingRunId, correctiveAction);
    return back(projectId, "defects");
  }

  @PostMapping("/security")
  public String security(
      Principal actor,
      @PathVariable Long projectId,
      @RequestParam String fingerprint,
      @RequestParam boolean approved,
      @RequestParam String findings) {
    service.assess(actor.getName(), projectId, fingerprint, approved, findings);
    return back(projectId, "security");
  }

  @PostMapping("/release")
  public String release(
      Principal actor,
      @PathVariable Long projectId,
      @RequestParam String fingerprint,
      @RequestParam String recipientNames,
      @RequestParam String notes) {
    var names =
        java.util.Arrays.stream(recipientNames.split(","))
            .map(String::strip)
            .filter(n -> !n.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    return "redirect:/releases/"
        + releases.publish(actor.getName(), projectId, fingerprint, names, notes);
  }

  private String back(Long id, String tab) {
    return "redirect:/projects/" + id + "/work?tab=" + tab;
  }
}
