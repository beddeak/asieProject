package com.asie.aegisvault.security;

import com.asie.aegisvault.User.Position;
import com.asie.aegisvault.User.User;
import com.asie.aegisvault.User.AccountStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserAccessPolicyTest {
    private final UserAccessPolicy policy = new UserAccessPolicy();

    @ParameterizedTest
    @EnumSource(Position.class)
    void onlyAdminIsRecognizedAsAdmin(Position position) {
        assertEquals(position == Position.ADMIN, policy.isAdmin(userWith(position)));
    }

    @ParameterizedTest
    @EnumSource(value = Position.class, names = {"STAFF", "ASSISTANT_MANAGER"})
    void positionsBelowManagerAreDenied(Position position) {
        assertThrows(AccessDeniedException.class, () -> policy.requireManagerOrAbove(userWith(position)));
    }

    @ParameterizedTest
    @EnumSource(value = Position.class, names = {
            "MANAGER", "DEPUTY_GENERAL_MANAGER", "GENERAL_MANAGER", "EXECUTIVE", "ADMIN"
    })
    void managerAndHigherPositionsAreAllowed(Position position) {
        assertDoesNotThrow(() -> policy.requireManagerOrAbove(userWith(position)));
    }

    @Test
    void missingUserOrPositionNeverGrantsAccess() {
        assertFalse(policy.isAdmin(null));
        assertFalse(policy.isAdmin(userWith(null)));
        assertThrows(AccessDeniedException.class, () -> policy.requireManagerOrAbove(null));
        assertThrows(AccessDeniedException.class, () -> policy.requireManagerOrAbove(userWith(null)));
    }

    private User userWith(Position position) {
        User user = mock(User.class);
        when(user.getPosition()).thenReturn(position);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        return user;
    }

    @ParameterizedTest
    @EnumSource(Position.class)
    void canAssignOnlyOwnRankOrLower(Position authorPosition) {
        User author = userWith(authorPosition);
        for (Position required : Position.values()) {
            if (authorPosition.isAtLeast(required)) {
                assertDoesNotThrow(() -> policy.requireAssignablePosition(author, required));
            } else {
                assertThrows(AccessDeniedException.class, () -> policy.requireAssignablePosition(author, required));
            }
            assertEquals(authorPosition.isAtLeast(required), policy.assignablePositions(author).contains(required));
        }
    }

    @Test
    void missingGradeOrUnknownUserCannotAssignGrade() {
        assertThrows(IllegalArgumentException.class, () -> policy.requireAssignablePosition(userWith(Position.ADMIN), null));
        assertThrows(AccessDeniedException.class, () -> policy.assignablePositions(null));
        assertThrows(AccessDeniedException.class, () -> policy.assignablePositions(userWith(null)));
    }

    @ParameterizedTest
    @EnumSource(value = AccountStatus.class, names = {"BAN", "LOCKED", "DELETED"})
    void inactiveAccountsCannotManageUsersOrDocuments(AccountStatus status) {
        User admin = userWith(Position.ADMIN);
        when(admin.getAccountStatus()).thenReturn(status);
        assertThrows(AccessDeniedException.class, () -> policy.requireAdmin(admin));
        assertThrows(AccessDeniedException.class, () -> policy.requireManagerOrAbove(admin));
        assertThrows(AccessDeniedException.class, () -> policy.assignablePositions(admin));
    }
}
