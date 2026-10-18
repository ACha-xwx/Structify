package com.feng.dsagent.studyroom.fragment;

import com.feng.dsagent.compiler.CodeExecutionReadiness;
import com.feng.dsagent.studyroom.draft.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FragmentReadService {
    private final JdbcFragmentRepository fragments;
    private final OutlineJson json;
    private final CodeExecutionReadiness execution;
    public FragmentReadService(JdbcFragmentRepository fragments,OutlineJson json,CodeExecutionReadiness execution) {
        this.fragments=fragments;this.json=json;this.execution=execution;
    }

    public Map<String,Object> current(long user,String room) {
        var row=fragments.store().row("SELECT current_run_id FROM study_rooms WHERE id=? AND user_id=?",room,user);
        if(row==null)throw OutlineJson.error(HttpStatus.NOT_FOUND,"STUDYROOM_NOT_FOUND","学习空间不存在");
        OutlineJson.require(row.get("current_run_id")!=null,"OUTLINE_REQUIRED","请先确认学习大纲");
        return get(user,(String)row.get("current_run_id"));
    }

    @Transactional(readOnly=true)
    public Map<String,Object> get(long user,String id) {
        var run=fragments.ownedRun(user,id);
        var store=fragments.store();
        List<Map<String,Object>> points=new ArrayList<>();
        String first=null;
        for(var unit:store.db().queryForList("SELECT * FROM studyroom_units WHERE run_id=? ORDER BY unit_order",id)) {
            List<Map<String,Object>> items=new ArrayList<>();
            for(var fragment:store.db().queryForList("SELECT * FROM studyroom_fragments WHERE unit_id=? ORDER BY fragment_order",unit.get("id"))) {
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("id",fragment.get("id"));item.put("key",fragment.get("fragment_key"));
                item.put("order",fragment.get("fragment_order"));item.put("status",fragment.get("status"));
                item.put("stepId",fragment.get("step_id"));item.put("contentRevision",fragment.get("content_revision"));
                item.put("error",fragment.get("error_code")==null?null:Map.of("code",fragment.get("error_code"),
                    "message","这段内容未准备完成，已就绪的内容仍可阅读。","retryable",!"FRAGMENT_BUDGET_EXHAUSTED".equals(fragment.get("error_code"))));
                items.add(item);
                if(first==null&&fragment.get("step_id")!=null)first=(String)fragment.get("id");
            }
            long ready=items.stream().filter(f->f.get("stepId")!=null).count();
            long failed=items.stream().filter(f->f.get("status").equals("FAILED")).count();
            boolean pending=items.stream().anyMatch(f->Set.of("PENDING","QUEUED","GENERATING").contains(f.get("status")));
            Map<String,Object> point=new LinkedHashMap<>();
            point.put("id",unit.get("id"));point.put("order",unit.get("unit_order"));
            var spec=json.mapper().readTree((String)unit.get("unit_spec_json"));
            point.put("title",spec.path("title").asString((String)unit.get("unit_key")));
            point.put("status",ready==items.size()&&!items.isEmpty()&&!pending&&failed==0?"READY":ready>0?"PARTIAL":failed>0?"NEEDS_ATTENTION":"PREPARING");
            point.put("fragments",items);point.put("readableFragmentCount",ready);point.put("failedFragmentCount",failed);point.put("hasPendingContent",pending);
            points.add(point);
        }
        var progress=store.row("SELECT * FROM studyroom_learning_progress WHERE user_id=? AND run_id=?",user,id);
        Map<String,Object> view=new LinkedHashMap<>();
        view.put("id",id);view.put("roomId",run.get("room_id"));view.put("status",run.get("status"));
        view.put("outline",json.value((String)run.get("approved_outline_json")));view.put("codeExecutionAvailable",execution.available());
        view.put("stateVersion",run.get("state_version"));view.put("firstReadableFragmentId",first);view.put("knowledgePoints",points);
        Map<String,Object> cursor=null;
        if(progress!=null){cursor=new LinkedHashMap<>();cursor.put("unitId",progress.get("unit_id"));cursor.put("fragmentId",progress.get("fragment_id"));
            cursor.put("completed",progress.get("completed"));cursor.put("focusSequence",progress.get("focus_sequence"));}
        view.put("progress",cursor);
        return view;
    }
}
