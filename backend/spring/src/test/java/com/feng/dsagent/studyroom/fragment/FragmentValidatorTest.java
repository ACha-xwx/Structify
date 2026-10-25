package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.feng.dsagent.compiler.CodeExecutionReadiness;
import com.feng.dsagent.knowledge.*;
import com.feng.dsagent.studyroom.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FragmentValidatorTest {
    @Test void bindsOnlyAuthorizedShortKeysAndKeepsPlainConceptsTextOnly() {
        var catalog=mock(StudyroomResourceCatalog.class);
        var resolver=new FragmentResourceResolver(catalog,new KnowledgeSearchService(List.of(),0));
        var validator=new FragmentValidator(new FragmentOutputContract(JsonMapper.builder().build()),resolver,catalog,mock(CodeExecutionReadiness.class));
        var evidence=new KnowledgeSearchResult(new KnowledgeChunk("actual-evidence",null,"栈","后进先出","source","1","PUBLIC"),1);
        var resources=new FragmentResourceResolver.ResolvedResources(Map.of("e1",evidence),Map.of(),Map.of(),List.of(),"revision");
        var plan=new FragmentPlan("f",0,"理解栈","CONCEPT",List.of("stack.definition"),List.of(),false);
        String raw="{\"title\":\"栈\",\"explanation\":\"后进先出\",\"evidenceKeys\":[\"e1\"],\"scenes\":[]}";
        var output=validator.bind(plan,resources,raw);
        assertThat(output.evidenceIds()).containsExactly("actual-evidence");
        assertThat(output.steps().getFirst().orchestration().primary().kind()).isEqualTo("TEXT");
        assertThatThrownBy(()->validator.bind(plan,resources,raw.replace("e1","e2")))
            .hasMessageContaining("授权依据");
        assertThatThrownBy(()->validator.bind(plan,resources,raw.replace("\"scenes\":[]",
            "\"scenes\":[{\"resourceKey\":\"r9\",\"explanation\":\"fake\"}]")))
            .hasMessageContaining("未授权资源键");
    }
}
