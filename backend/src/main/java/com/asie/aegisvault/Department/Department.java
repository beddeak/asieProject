package com.asie.aegisvault.Department;

import jakarta.persistence.*;
import lombok.Getter;

import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "department")
public class Department {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255, unique = true)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;
}