package com.feng.dsagent.studyroom.fragment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.feng.dsagent.compiler.CodeExecutionReadiness;

class FragmentReadServiceTest {
    @Test void partialFailuresDoNotHidePublishedFragmentsAndReadsNeverQueueWork() {
        var db = new FragmentTestDatabase();
        db.unit("u",0);
        db.fragment("first","u",0,"READY");
        db.fragment("second","u",1,"FAILED");
        var reader = new FragmentReadService(new JdbcFragmentRepository(db.store,db.json),db.json,mock(CodeExecutionReadiness.class));
        var result = reader.get(db.user,"run");
        var unit = ((List<Map<String,Object>>) result.get("knowledgePoints")).getFirst();
        assertThat(unit.get("status")).isEqualTo("PARTIAL");
        assertThat(unit.get("readableFragmentCount")).isEqualTo(1L);
        assertThat(unit.get("failedFragmentCount")).isEqualTo(1L);
        assertThat(result.get("firstReadableFragmentId")).isEqualTo("first");
        assertThat(db.jdbc.queryForObject("SELECT COUNT(*) FROM studyroom_generation_jobs",Integer.class)).isZero();
        assertThatThrownBy(() -> reader.get(db.user+999,"run")).hasMessageContaining("学习路线不存在");
    }
}
