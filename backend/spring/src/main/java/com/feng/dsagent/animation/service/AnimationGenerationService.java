package com.feng.dsagent.animation.service;

import com.feng.dsagent.aiquota.support.AiQuotaExecution;
import com.feng.dsagent.animation.domain.AnimationDefinition;
import com.feng.dsagent.animation.domain.AnimationValidationResult;
import com.feng.dsagent.animation.dto.AnimationGenerationCommandDTO;
import com.feng.dsagent.animation.mapper.AnimationMapper;
import com.feng.dsagent.animation.prompt.AnimationGenerationPrompts;
import com.feng.dsagent.animation.support.AnimationValidator;
import com.feng.dsagent.animation.error.AnimationErrorCode;
import com.feng.dsagent.animation.vo.AnimationGenerationVO;
import com.feng.dsagent.common.exception.BizException;
import com.feng.dsagent.model.ModelClient;
import com.feng.dsagent.model.ModelErrorCode;
import com.feng.dsagent.model.ModelMessage;
import com.feng.dsagent.model.ModelRequest;
import com.feng.dsagent.model.exception.ModelClientException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class AnimationGenerationService {

    private static final Set<String> TYPES = Set.of("stack", "list", "tree", "queue", "heap", "hash", "array");
    private static final Pattern FENCED_JSON = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private final AiQuotaExecution execution;
    private final AnimationValidator validator;
    private final AnimationMapper mapper;
    private final ObjectMapper objectMapper;

    public AnimationGenerationService(
        ModelClient model,
        AnimationValidator validator,
        AnimationMapper mapper,
        ObjectMapper objectMapper
    ) {
        this(AiQuotaExecution.unmetered(model), validator, mapper, objectMapper);
    }

    @Autowired
    public AnimationGenerationService(
        AiQuotaExecution execution,
        AnimationValidator validator,
        AnimationMapper mapper,
        ObjectMapper objectMapper
    ) {
        this.execution = execution;
        this.validator = validator;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public AnimationGenerationVO generate(AnimationGenerationCommandDTO command, Long userId) {
        return generate(command, userId, null);
    }

    public AnimationGenerationVO generate(AnimationGenerationCommandDTO command, Long userId, String requestId) {
        execution.requireFormalAuthentication(userId);
        String prompt = normalizePrompt(command.prompt());
        String preferredType = normalizeType(command.preferredType());
        String chapterId = normalizeChapterId(command.chapterId());
        String userPrompt = preferredType == null
            ? prompt
            : "优先使用 " + preferredType + " 类型生成动画：" + prompt;

        String raw;
        try {
            raw = execution.complete(userId, "animation", requestId, new ModelRequest(
                List.of(
                    new ModelMessage("system", AnimationGenerationPrompts.SYSTEM_PROMPT),
                    new ModelMessage("user", userPrompt)
                ),
                0.15,
                1600
            )).content();
        } catch (ModelClientException error) {
            throw modelFailure(error);
        }

        AnimationDefinition definition = parse(raw);
        AnimationValidationResult validation = validator.validate(definition);
        if (!validation.valid()) {
            throw AnimationErrorCode.MODEL_INVALID.toException("模型返回的动画步骤不符合安全协议");
        }
        if (preferredType != null && !preferredType.equals(definition.type())) {
            throw AnimationErrorCode.MODEL_INVALID.toException("模型返回了错误的动画类型");
        }

        if (userId == null) {
            return new AnimationGenerationVO(definition, null, false);
        }
        String payload = serialize(definition);
        String recordId = UUID.randomUUID().toString();
        String chapter = chapterId == null || chapterId.isBlank() ? null : chapterId.trim();
        mapper.insertRecord(recordId, userId, chapter, definition.type(), definition.title(), payload);
        return new AnimationGenerationVO(definition, recordId, true);
    }

    private AnimationDefinition parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalidModelResult();
        }
        String json = raw.trim();
        Matcher fenced = FENCED_JSON.matcher(json);
        if (fenced.find()) {
            json = fenced.group(1).trim();
        } else {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw invalidModelResult();
            }
            json = json.substring(start, end + 1);
        }
        try {
            return objectMapper.readValue(json, AnimationDefinition.class);
        } catch (Exception error) {
            throw invalidModelResult();
        }
    }

    private String serialize(AnimationDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to serialize animation definition", error);
        }
    }

    private String normalizePrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        if (normalized.isBlank()) {
            throw AnimationErrorCode.PROMPT_REQUIRED.toException();
        }
        if (normalized.length() > 2000) {
            throw AnimationErrorCode.PROMPT_TOO_LONG.toException();
        }
        return normalized;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        String normalized = type.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(normalized)) {
            throw AnimationErrorCode.TYPE_UNSUPPORTED.toException();
        }
        return normalized;
    }

    private String normalizeChapterId(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) {
            return null;
        }
        String normalized = chapterId.trim();
        if (normalized.length() > 64 || !normalized.matches("^[0-9]{2}-[a-z0-9-]+$")
                || mapper.countPublishedChapter(normalized) <= 0) {
            throw AnimationErrorCode.CHAPTER_INVALID.toException();
        }
        return normalized;
    }

    private BizException modelFailure(ModelClientException error) {
        for (ModelErrorCode value : ModelErrorCode.values()) {
            if (value.code().equals(error.code())) {
                return value.toException("动画生成服务暂时不可用");
            }
        }
        HttpStatus status = switch (error.code()) {
            case "MODEL_NOT_CONFIGURED" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "MODEL_REQUEST_TIMEOUT" -> HttpStatus.GATEWAY_TIMEOUT;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return new BizException(status, error.code(), "动画生成服务暂时不可用");
    }

    private BizException invalidModelResult() {
        return AnimationErrorCode.MODEL_INVALID.toException();
    }
}
