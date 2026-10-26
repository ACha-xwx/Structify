package com.feng.dsagent.auth.vo;

import java.time.Duration;

import com.feng.dsagent.auth.vo.UserVO;

/**
 * 职责：登录成功后的会话载荷（含 cookie TTL，供控制器写 Cookie）。
 */
public record AuthSessionVO(String token, UserVO user, Duration tokenTtl) {
    public AuthSessionVO {
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("tokenTtl must be positive");
        }
    }
}
