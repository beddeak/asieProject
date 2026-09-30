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
                                   BindingResult bindingResult, Model model, Principal principal) {
        departmentService.requireAdmin(principal.getName());
        if (bindingResult.hasErrors()) {
            return "departmentcreate";
        }
        try {

            
            departmentService.create(departmentCreate.getName(), departmentCreate.getDescription(), principal.getName());
            return "departmentcreate";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "departmentcreate";
        } catch (DataIntegrityViolationException e) {
            model.addAttribute("errorMessage", "이미 있는 부서이름입니다");
            return "departmentcreate";
        }
    }
}
