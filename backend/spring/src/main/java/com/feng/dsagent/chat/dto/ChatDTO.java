package com.feng.dsagent.chat.dto;

import com.feng.dsagent.chat.domain.ChatAttachment;
import com.feng.dsagent.chat.domain.ChatCommand;
import com.feng.dsagent.chat.domain.ChatTurn;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatDTO(
    @NotBlank @Size(max = 4000) String prompt,
    @Size(max = 64) String chapterId,
    @Size(max = 64) String sessionId,
    @Size(max = 12) List<@Valid TurnDTO> history,
    Boolean thinkingEnabled,
    @jakarta.validation.constraints.Pattern(regexp = "low|high|max") String reasoningEffort,
    @Size(max = 6) List<ChatAttachment> attachments,
    @jakarta.validation.constraints.Positive Long retryMessageId
) {
    public ChatCommand command() {
        List<ChatTurn> turns = history == null ? List.of() : history.stream()
            .map(turn -> new ChatTurn(turn.role(), turn.content(), turn.attachments()))
            .toList();
        return new ChatCommand(prompt, chapterId, sessionId, turns, thinkingEnabled, reasoningEffort, attachments, retryMessageId);
    }
}
