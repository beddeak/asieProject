package com.asie.aegisvault.User;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class HomeController {
    private final UserRepository userRepository;

    @GetMapping("/")
    public String home(Principal principal) {
        User user = userRepository.findByNickname(principal.getName())
                .orElseThrow(() -> new AccessDeniedException("사용자를 찾을 수 없습니다."));
        return user.getPosition().isAdmin() ? "redirect:/admin/users" : "redirect:/document/write";
    }
}
