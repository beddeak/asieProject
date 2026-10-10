package com.asie.aegisvault.security;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.AccountStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class UserAccessPolicy {
    public void requireActive(User user) {
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("정상 상태의 계정만 이용할 수 있습니다.");
        }
    }

    public void requireAdmin(User user) {
        requireActive(user);
        if (!isAdmin(user)) {
            throw new AccessDeniedException("관리자만 이용할 수 있습니다.");
        }
    }

    public boolean isAdmin(User user) {
        return user != null && user.getPosition() != null && user.getPosition().isAdmin();
    }

    public boolean canReviewDocuments(User user) {
        return user != null && user.getAccountStatus() == AccountStatus.ACTIVE
                && user.getPosition() != null && user.getPosition().isAtLeast(Position.MANAGER)
                && (isAdmin(user) || (user.getDepartment() != null && !user.getDepartment().isClosed()));
    }

    public void requireDocumentReviewer(User user) {
        if (!canReviewDocuments(user)) throw new AccessDeniedException("문서 검토 권한이 없습니다.");
    }

    public void requireManagerOrAbove(User user) {
        requireActive(user);
        if (user == null || user.getPosition() == null
                || !user.getPosition().isAtLeast(Position.MANAGER)) {
            throw new AccessDeniedException("과장 이상만 이용할 수 있습니다.");
        }
    }

    public List<Position> assignablePositions(User user) {
        requireActive(user);
        if (user == null || user.getPosition() == null) {
            throw new AccessDeniedException("사용자 직급을 확인할 수 없습니다.");
        }
        return Arrays.stream(Position.values())
                .filter(user.getPosition()::isAtLeast)
                .toList();
    }

    public void requireAssignablePosition(User user, Position requiredPosition) {
        if (requiredPosition == null) {
            throw new IllegalArgumentException("열람 가능한 최소 직급을 선택해주세요");
        }
        if (!assignablePositions(user).contains(requiredPosition)) {
            throw new AccessDeniedException("자신의 직급보다 높은 열람 등급은 설정할 수 없습니다.");
        }
    }
}
