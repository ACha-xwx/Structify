package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.studyroom.draft.mapper.OutlineMapper;

import com.feng.dsagent.studyroom.draft.service.OutlineFeasibilityService;
import com.feng.dsagent.studyroom.draft.service.OutlineService;
import com.feng.dsagent.studyroom.support.StudyroomMapperFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.feng.dsagent.studyroom.draft.*;
import com.feng.dsagent.studyroom.generation.JdbcGenerationRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

class ProgressiveOutlineConfirmationTest {
    @Test void confirmationFreezesReviewedPlansAndQueuesOnlyBoundedFragments() throws Exception {
        var db=new FragmentTestDatabase();
        var unit=new ReviewedOutline.Unit("concept","栈","理解栈",List.of("stack.definition"),"EXPLAIN",List.of("CONCEPT"),List.of());
        var outline=new ReviewedOutline(1,LearningDepth.STANDARD,"栈",List.of("理解栈"),List.of(),List.of(unit),List.of(),List.of(),List.of(),null,"EVIDENCE_ONLY");
        db.jdbc.update("UPDATE study_outline_drafts SET status='REVIEWABLE',latest_revision=1 WHERE id='draft'");
        db.jdbc.update("INSERT INTO study_outline_revisions(id,draft_id,revision,base_revision,outline_json,issues_json,change_summary_json) VALUES('revision','draft',1,0,?,'[]','[]')",db.json.write(outline));
        var jobs=new JdbcGenerationRepository(db.store,db.json,new DataSourceTransactionManager(db.source));
        var fragments=new JdbcFragmentRepository(db.store,db.json);
        var compiler=new ContentPlanCompiler();
        var feasibility=mock(OutlineFeasibilityService.class);
        when(feasibility.assess(any(),any(),any())).thenReturn(new OutlineFeasibilityService.Assessment(outline,List.of()));
        var service=new OutlineService(db.store,StudyroomMapperFactory.create(db.source, OutlineMapper.class),jobs,db.json,new OutlineValidator(),feasibility,compiler,fragments,new PreparationScheduler(fragments,jobs,db.json));
        String hash=compiler.hash(outline,db.json);
        assertThatThrownBy(()->service.confirm(db.user,"draft",1,"bad-plan","wrong"))
            .hasMessageContaining("讲解安排已改变");
        var result=service.confirm(db.user,"draft",1,"confirmed",hash);
        assertThat(service.confirm(db.user,"draft",1,"confirmed",hash)).isEqualTo(result);
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_fragments WHERE run_id=?",Integer.class,result.get("runId"))).isEqualTo(1);
        assertThat(db.jdbc.queryForList("SELECT DISTINCT job_type FROM studyroom_generation_jobs",String.class)).containsExactly("FRAGMENT");
    }
}
