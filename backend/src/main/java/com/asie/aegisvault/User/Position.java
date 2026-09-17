package com.asie.aegisvault.User;

import lombok.Getter;

@Getter
public enum Position {
    STAFF("사원", 1),
    ASSISTANT_MANAGER("대리", 2),
    MANAGER("과장", 3),
    DEPUTY_GENERAL_MANAGER("차장", 4),
    GENERAL_MANAGER("부장", 5),
    EXECUTIVE("임원", 6),
    ADMIN("관리자", 7);

    private final String displayName;
    private final int levelOrder;

    Position(String displayName, int levelOrder) {
        this.displayName = displayName;
        this.levelOrder = levelOrder;
    }
    public boolean isAtLeast(Position requiredPosition) {
        return this.levelOrder >= requiredPosition.levelOrder;
    }
    public boolean isAdmin() {
        return this == ADMIN;
    }
}
