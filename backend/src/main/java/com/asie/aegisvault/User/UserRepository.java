package com.asie.aegisvault.User;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.admin.UserListItem;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

  @Query(
      """
      select new com.asie.aegisvault.admin.UserListItem(
          u.id, u.nickname, u.email, u.position, u.accountStatus, d.id, d.name, u.createdAt, u.clearance)
      from User u left join u.department d
      where (:pattern is null or lower(u.nickname) like :pattern escape '\\'
             or lower(u.email) like :pattern escape '\\')
        and (:departmentId is null or (:departmentId = 0 and d.id is null) or d.id = :departmentId)
        and (:position is null or u.position = :position)
        and (:status is null or u.accountStatus = :status)
      """)
  Page<UserListItem> searchForAdministration(
      @Param("pattern") String pattern,
      @Param("departmentId") Long departmentId,
      @Param("position") Position position,
      @Param("status") AccountStatus status,
      Pageable pageable);

  // 관리자끼리 동시에 서로를 정지해 마지막 관리자가 사라지는 상황을 방지합니다.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select u from User u where u.position = com.asie.aegisvault.User.Position.ADMIN order by"
          + " u.id")
  List<User> findAdministratorsForUpdate();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id=:id")
  Optional<User> lockById(@Param("id") Long id);

  long countByAccountStatus(AccountStatus status);

  long countByDepartmentIsNullAndAccountStatusNot(AccountStatus status);

  Optional<User> findByNickname(String nickname);

  boolean existsByEmail(String email);

  boolean existsByNickname(String nickname);

  List<User> findByDepartment(Department department);

  List<User> findByPosition(Position position);

  @Query(
      "select u.id from User u where u.accountStatus=com.asie.aegisvault.User.AccountStatus.ACTIVE"
          + " and (u.position=com.asie.aegisvault.User.Position.ADMIN or"
          + " (u.department.id=:departmentId and u.position in :positions and u.clearance in"
          + " :clearances))")
  List<Long> reviewers(
      @Param("departmentId") Long departmentId,
      @Param("positions") List<Position> positions,
      @Param("clearances") List<com.asie.aegisvault.security.SecurityClassification> clearances);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select u from User u where u.department.id=:id order by u.id")
  java.util.List<User> lockDepartmentUsers(
      @org.springframework.data.repository.query.Param("id") Long id);

  @Query(
      "select new com.asie.aegisvault.project.Candidate(u.id,u.nickname,u.position,d.name) from"
          + " User u left join u.department d where"
          + " u.accountStatus=com.asie.aegisvault.User.AccountStatus.ACTIVE and (:keyword is null"
          + " or lower(u.nickname) like :keyword escape '\\')")
  Page<com.asie.aegisvault.project.Candidate> candidates(
      @Param("keyword") String keyword, Pageable pageable);

  java.util.List<User> findByNicknameIn(java.util.Collection<String> nicknames);
}
