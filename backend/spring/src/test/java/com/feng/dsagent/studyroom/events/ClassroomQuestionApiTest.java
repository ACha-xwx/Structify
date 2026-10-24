package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.security.JwtTokenService;
import com.feng.dsagent.studyroom.*;
import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.checks.ClassroomCheckService;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClassroomQuestionApiTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired JwtTokenService tokens;@Autowired OutlineJson json;@Autowired ClassroomCheckService checks;
    static final long USER=97411,OTHER=97412;
    String auth(long user){return "Bearer "+tokens.issue(user,"qa-api@example.com",Set.of("STUDENT"));}
    @BeforeEach void prepare() {
        db.update("DELETE FROM users WHERE id IN (?,?)",USER,OTHER);
        db.update("INSERT INTO users(id,email,password_hash) VALUES(?,'qa-api@example.com','hash')",USER);
        db.update("INSERT INTO users(id,email,password_hash) VALUES(?,'qa-other@example.com','hash')",OTHER);
        db.update("INSERT INTO study_rooms(id,user_id,title,initial_question,summary,current_run_id,current_step_id) VALUES('qa-room',?,'栈','栈的定义','公开合成课堂','qa-run','qa-step')",USER);
        db.update("INSERT INTO study_outline_drafts(id,user_id,question,depth,status) VALUES('qa-draft',?,'栈','STANDARD','CONFIRMED')",USER);
        db.update("INSERT INTO studyroom_runs(id,room_id,user_id,draft_id,outline_revision,run_revision,status,approved_outline_json) VALUES('qa-run','qa-room',?,'qa-draft',1,1,'ACTIVE','{}')",USER);
        db.update("INSERT INTO studyroom_units(id,run_id,unit_key,unit_order,status,unit_spec_json,content_hash) VALUES('qa-unit','qa-run','stack',0,'READY','{}','hash')");
        String scene=json.write(new StudyroomScene.Orchestration(2,"理解栈","CONCEPT",new StudyroomScene("text","TEXT","概念讲解",List.of(),null,null,null,null),List.of()));
        db.update("INSERT INTO study_room_steps(id,study_room_id,step_key,step_type,title,explanation,evidence_json,resources_json,step_order,orchestration_json,unit_id,run_id,run_scope,step_key_in_unit,generation_status) VALUES('qa-step','qa-room','concept','explanation','栈','后进先出','[]','[]',0,?,'qa-unit','qa-run','qa-run','concept','READY')",scene);
        db.update("INSERT INTO studyroom_fragments(id,run_id,unit_id,fragment_key,fragment_order,plan_json,plan_hash,status,step_id,content_revision) VALUES('qa-fragment','qa-run','qa-unit','concept',0,'{}','hash','READY','qa-step',1)");
    }
    static final String BODY="{\"question\":\"为什么后进先出？\",\"runId\":\"qa-run\",\"fragmentId\":\"qa-fragment\",\"contentRevision\":1,\"context\":{\"stepId\":\"qa-step\",\"sceneId\":\"text\"}}";
    @Test void realReceiptMatchesContractAndCancelIsOwnedAndIdempotent() throws Exception {
        String path="/api/v1/study-rooms/qa-room/messages";
        var response=mvc.perform(post(path).header("Authorization",auth(USER)).header("Idempotency-Key","question-api").contentType("application/json").content(BODY)).andExpect(status().isAccepted()).andReturn();
        var receipt=json.object(response.getResponse().getContentAsString());
        var fixture=json.mapper().readTree(Files.readString(Path.of("../../fixtures/http/studyroom-classroom.json")));
        for(var item:fixture.path("cases"))if("POST".equals(item.path("method").asString())&&"/study-rooms/{id}/messages".equals(item.path("path").asString())) {
            assertEquals(item.path("status").asInt(),response.getResponse().getStatus());
            for(var key:item.path("response").propertyNames())assertTrue(receipt.containsKey(key));
        }
        String id=(String)receipt.get("questionId");assertEquals("question:"+id,receipt.get("streamId"));
        mvc.perform(post(path).header("Authorization",auth(USER)).header("Idempotency-Key","question-api").contentType("application/json").content(BODY)).andExpect(status().isAccepted()).andExpect(jsonPath("$.questionId").value(id));
        mvc.perform(get(path+"/"+id).header("Authorization",auth(OTHER))).andExpect(status().isNotFound());
        mvc.perform(post(path+"/"+id+"/cancel").header("Authorization",auth(USER))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.answer").value(""));
        mvc.perform(post(path+"/"+id+"/cancel").header("Authorization",auth(USER))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM studyroom_generation_calls WHERE job_id=(SELECT job_id FROM studyroom_questions WHERE id=?)",Integer.class,id));
    }
    @Test void checkHttpResponseDoesNotRevealTheAnswerUntilAnOwnedAttempt() throws Exception {
        db.update("INSERT INTO studyroom_generation_jobs(id,user_id,run_id,unit_id,fragment_id,job_type,job_key,status,payload_json) VALUES('qa-job',?,'qa-run','qa-unit','qa-fragment','FRAGMENT','qa-check','RUNNING','{\"revision\":1}')",USER);
        var job=db.queryForMap("SELECT * FROM studyroom_generation_jobs WHERE id='qa-job'");
        var quiz=json.object("{\"checks\":[{\"prompt\":\"先弹出谁？\",\"options\":[{\"id\":\"a\",\"text\":\"先入栈\"},{\"id\":\"b\",\"text\":\"后入栈\"}],\"answerOption\":\"b\",\"analysis\":\"栈遵循后进先出。\"}]}");var sample=(java.util.Map<String,Object>)((java.util.List<?>)quiz.get("checks")).getFirst();var candidates=new java.util.ArrayList<Object>();for(int i=0;i<3;i++){var q=new java.util.LinkedHashMap<>(sample);q.put("prompt","相关题"+i);candidates.add(q);}checks.publish(job,json.write(java.util.Map.of("checks",candidates)));
        var response=mvc.perform(get("/api/v1/studyroom-runs/qa-run/knowledge-points/qa-unit/checks").header("Authorization",auth(USER))).andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].answerOption").doesNotExist()).andExpect(jsonPath("$.questions[0].analysis").doesNotExist()).andReturn();
        String id=json.mapper().readTree(response.getResponse().getContentAsString()).path("questions").get(0).path("id").asString();
        mvc.perform(post("/api/v1/studyroom-runs/qa-run/checks/"+id+"/attempts").header("Authorization",auth(USER)).header("Idempotency-Key","qa-attempt").contentType("application/json").content("{\"selectedOption\":\"b\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.correct").value(true)).andExpect(jsonPath("$.answerOption").value("b"));
    }
}
