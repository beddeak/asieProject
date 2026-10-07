package com.asie.aegisvault.Document;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.asie.aegisvault.Document.dto.DocumentCreateRequest;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.User.User;

import java.security.Principal;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;



@RequiredArgsConstructor
@Controller
@RequestMapping("/document")
public class DocumentController {
    private final DocumentService documentService;
    private final UserRepository userRepository;

    @GetMapping({"", "/", "/list"})
    @Transactional(readOnly = true)
    public String documentList(@RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(name = "q", defaultValue = "") String query,
                               Model model, Principal principal) {
        User viewer = currentUser(principal);
        String titleQuery = query.strip();
        if (titleQuery.length() > 100) {
            titleQuery = titleQuery.substring(0, 100);
        }
        Page<DocumentVersion> documentPage = documentService.accessibleDocuments(viewer.getId(), page, size, titleQuery);
        model.addAttribute("documentPage", documentPage);
        model.addAttribute("documentVersions", documentPage.getContent());
        model.addAttribute("canWrite", viewer.getDepartment() != null && viewer.getDepartment().getId() != null);
        model.addAttribute("departmentName", viewer.getPosition() != null && viewer.getPosition().isAdmin()
                ? "전체 부서" : viewer.getDepartment() == null ? "부서 미배정" : viewer.getDepartment().getName());
        model.addAttribute("query", titleQuery);
        return "documentlist";
    }

    @GetMapping("/write")
    public String writeDocument(@ModelAttribute("documentCreateRequest") DocumentCreateRequest documentCreateRequest,
                                Model model, Principal principal) {
        User author = currentUser(principal);
        model.addAttribute("availablePositions", documentService.assignablePositions(author.getId()));
        model.addAttribute("automaticallyApproved", author.getPosition().isAdmin());
        return "documentwrite";
    }
    @PostMapping("/write")
    public String writeDocument(@Valid @ModelAttribute("documentCreateRequest") DocumentCreateRequest documentCreateRequest,
                                BindingResult bindingResult, Model model, Principal principal,
                                RedirectAttributes redirectAttributes) {
        User author = currentUser(principal);
        model.addAttribute("availablePositions", documentService.assignablePositions(author.getId()));
        model.addAttribute("automaticallyApproved", author.getPosition().isAdmin());
        if(bindingResult.hasErrors()) {
            return "documentwrite";
        }
        try {
            Document document = documentService.create(documentCreateRequest.title(), documentCreateRequest.content(),
                    documentCreateRequest.requiredPosition(), author.getId());
            redirectAttributes.addFlashAttribute("successMessage", author.getPosition().isAdmin()
                    ? "관리자 문서가 등록되어 승인 완료되었습니다."
                    : "문서가 등록되어 검토 대기로 전환되었습니다.");
            return "redirect:/document/detail/" + document.getId();
        }catch(IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "documentwrite";
        }
    }
    @GetMapping("/detail/{id}")
    public String documentDetail(Model model, @PathVariable("id") Long id, Principal principal) {
        User viewer = currentUser(principal);
        DocumentVersion detail = documentService.documentdetail(id, viewer.getId());
        model.addAttribute("documentVersion", detail);
        return "documentdetail";
    }

    @GetMapping("/review")
    public String reviewQueue(@RequestParam(defaultValue = "0") int page, Model model, Principal principal) {
        User reviewer = currentUser(principal);
        model.addAttribute("reviewPage", documentService.reviewQueue(reviewer.getId(), page));
        return "documentreview";
    }

    @GetMapping("/review/{versionId}")
    public String reviewDetail(@PathVariable Long versionId, Model model, Principal principal) {
        User reviewer = currentUser(principal);
        model.addAttribute("documentVersion", documentService.reviewDetail(versionId, reviewer.getId()));
        model.addAttribute("reviewMode", true);
        return "documentdetail";
    }

    @PostMapping("/review/{versionId}/approve")
    public String approve(@PathVariable Long versionId, Principal principal, RedirectAttributes redirectAttributes) {
        documentService.approve(versionId, currentUser(principal).getId());
        redirectAttributes.addFlashAttribute("successMessage", "문서를 승인했습니다.");
        return "redirect:/document/review";
    }

    @PostMapping("/review/{versionId}/reject")
    public String reject(@PathVariable Long versionId, Principal principal, RedirectAttributes redirectAttributes) {
        documentService.reject(versionId, currentUser(principal).getId());
        redirectAttributes.addFlashAttribute("successMessage", "문서를 반려했습니다.");
        return "redirect:/document/review";
    }

    private User currentUser(Principal principal) {
        return userRepository.findByNickname(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 확인할 수 없습니다."));
    }
}
