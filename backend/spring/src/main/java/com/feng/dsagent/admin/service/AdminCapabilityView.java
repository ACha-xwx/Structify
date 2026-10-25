package com.feng.dsagent.admin.service;

import com.feng.dsagent.admin.AdminRepository;
import com.feng.dsagent.common.ApiException;
import com.feng.dsagent.mail.controller.MailConfigController;
import com.feng.dsagent.mail.service.MailConfigService;
import com.feng.dsagent.compiler.controller.SandboxConfigController;
import com.feng.dsagent.compiler.service.SandboxConfigService;
import com.feng.dsagent.modelconfig.service.ModelConfigService;
import com.feng.dsagent.security.AuthenticatedUser;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

public record AdminCapabilityView(
    long userId,
    java.util.List<String> roles,
    Map<String, AdminModuleCapability> modules,
    AdminServiceStatus service
) {
}
