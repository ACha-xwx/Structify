package com.feng.dsagent.animation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.feng.dsagent.classroom.ClassroomModelJson;
import com.feng.dsagent.model.ModelResponse;
import com.feng.dsagent.security.AuthenticatedUser;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class AnimationIntentControllerTest {
    final ObjectMapper mapper = new ObjectMapper();
    final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    final AuthenticatedUser user = new AuthenticatedUser(1,"test@example.test",Set.of("STUDENT"));
    final String valid = """
        {"version":"1.0","structure":"tree","operation":"traverse","params":{"order":"preorder"},"initial_state":{"data":["A","B","C"]}}
        """;

    @Test void asksModelAndRepairsInvalidRequestBeforeReturningExecutableInput() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("06-tree"))).thenReturn(List.of("树与二叉树"));
        AtomicInteger calls = new AtomicInteger();
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            assertThat(feature).isEqualTo("animation-intent");
            assertThat(request.jsonObject()).isTrue();
            if (calls.getAndIncrement()==0) return new ModelResponse(valid.replace("{\"data\":[\"A\",\"B\",\"C\"]}","[\"A\",\"B\",\"C\"]"));
            assertThat(request.messages().getLast().content()).contains("initial_state must be an object");
            return new ModelResponse(valid);
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper);
        var result = controller.interpret(user,new AnimationIntentController.Input("06-tree","讲先序遍历"));
        assertThat(result.path("initial_state").path("data")).hasSize(3);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void missingChapterNeverCallsTheModel() {
        ClassroomModelJson model = mock(ClassroomModelJson.class);
        when(jdbc.queryForList(anyString(),eq(String.class),eq("missing"))).thenReturn(List.of());
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper);
        assertThatThrownBy(() -> controller.interpret(user,new AnimationIntentController.Input("missing","动画"))).hasMessageContaining("章节未发布");
        verifyNoInteractions(model);
    }

    /**
     * Whether a reply takes up an offer is read, not matched: the learner's own words go to the model
     * and a reply that means something else must come back as a decline instead of a demo nobody
     * asked for. "包的" and "o而k之" are the same yes as "好的", and no word list holds them all.
     */
    @Test void aReplyThatIsNoAgreementIsDeclinedInsteadOfBecomingADemo() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("06-tree"))).thenReturn(List.of("树与二叉树"));
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            assertThat(request.messages().getLast().content()).contains("那队列呢");
            return new ModelResponse("{\"declined\":true,\"reason\":\"学生在问队列，不是答应看演示\"}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper);
        assertThatThrownBy(() -> controller.interpret(user,new AnimationIntentController.Input("06-tree","讲先序遍历",null,false,"那队列呢")))
            .hasMessageContaining("学生在问队列");
    }

    @Test void unsupportedAlgorithmIsNotReplacedWithAnUnrelatedDemo() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("06-tree"))).thenReturn(List.of("树与二叉树"));
        var model = new ClassroomModelJson((id,feature,key,request) -> new ModelResponse("{\"unsupported\":true,\"reason\":\"尚未实现AVL旋转\"}"),mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper);
        assertThatThrownBy(() -> controller.interpret(user,new AnimationIntentController.Input("06-tree","AVL旋转"))).hasMessageContaining("尚未实现AVL旋转");
    }
}
