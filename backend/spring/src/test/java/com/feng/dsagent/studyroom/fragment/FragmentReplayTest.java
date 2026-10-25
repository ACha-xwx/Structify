package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import com.feng.dsagent.compiler.CodeExecutionReadiness;
import com.feng.dsagent.knowledge.*;
import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.OutlineJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

/** Runs production contracts and binding without an execution client or a running server. */
class FragmentReplayTest {
    @Test void replaysSanitizedFailuresAndAdviceWithoutModelOrCodeExecution() throws Exception {
        var mapper=JsonMapper.builder().build();var json=new OutlineJson(mapper);
        var manifest=mapper.readTree(new ClassPathResource("studyroom/replay/manifest.json").getInputStream());
        assertThat(manifest.path("fixtureOrigin").asString()).isEqualTo("synthetic-public");
        var catalog=new StudyroomResourceCatalog(null,null,null,null);
        var resolver=new FragmentResourceResolver(catalog,new KnowledgeSearchService(List.of(),0));
        var validator=new FragmentValidator(new FragmentOutputContract(mapper),resolver,catalog,mock(CodeExecutionReadiness.class));
        var evidence=new KnowledgeSearchResult(new KnowledgeChunk("public-stack",null,"栈","栈遵循后进先出。","synthetic","1","PUBLIC"),1);
        var candidate=new StudyroomResourceCatalog.Candidate(StudyroomModelOutput.ResourceType.C_CODE,"public-code","源码","合成源码",List.of());
        var resources=new FragmentResourceResolver.ResolvedResources(Map.of("e1",evidence),Map.of("r1",candidate),Map.of("r1",Map.of()),
            List.of(Map.of("id","public-code","source",manifest.path("code").path("source").asString(),"lineCount","2")),"synthetic-revision");
        int count=0;
        for(var fixture:manifest.path("cases")) {
            String id=fixture.path("id").asString();String activity=fixture.path("activity").asString();
            String output=fixture.has("modelOutputRaw")?fixture.path("modelOutputRaw").asString():mapper.writeValueAsString(fixture.path("modelOutput"));
            var plan=new FragmentPlan("f",0,"理解栈",activity,List.of("stack.definition"),List.of(),false);
            org.assertj.core.api.ThrowableAssert.ThrowingCallable execute=()->{
                if(fixture.path("phase").asString().equals("review"))new TeachingReviewContract(json).validate(output,Set.of("e1"));
                else validator.bind(plan,resources,output);
            };
            if(fixture.path("expectedIssueCode").isNull())assertThatCode(execute).as(id).doesNotThrowAnyException();
            else assertThatThrownBy(execute).as(id).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(fixture.path("expectedIssueCode").asString());
            count++;
        }
        assertThat(count).isEqualTo(7);
    }
}
