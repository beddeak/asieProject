package com.asie.aegisvault.User;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;



@RequiredArgsConstructor 
@Controller
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    @GetMapping("/signup")
    public String signup() {
        return "Signup";
    }
    @PostMapping("/signup")
    public String signup(@Valid User user) {
        try {
            userService.create(user.getNickname(), user.getEmail(), user.getPassword());
            return "redirect:/";
        } catch (IllegalArgumentException e) {
            return "Signup";
        }
    }
    @GetMapping("/login")
    public String login() {
        return "Login";
    }
}
