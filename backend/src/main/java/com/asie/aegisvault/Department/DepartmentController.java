package com.asie.aegisvault.Department;

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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@RequiredArgsConstructor
@Controller 
@RequestMapping("/dep/")
public class DepartmentController {
    private final DepartmentService departmentService;

    @GetMapping("/create")
    public String createDepartment(@ModelAttribute("departmentCreate") DepartmentCreate departmentCreate,
                                   Principal principal) {
        departmentService.requireAdmin(principal.getName());
        return "departmentcreate";
    }

    @PostMapping("/create")
    public String createDepartment(@Valid @ModelAttribute("departmentCreate") DepartmentCreate departmentCreate,
                                   BindingResult bindingResult, Model model, Principal principal,
                                   RedirectAttributes redirect) {
        departmentService.requireAdmin(principal.getName());
        if (bindingResult.hasErrors()) {
            return "departmentcreate";
        }
        try {
            Department department = departmentService.create(
                    departmentCreate.getName(), departmentCreate.getDescription(), principal.getName());
            redirect.addFlashAttribute("successMessage", "부서를 생성했습니다. 이곳에서 부서 공지를 관리할 수 있습니다.");
            return "redirect:/departments/" + department.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "departmentcreate";
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "이미 있는 부서이름입니다");
            return "departmentcreate";
        }
    }
}
