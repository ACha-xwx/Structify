package com.feng.dsagent.auth.dto;

import com.feng.dsagent.auth.error.AuthErrorCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginDTO(
    @Size(max = 254) String email,
    @Size(max = 32) String username,
    @NotBlank @Size(max = 128) String password
) {
    public String identifier() {
        boolean hasEmail = email != null && !email.isBlank();
        boolean hasUsername = username != null && !username.isBlank();
        if (hasEmail == hasUsername) {
            throw AuthErrorCode.AUTH_LOGIN_IDENTIFIER_INVALID.toException();
        }
        return hasUsername ? username : email;
    }
}
