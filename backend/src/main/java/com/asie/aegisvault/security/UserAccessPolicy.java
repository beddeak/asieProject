package com.asie.aegisvault.security;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;

@Component
public class UserAccessPolicy {
    public boolean isAdmin(User user) {
        return user != null && user.getPosition() != null && user.getPosition().isAdmin();
    }

    public void requireManagerOrAbove(User user) {
        if (user == null || user.getPosition() == null
                || !user.getPosition().isAtLeast(Position.MANAGER)) {
            throw new AccessDeniedException("과장 이상만 이용할 수 있습니다.");
        }
    }

    public List<Position> assignablePositions(User user) {
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
