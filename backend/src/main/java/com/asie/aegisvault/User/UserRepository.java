package com.asie.aegisvault.User;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.LockModeType;

import com.asie.aegisvault.Department.Department;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    @Override
    @EntityGraph(attributePaths = "department")
    Page<User> findAll(Specification<User> specification, Pageable pageable);

    // 관리자끼리 동시에 서로를 정지해 마지막 관리자가 사라지는 상황을 방지합니다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.position = com.asie.aegisvault.User.Position.ADMIN order by u.id")
    List<User> findAdministratorsForUpdate();

    long countByAccountStatus(AccountStatus status);

    long countByDepartmentIsNullAndAccountStatusNot(AccountStatus status);

    Optional<User> findByNickname(String nickname);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    List<User> findByDepartment(Department department);

    List<User> findByPosition(Position position);
}
