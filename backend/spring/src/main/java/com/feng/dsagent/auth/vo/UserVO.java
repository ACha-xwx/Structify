package com.feng.dsagent.auth.vo;

import java.util.Set;

public record UserVO(long id, String email, String username, Set<String> roles) {

    public UserVO {
        roles = Set.copyOf(roles);
    }
}
