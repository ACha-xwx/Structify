package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.knowledge.service.KnowledgeSearchService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.feng.dsagent.knowledge.*;
import com.feng.dsagent.studyroom.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FragmentResourceResolverTest {
    @Test void keepsDifferentRelevantPagesFromOneSourceAndRejectsUnknownBindings() {
        var knowledge = new KnowledgeSearchService(List.of(
            new KnowledgeChunk("a",null,"栈","栈的定义","same","1","PUBLIC"),
            new KnowledgeChunk("b",null,"栈","栈的存储","same","2","PUBLIC")),0);
        var catalog=mock(StudyroomResourceCatalog.class);
        when(catalog.candidates(null)).thenReturn(List.of(new StudyroomResourceCatalog.Candidate(
            StudyroomModelOutput.ResourceType.PPT,"slide","栈","栈的定义",List.of())));
        var resolver=new FragmentResourceResolver(catalog,knowledge);
        var plan=new FragmentPlan("f",0,"理解栈","CONCEPT",List.of("stack.definition"),List.of(),false);
        var result=resolver.resolve("栈",null,plan);
        assertThat(result.evidence()).hasSize(2);
        assertThat(result.candidates()).containsKey("r1");
        assertThatThrownBy(()->resolver.resolveSelected("bad",result)).isInstanceOf(IllegalArgumentException.class);
    }
}
