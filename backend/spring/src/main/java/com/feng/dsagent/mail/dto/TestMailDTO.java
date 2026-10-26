package com.feng.dsagent.mail.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TestMailDTO(
    @Valid MailConfigDTO config,
    @NotBlank @Email @Size(max = 254) String recipient
) {}
