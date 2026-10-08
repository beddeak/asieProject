package com.asie.aegisvault.admin;

import com.asie.aegisvault.Department.Department;
import com.asie.aegisvault.Department.DepartmentRepository;
import com.asie.aegisvault.activity.DocumentActivity;
import com.asie.aegisvault.activity.DocumentActivityRepository;
import com.asie.aegisvault.activity.DocumentAction;
import com.asie.aegisvault.User.AccountStatus;
import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.UserRepository;
import com.asie.aegisvault.security.UserAccessPolicy;
import com.asie.aegisvault.common.PageQueries;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final DocumentActivityRepository activities;
    private final UserAccessPolicy accessPolicy;

    public User requireAdmin(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new AccessDeniedException("사용자를 확인할 수 없습니다.");
        }
        User actor = users.findByNickname(nickname)
                .orElseThrow(() -> new AccessDeniedException("사용자를 확인할 수 없습니다."));
        accessPolicy.requireAdmin(actor);
        return actor;
    }

    public Page<UserListItem> search(String actor, String keyword, Long departmentId,
                             Position position, AccountStatus status, int page) {
        requireAdmin(actor);
        String pattern = keyword == null || keyword.isBlank() ? null : containsPattern(keyword);
        return PageQueries.fetch(page, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"),
                pageable -> users.searchForAdministration(pattern, departmentId, position, status, pageable));
    }

    public List<Department> departments(String actor) {
        requireAdmin(actor);
        return departments.findAll(Sort.by("name", "id"));
    }

    public Summary summary(String actor) {
        requireAdmin(actor);
        return new Summary(users.count(), users.countByAccountStatus(AccountStatus.ACTIVE),
                users.countByAccountStatus(AccountStatus.BAN),
                users.countByDepartmentIsNullAndAccountStatusNot(AccountStatus.DELETED));
    }

    @Transactional
    public void assign(String actor, Long userId, UserAssignmentRequest request) {
        User admin = mutationActor(actor);
        User target = editableUser(userId);
        if (request == null || request.position() == null
                || (request.departmentId() != null && request.departmentId() < 1)) {
            throw new IllegalArgumentException("올바른 직급과 부서를 선택해주세요.");
        }
        if (admin.getId().equals(userId) && request.position() != Position.ADMIN) {
            throw new IllegalArgumentException("자신의 관리자 직급은 변경할 수 없습니다.");
        }
        Department department = request.departmentId() == null ? null
                : departments.findById(request.departmentId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 부서를 찾을 수 없습니다."));
        target.assign(request.position(), department);
    }

    @Transactional
    public void changeStatus(String actor, Long userId, AccountStatus status) {
        User admin = mutationActor(actor);
        User target = editableUser(userId);
        if (status == null || status == AccountStatus.DELETED) {
            throw new IllegalArgumentException("올바른 계정 상태를 선택해주세요.");
        }
        if (admin.getId().equals(userId) && status != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("자신의 계정은 정지하거나 잠글 수 없습니다.");
        }
        target.changeAccountStatus(status);
    }

    @Transactional
    public void delete(String actor, Long userId) {
        User admin = mutationActor(actor);
        User target = editableUser(userId);
        if (admin.getId().equals(userId)) {
            throw new IllegalArgumentException("자신의 관리자 계정은 삭제할 수 없습니다.");
        }
        target.changeAccountStatus(AccountStatus.DELETED);
    }

    public Page<DocumentActivity> activity(String actor, Long userId, Long documentId,
                                          DocumentAction action, String keyword, int page) {
        requireAdmin(actor);
        Specification<DocumentActivity> filter = (root, query, cb) -> cb.conjunction();
        if (userId != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("actorId"), userId));
        }
        if (documentId != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("documentId"), documentId));
        }
        if (action != null) {
            filter = filter.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = containsPattern(keyword);
            filter = filter.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("actorNickname")), pattern, '\\'),
                    cb.like(cb.lower(root.get("documentTitle")), pattern, '\\')));
        }
        Specification<DocumentActivity> activityFilter = filter;
        return PageQueries.fetch(page, 30, Sort.by(Sort.Direction.DESC, "occurredAt", "id"),
                pageable -> activities.findAll(activityFilter, pageable));
    }

    private User mutationActor(String actor) {
        users.findAdministratorsForUpdate();
        return requireAdmin(actor);
    }

    private User editableUser(Long id) {
        if (id == null || id < 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
        User target = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (target.getAccountStatus() == AccountStatus.DELETED) {
            throw new IllegalArgumentException("이미 삭제된 계정은 변경할 수 없습니다.");
        }
        return target;
    }

    private String containsPattern(String keyword) {
        return "%" + keyword.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public record Summary(long total, long active, long banned, long unassigned) { }
}
