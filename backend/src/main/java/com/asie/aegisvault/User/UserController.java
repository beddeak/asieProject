package com.asie.aegisvault.User;

import org.springframework.stereotype.Controller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;



@RequiredArgsConstructor 
@Controller
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    @GetMapping("/signup")
    public String signup(@ModelAttribute("signupRequest") SignupRequest signupRequest) {
        return "Signup";
    }
    @PostMapping("/signup")
    public String signup(
            @Valid @ModelAttribute("signupRequest") SignupRequest signupRequest,
            BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            return "Signup";
        }

        if (!signupRequest.getPassword().equals(signupRequest.getPasswordConfirm())) {
            bindingResult.rejectValue("passwordConfirm", "passwordMismatch",
                    "비밀번호가 일치하지 않습니다");
            return "Signup";
        }

        try {
            userService.create(signupRequest.getNickname(), signupRequest.getEmail(),
                    signupRequest.getPassword());
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "Signup";
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "이미 사용 중인 아이디 또는 이메일입니다.");
            return "Signup";
        }
    }
    @GetMapping("/login")
    public String login() {
        return "Login";
    }
}
