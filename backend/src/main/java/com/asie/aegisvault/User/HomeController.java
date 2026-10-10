package com.asie.aegisvault.User;

import com.asie.aegisvault.Document.DocumentService;
import com.asie.aegisvault.User.dto.HomeProfile;
import com.asie.aegisvault.security.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final UserAccessPolicy userAccessPolicy;
    private final com.asie.aegisvault.notification.HomeTaskService homeTasks;
    private final com.asie.aegisvault.project.ProjectService projects;

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String home(Model model, Principal principal) {
        User user = userRepository.findByNickname(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("사용자를 찾을 수 없습니다."));
        userAccessPolicy.requireActive(user);
        if (user.getPosition() == null) {
            throw new AccessDeniedException("사용자 직급을 확인할 수 없습니다.");
        }
        boolean assigned = user.getDepartment() != null && user.getDepartment().getId() != null;
        boolean admin = userAccessPolicy.isAdmin(user);
        var documents = documentService.accessibleDocuments(user.getId(), 0, 6, "");
        model.addAttribute("homeUser", new HomeProfile(user.getNickname(),
                assigned ? user.getDepartment().getName() : null, user.getPosition().getDisplayName()));
        model.addAttribute("recentDocuments", documents.getContent());
        model.addAttribute("documentCount", documents.getTotalElements());
        model.addAttribute("isAdmin", admin);
        model.addAttribute("canWrite", assigned);
        model.addAttribute("canReview", userAccessPolicy.canReviewDocuments(user));
        model.addAttribute("homeTasks", homeTasks.queues(user));
        model.addAttribute("homeProjects", projects.recent(principal.getName()));
        return "home";
    }
}
