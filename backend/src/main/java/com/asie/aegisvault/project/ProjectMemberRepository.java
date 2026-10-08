package com.asie.aegisvault.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
  Optional<ProjectMember> findByProjectIdAndUserId(Long projectId, Long userId);

  boolean existsByProjectIdAndUserId(Long projectId, Long userId);

  @Query("select m.role from ProjectMember m where m.project.id=:projectId and m.user.id=:userId")
  Optional<ProjectRole> role(@Param("projectId") Long projectId, @Param("userId") Long userId);

  @Query(
      """
      select new com.asie.aegisvault.project.MemberSummary(m.user.id,m.user.nickname,m.user.email,m.role)
      from ProjectMember m where m.project.id=:projectId order by m.user.nickname,m.id
      """)
  org.springframework.data.domain.Page<MemberSummary> members(
      @Param("projectId") Long projectId, org.springframework.data.domain.Pageable pageable);

  @Query(
      "select m.user.id from ProjectMember m where m.project.id=:projectId and m.role in :roles and"
          + " m.user.accountStatus=com.asie.aegisvault.User.AccountStatus.ACTIVE")
  List<Long> recipients(
      @Param("projectId") Long projectId, @Param("roles") List<ProjectRole> roles);

  long countByProjectIdAndRole(Long projectId, ProjectRole role);

  @Query(
      "select count(m) from ProjectMember m where m.project.id=:projectId and"
          + " m.role=com.asie.aegisvault.project.ProjectRole.OWNER and"
          + " m.user.accountStatus=com.asie.aegisvault.User.AccountStatus.ACTIVE")
  long activeOwners(@Param("projectId") Long projectId);
}
