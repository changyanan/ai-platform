package com.aiplatform.prompt.vo;

import com.aiplatform.prompt.entity.PromptVersion;

import java.time.LocalDateTime;
import java.util.List;

public record PromptVersionVO(
        Long id,
        Long promptId,
        Integer version,
        String content,
        List<PromptVariableVO> variables,
        String changeNote,
        LocalDateTime createdAt
) {

    public static PromptVersionVO from(PromptVersion version, List<PromptVariableVO> variables) {
        return new PromptVersionVO(
                version.getId(),
                version.getPromptId(),
                version.getVersion(),
                version.getContent(),
                variables,
                version.getChangeNote(),
                version.getCreatedAt()
        );
    }
}
