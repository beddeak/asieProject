package com.asie.aegisvault.User;

import com.asie.aegisvault.security.UserAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final UserRepository userRepository;
    private final UserAccessPolicy userAccessPolicy;

    @GetMapping("/")
    public String home(Principal principal, Model model) {
        User user = userRepository.findByNickname(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("사용자를 찾을 수 없습니다."));
        userAccessPolicy.requireActive(user);
        Position position = user.getPosition();
        if (position == null) {
            throw new AccessDeniedException("사용자 직급을 확인할 수 없습니다.");
        }
        boolean assigned = user.getDepartment() != null;
        model.addAttribute("accountName", user.getNickname());
        model.addAttribute("positionName", position.getDisplayName());
        model.addAttribute("admin", position.isAdmin());
        model.addAttribute("canWrite", assigned);
        model.addAttribute("canReview", position.isAtLeast(Position.MANAGER) && (position.isAdmin() || assigned));
        return "home";
    }
}
