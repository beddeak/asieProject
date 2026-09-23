package com.asie.aegisvault.Department;

import org.springframework.stereotype.Controller;
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
@RequestMapping("/dep/")
public class DepartmentController {
    private final DepartmentService departmentService;

    @GetMapping("/create")
    public String createDepartment(@ModelAttribute("departmentCreate") DepartmentCreate departmentCreate) {
        return "departmentcreate";
    }

    @PostMapping("/create")
    public String createDepartment(@Valid @ModelAttribute("departmentCreate") DepartmentCreate departmentCreate, BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return "departmentcreate";
        }
        try {
            departmentService.create(departmentCreate.getName(), departmentCreate.getDescription());
            return "departmentcreate";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "departmentcreate";
        }
    }
}
