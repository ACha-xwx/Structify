package com.feng.dsagent.admin.service;

import com.feng.dsagent.admin.converter.AdminAuditEventConverter;
import com.feng.dsagent.admin.converter.AdminUserConverter;
import com.feng.dsagent.admin.domain.UserStatus;
import com.feng.dsagent.admin.entity.AdminUserEntity;
import com.feng.dsagent.admin.error.AdminErrorCode;
import com.feng.dsagent.admin.mapper.AdminMapper;
import com.feng.dsagent.admin.query.AdminAuditQuery;
import com.feng.dsagent.admin.query.AdminUserQuery;
import com.feng.dsagent.admin.vo.AdminAuditEventVO;
import com.feng.dsagent.admin.vo.AdminCapabilityVO;
import com.feng.dsagent.admin.vo.AdminModuleCapabilityVO;
import com.feng.dsagent.admin.vo.AdminServiceStatusVO;
import com.feng.dsagent.admin.vo.AdminUserVO;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.common.support.PageParams;
import com.feng.dsagent.common.support.StringNormalizer;
import com.feng.dsagent.common.vo.PageVO;
import com.feng.dsagent.compiler.service.SandboxConfigService;
import com.feng.dsagent.compiler.vo.SandboxConfigCapabilityVO;
import com.feng.dsagent.common.vo.ConfigCapabilityVO;
import com.feng.dsagent.mail.service.MailConfigService;
import com.feng.dsagent.mail.vo.MailConfigVO;
import com.feng.dsagent.modelconfig.service.ModelConfigService;
import com.feng.dsagent.security.domain.AuthenticatedUser;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 职责：编排管理端用户治理、审计查询与控制台能力聚合。
 */
@Service
public class AdminService {
    private static final Set<String> ALLOWED_ROLES = Set.of("STUDENT", "TEACHER", "ADMIN");

    private final AdminMapper mapper;
    private final AdminUserConverter adminUserConverter;
    private final AdminAuditEventConverter adminAuditEventConverter;
    private final ModelConfigService modelConfig;
    private final MailConfigService mailConfig;
    private final SandboxConfigService sandboxConfig;

    public AdminService(
        AdminMapper mapper,
        AdminUserConverter adminUserConverter,
        AdminAuditEventConverter adminAuditEventConverter,
        ModelConfigService modelConfig,
        MailConfigService mailConfig,
        SandboxConfigService sandboxConfig
    ) {
        this.mapper = mapper;
        this.adminUserConverter = adminUserConverter;
        this.adminAuditEventConverter = adminAuditEventConverter;
        this.modelConfig = modelConfig;
        this.mailConfig = mailConfig;
        this.sandboxConfig = sandboxConfig;
    }

