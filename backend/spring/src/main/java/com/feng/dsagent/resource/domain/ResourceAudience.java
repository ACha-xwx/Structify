package com.feng.dsagent.resource.domain;

import com.feng.dsagent.security.domain.AuthenticatedUser;

public record ResourceAudience(boolean allowClassroomOnly, boolean allowTeamOnly) {

    public static ResourceAudience from(AuthenticatedUser user) {
        if (user == null) {
            return new ResourceAudience(false, false);
        }
        boolean teamMember = user.hasRole("TEACHER") || user.hasRole("ADMIN");
        return new ResourceAudience(true, teamMember);
    }
}
