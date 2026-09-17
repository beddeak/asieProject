package com.asie.aegisvault.User;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.asie.aegisvault.Department.Department;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    List<User> findByDepartment(Department department);

    List<User> findByPosition(Position position);
}