    public AdminCapabilityVO capabilities(AuthenticatedUser user) {
        Map<String, AdminModuleCapabilityVO> modules = new LinkedHashMap<>();
        modules.put("users", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        modules.put("audit", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        modules.put("reviewQueue", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        modules.put("backgroundTasks", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        // The studyroom redesign keeps its own ledgers; the console now reads them, so say so here.
        modules.put("studyroom", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        modules.put("learning", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        modules.put("quota", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        modules.put("classes", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        // The knowledge console owns chunk CRUD and the retrieval tuning; both write through here.
        modules.put("knowledge", new AdminModuleCapabilityVO(true, "AVAILABLE", null));
        ModelConfigService.CapabilityState modelSettings = modelConfig.capabilityState();
        modules.put(
            "modelSettings",
            new AdminModuleCapabilityVO(
                modelSettings.available(),
                modelSettings.available() ? "AVAILABLE" : "UNAVAILABLE",
                modelSettings.reason()
            )
        );
        ConfigCapabilityVO<MailConfigVO> mailSettings = mailConfig.capability();
        modules.put(
            "mailSettings",
            new AdminModuleCapabilityVO(
                mailSettings.available(),
                mailSettings.available() ? "AVAILABLE" : "UNAVAILABLE",
                mailSettings.reason()
            )
        );
        SandboxConfigCapabilityVO sandboxSettings = sandboxConfig.capability();
        modules.put(
            "sandboxSettings",
            new AdminModuleCapabilityVO(
                sandboxSettings.available(),
                sandboxSettings.available() ? "AVAILABLE" : "UNAVAILABLE",
                sandboxSettings.reason()
            )
        );
        return new AdminCapabilityVO(
            user.userId(),
            user.roles().stream().sorted().toList(),
            modules,
            new AdminServiceStatusVO("spring", "0.0.1-SNAPSHOT", "AVAILABLE")
        );
    }

    public PageVO<AdminUserVO> users(int page, int size, String search, String status, String role) {
        PageParams.requireValid(page, size, AdminErrorCode.PAGE_INVALID);
        AdminUserQuery query = new AdminUserQuery(
            page,
            size,
            StringNormalizer.optional(search),
            parseStatus(status),
            normalizeRole(role)
        );
        long total = mapper.countUsers(query);
        return PageVO.of(
            page,
            size,
            total,
            mapper.listUsers(query).stream().map(adminUserConverter::toView).toList()
        );
    }

    public AdminUserVO user(long userId) {
        return adminUserConverter.toView(requireUser(userId));
    }

    @Transactional
    public AdminUserVO updateStatus(
        AuthenticatedUser actor,
        long targetUserId,
        String requestedStatus,
        String requestedReason,
        String requestId
    ) {
        rejectSelfMutation(actor, targetUserId);
        UserStatus status = parseRequiredStatus(requestedStatus);
        String reason = StringNormalizer.optional(requestedReason);
        if (status == UserStatus.DISABLED && reason == null) {
            throw AdminErrorCode.DISABLE_REASON_REQUIRED.toException();
        }
        AdminUserEntity before = mapper.lockUser(targetUserId)
            .orElseThrow(() -> userNotFound(targetUserId));
        if (before.status() == status) {
            appendAudit(actor, "USER_STATUS_CHANGED", before, before, "NO_CHANGE", requestId);
            return adminUserConverter.toView(before);
        }
        if (status == UserStatus.DISABLED) {
            protectLastActiveAdministrator(before, false);
        }
        mapper.updateStatus(targetUserId, status, reason);
        AdminUserEntity after = requireUser(targetUserId);
        appendAudit(actor, "USER_STATUS_CHANGED", before, after, "SUCCESS", requestId);
        return adminUserConverter.toView(after);
    }

    @Transactional
    public AdminUserVO updateRoles(
        AuthenticatedUser actor,
        long targetUserId,
        Set<String> requestedRoles,
        String requestId
    ) {
        rejectSelfMutation(actor, targetUserId);
        Set<String> roles = normalizeRoles(requestedRoles);
        AdminUserEntity before = mapper.lockUser(targetUserId)
            .orElseThrow(() -> userNotFound(targetUserId));
        if (before.roles().equals(roles)) {
            appendAudit(actor, "USER_ROLES_CHANGED", before, before, "NO_CHANGE", requestId);
            return adminUserConverter.toView(before);
        }
        protectLastActiveAdministrator(before, roles.contains("ADMIN"));
        mapper.replaceRoles(targetUserId, roles);
        AdminUserEntity after = requireUser(targetUserId);
        appendAudit(actor, "USER_ROLES_CHANGED", before, after, "SUCCESS", requestId);
        return adminUserConverter.toView(after);
    }

    public PageVO<AdminAuditEventVO> auditEvents(
        int page,
        int size,
        Long actorUserId,
        String action,
        String targetType,
        String targetId,
        String from,
        String to
    ) {
        PageParams.requireValid(page, size, AdminErrorCode.PAGE_INVALID);
        Instant fromInstant = parseInstant(from, "from");
        Instant toInstant = parseInstant(to, "to");
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw AdminErrorCode.AUDIT_TIME_RANGE_INVALID.toException();
        }
        AdminAuditQuery query = new AdminAuditQuery(
            page,
            size,
            actorUserId,
            StringNormalizer.upper(action),
            StringNormalizer.upper(targetType),
            StringNormalizer.optional(targetId),
            fromInstant,
            toInstant
        );
        long total = mapper.countAuditEvents(query);
        return PageVO.of(
            page,
            size,
            total,
            mapper.selectAuditEvents(query).stream().map(adminAuditEventConverter::toView).toList()
        );
    }

    private void protectLastActiveAdministrator(AdminUserEntity before, boolean keepsAdministratorRole) {
        if (before.status() != UserStatus.ACTIVE || !before.roles().contains("ADMIN") || keepsAdministratorRole) {
            return;
        }
        mapper.lockActiveAdministrators();
        if (mapper.activeAdministratorCount() <= 1) {
            throw AdminErrorCode.LAST_ADMIN_PROTECTED.toException();
        }
    }

    private void appendAudit(
        AuthenticatedUser actor,
        String action,
        AdminUserEntity before,
        AdminUserEntity after,
        String result,
        String requestId
    ) {
        mapper.appendAuditEvent(
            actor.userId(),
            action,
            "USER",
            Long.toString(after.id()),
            result,
            requestId == null ? "" : requestId,
            summary(before),
            summary(after)
        );
    }

    private String summary(AdminUserEntity user) {
        return "status=" + user.status().name() + ";roles=" + String.join(",", user.roles().stream().sorted().toList());
    }

    private AdminUserEntity requireUser(long userId) {
        return mapper.findUser(userId).orElseThrow(() -> userNotFound(userId));
    }

    private BizException userNotFound(long userId) {
        return AdminErrorCode.USER_NOT_FOUND.toException();
    }

    private void rejectSelfMutation(AuthenticatedUser actor, long targetUserId) {
        if (actor.userId() == targetUserId) {
            throw AdminErrorCode.SELF_MUTATION_FORBIDDEN.toException();
        }
    }

    private UserStatus parseRequiredStatus(String value) {
        UserStatus status = parseStatus(value);
        if (status == null) {
            throw AdminErrorCode.USER_STATUS_INVALID.toException();
        }
        return status;
    }

    private UserStatus parseStatus(String value) {
        String normalized = StringNormalizer.upper(value);
        if (normalized == null) {
            return null;
        }
        try {
            return UserStatus.valueOf(normalized);
        } catch (IllegalArgumentException error) {
            throw AdminErrorCode.USER_STATUS_INVALID.toException();
        }
    }

    private String normalizeRole(String value) {
        String role = StringNormalizer.upper(value);
        if (role == null) {
            return null;
        }
        if (!ALLOWED_ROLES.contains(role)) {
            throw AdminErrorCode.ROLE_INVALID.toException();
        }
        return role;
    }

    private Set<String> normalizeRoles(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw AdminErrorCode.ROLE_SET_INVALID.toException("角色集合不能为空");
        }
        Set<String> roles = new LinkedHashSet<>();
        for (String value : values) {
            String role = normalizeRole(value);
            if (role == null) {
                throw AdminErrorCode.ROLE_SET_INVALID.toException("角色集合包含空值");
            }
            roles.add(role);
        }
        if (!roles.contains("STUDENT")) {
            throw AdminErrorCode.ROLE_SET_INVALID.toException("所有账号必须保留 STUDENT 基础角色");
        }
        return Set.copyOf(roles);
    }

    private Instant parseInstant(String value, String parameter) {
        String normalized = StringNormalizer.optional(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException error) {
            throw AdminErrorCode.AUDIT_TIME_INVALID.toException(parameter + " 必须是 ISO-8601 UTC 时间");
        }
    }
}
