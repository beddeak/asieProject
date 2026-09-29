package com.asie.aegisvault.security;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** 여러 서비스에서 재사용하는 사용자 직급 검사입니다. */
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
}
