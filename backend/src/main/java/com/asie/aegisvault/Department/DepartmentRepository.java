package com.asie.aegisvault.Department;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select d from Department d where d.id in :ids order by d.id")
  java.util.List<Department> lockDepartments(
      @org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

  boolean existsByName(String name);

  boolean existsById(Long Id);
}
