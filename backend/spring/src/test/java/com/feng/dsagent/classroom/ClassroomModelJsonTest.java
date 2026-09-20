package com.feng.dsagent.classroom;

import static org.assertj.core.api.Assertions.*;
import com.feng.dsagent.aiquota.AiQuotaExecution;
import com.feng.dsagent.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ClassroomModelJsonTest {
    @Test void returnsTheExactValidationPointToTheModelForRepair() {
        List<String> requests = new ArrayList<>();
        AiQuotaExecution model = (user, operation, id, request) -> {
            requests.add(request.messages().toString());
            return new ModelResponse(requests.size()==1 ? "{\"feedback\":\"\"}" : "{\"feedback\":\"正确的解释\"}");
        };
        var result = new ClassroomModelJson(model,new ObjectMapper()).generate(1,"老师","问题",500,json -> ClassroomModelJson.requireText(json,"feedback"));
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).contains("$.feedback 必须是非空字符串");
        assertThat(result.path("feedback").asText()).isEqualTo("正确的解释");
    }
    @Test void invalidJsonIsRetriedButAnUpstreamFailureIsNotDisguised() {
        List<String> requests = new ArrayList<>();
        AiQuotaExecution model = (user, operation, id, request) -> { requests.add(id); return new ModelResponse("broken"); };
        assertThatThrownBy(() -> new ClassroomModelJson(model,new ObjectMapper()).generate(1,"老师","问题",500,json -> {})).hasMessageContaining("模型修正后仍未通过校验");
        assertThat(requests).hasSize(3).doesNotHaveDuplicates();
    }
}
