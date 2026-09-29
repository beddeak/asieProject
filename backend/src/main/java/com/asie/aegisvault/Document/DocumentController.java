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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.ui.Model;



@RequiredArgsConstructor
@Controller
@RequestMapping("/document/")
public class DocumentController {
    private final DocumentService documentService;
    private final UserRepository userRepository;
    @GetMapping("/write")
    public String writeDocument(@ModelAttribute("documentCreateRequest") DocumentCreateRequest documentCreateRequest) {
        return "documentwrite";
    }
    @PostMapping("/write")
    public String writeDocument(@Valid @ModelAttribute("documentCreateRequest") DocumentCreateRequest documentCreateRequest, BindingResult bindingResult, Model model, Principal principal) {
        if(bindingResult.hasErrors()) {
            return "documentwrite";
        }
        String authorNickname = principal.getName();
        model.addAttribute("authorNickname", authorNickname);
        User author = userRepository.findByNickname(authorNickname).orElseThrow(() -> new IllegalArgumentException("유저를 찾을수가 없습니다"));
        try {
            documentService.create(documentCreateRequest.versionNumber(),
                                    documentCreateRequest.title(),
                                documentCreateRequest.content(),
                            author.getId());
            model.addAttribute("successMessage", "문서가 첫 번째 버전의 초안으로 저장되었습니다.");
            return "documentwrite";
        }catch(IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "documentwrite";
        }
    }
    @GetMapping("/detail/{id}")
    public String documentDetail(Model model, @PathVariable("id") Long id, Principal principal) {
        User viewer = userRepository.findByNickname(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("사용자 정보를 확인할 수 없습니다."));
        DocumentVersion detail = documentService.documentdetail(id, viewer.getId());
        model.addAttribute("documentVersion", detail);
        return "documentdetail";
    }
    
}
