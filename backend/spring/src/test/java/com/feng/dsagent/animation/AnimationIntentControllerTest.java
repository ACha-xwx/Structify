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
import tools.jackson.databind.node.ObjectNode;

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
     * A request carrying only a reply must still bind: `confirmed` is absent from it, and a primitive
     * boolean would map the missing field to null and reject the whole body as unreadable JSON.
     */
    @Test void aReplyWithoutConfirmedStillBinds() {
        AnimationIntentController.Input input = mapper.readValue(
            "{\"chapterId\":\"06-tree\",\"prompt\":\"讲先序遍历\",\"reply\":\"包的\"}", AnimationIntentController.Input.class);
        assertThat(input.reply()).isEqualTo("包的");
        assertThat(input.confirmed()).isFalse();
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

    /**
     * The engine really can insert into a binary search tree, and the model has refused exactly that
     * while claiming the table holds no such operation - so the learner was told a demo was missing
     * when it was one call away. A refusal is now checked against the engine's own table before it is
     * believed, and a wrong one becomes the demo that was asked for.
     */
    @Test void aRefusalIsCheckedAgainstTheCapabilityTableBeforeItIsBelieved() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("06-tree"))).thenReturn(List.of("树与二叉树"));
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        ObjectNode capabilities = (ObjectNode) mapper.readTree("""
            {"prompt":"bst.insert[initialData|key]：二叉排序树插入","capabilities":[
              {"capability":"bst.insert","structure":"bst","operation":"insert","label":"二叉排序树插入",
               "description":"按关键字找到插入位置并挂上新结点","requiredArguments":["initialData","key"]}
            ]}
            """);
        when(engine.capabilities(anyString())).thenReturn(java.util.Optional.of(capabilities));
        ObjectNode resolution = (ObjectNode) mapper.readTree("""
            {"status":"ready","toolRequest":{"request":
              {"version":"1.0","structure":"bst","operation":"insert","params":{},"initial_state":{}}}}
            """);
        when(engine.resolve(any(),any())).thenReturn(java.util.Optional.of(resolution));

        AtomicInteger calls = new AtomicInteger();
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse("{\"unsupported\":true,\"reason\":\"tree 仅支持遍历，无法演示插入\"}");
            }
            // The recheck has to put the real entries in front of it, or the second answer repeats the first.
            assertThat(request.messages().getFirst().content()).contains("bst.insert").contains("二叉排序树插入");
            return new ModelResponse("{\"needed\":true,\"confidence\":0.9,\"capability\":\"bst.insert\",\"purpose\":\"看结点怎么找到位置\",\"arguments\":{}}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        var result = controller.interpret(user,new AnimationIntentController.Input("06-tree","我想学一下排序树"));

        assertThat(result.path("operation").asText()).isEqualTo("insert");
        assertThat(calls.get()).isEqualTo(2);
    }

    /**
     * The reported case, verbatim: the learner is in the stack chapter and asks for a halving search. The
     * model refused with "折半查找属于查找算法，不在当前栈与队列章节的能力表内", and the check only ever read
     * that same chapter, so the refusal stood. A chapter is a preference, not a boundary - the check reads
     * the whole registry now.
     */
    @Test void aRefusalIsCheckedAgainstTheWholeRegistryNotOnlyTheChapter() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("03-stack-queue"))).thenReturn(List.of("栈与队列"));
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        when(engine.capabilities("03-")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"stack.push[value]：栈入栈","capabilities":[
              {"capability":"stack.push","structure":"stack","operation":"push","label":"栈入栈","description":"新元素压到栈顶"}]}
            """)));
        when(engine.capabilities("")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"search.binary[initialData|key]：折半查找","capabilities":[
              {"capability":"search.binary","structure":"search","operation":"binary","label":"折半查找","description":"每次砍掉一半区间"}]}
            """)));
        ObjectNode resolution = (ObjectNode) mapper.readTree("""
            {"status":"ready","toolRequest":{"request":
              {"version":"1.0","structure":"search","operation":"binary","params":{},"initial_state":{}}}}
            """);
        when(engine.resolve(any(),any())).thenReturn(java.util.Optional.of(resolution));

        AtomicInteger calls = new AtomicInteger();
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse("{\"unsupported\":true,\"reason\":\"折半查找属于查找算法，不在当前栈与队列章节的能力表内\"}");
            }
            // The second answer must be able to see the entry it could not see before.
            assertThat(request.messages().getFirst().content()).contains("search.binary").contains("折半查找");
            return new ModelResponse("{\"needed\":true,\"confidence\":0.9,\"capability\":\"search.binary\",\"purpose\":\"看区间怎么折半\",\"arguments\":{}}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        var result = controller.interpret(user,new AnimationIntentController.Input("03-stack-queue","折半查找"));

        assertThat(result.path("operation").asText()).isEqualTo("binary");
        assertThat(calls.get()).isEqualTo(2);
    }

    /**
     * 绪论 registers no demos of its own, so the scoped table comes back empty. The request then dropped to
     * the free-form path, which puts no capability table in front of the model at all - and a model without
     * the facts improvises. An empty chapter widens to the whole textbook instead.
     */
    @Test void aChapterWithNoDemosOfItsOwnReadsTheWholeRegistryInsteadOfGuessing() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("01-introduction"))).thenReturn(List.of("绪论"));
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        when(engine.capabilities("01-")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"","capabilities":[]}
            """)));
        when(engine.capabilities("")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"linked_list.reverse[initialData]：单链表逆置","capabilities":[
              {"capability":"linked_list.reverse","structure":"linked_list","operation":"reverse","label":"单链表逆置","description":"逐结点反转 next 指针"}]}
            """)));
        ObjectNode resolution = (ObjectNode) mapper.readTree("""
            {"status":"ready","toolRequest":{"request":
              {"version":"1.0","structure":"linked_list","operation":"reverse","params":{},"initial_state":{}}}}
            """);
        when(engine.resolve(any(),any())).thenReturn(java.util.Optional.of(resolution));

        var model = new ClassroomModelJson((id,feature,key,request) -> {
            assertThat(request.messages().getFirst().content()).contains("linked_list.reverse");
            return new ModelResponse("{\"needed\":true,\"confidence\":0.9,\"capability\":\"linked_list.reverse\",\"purpose\":\"看指针怎么反过来\",\"arguments\":{}}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        var result = controller.interpret(user,new AnimationIntentController.Input("01-introduction","单链表逆置"));

        assertThat(result.path("operation").asText()).isEqualTo("reverse");
    }

    /**
     * The door the model actually used in the report: it answered "not needed" rather than "unsupported",
     * reasoning that halving search does not belong to the stack chapter. That is a chapter boundary deciding,
     * so the wider table is checked here too.
     */
    @Test void aNotNeededAnswerIsRecheckedWhenAnotherChapterHoldsTheDemo() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("03-stack-queue"))).thenReturn(List.of("栈与队列"));
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        when(engine.capabilities("03-")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"stack.push[value]：栈入栈","capabilities":[
              {"capability":"stack.push","structure":"stack","operation":"push","label":"栈入栈","description":"新元素压到栈顶"}]}
            """)));
        when(engine.capabilities("")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"search.binary[initialData|key]：折半查找","capabilities":[
              {"capability":"search.binary","structure":"search","operation":"binary","label":"折半查找","description":"每次砍掉一半区间"}]}
            """)));
        ObjectNode resolution = (ObjectNode) mapper.readTree("""
            {"status":"ready","toolRequest":{"request":
              {"version":"1.0","structure":"search","operation":"binary","params":{},"initial_state":{}}}}
            """);
        when(engine.resolve(any(),any())).thenReturn(java.util.Optional.of(resolution));

        AtomicInteger calls = new AtomicInteger();
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            if (calls.getAndIncrement() == 0) {
                return new ModelResponse("{\"needed\":false,\"confidence\":0.2,\"capability\":\"\",\"purpose\":\"折半查找属于查找算法，不在当前栈与队列章节的能力表内\",\"arguments\":{}}");
            }
            assertThat(request.messages().getFirst().content()).contains("search.binary").contains("折半查找");
            return new ModelResponse("{\"needed\":true,\"confidence\":0.9,\"capability\":\"search.binary\",\"purpose\":\"看区间怎么折半\",\"arguments\":{}}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        var result = controller.interpret(user,new AnimationIntentController.Input("03-stack-queue","折半查找"));

        assertThat(result.path("operation").asText()).isEqualTo("binary");
        assertThat(calls.get()).isEqualTo(2);
    }

    /**
     * The check costs a call, so it only runs when the wider table holds something the chapter's did not. A
     * concept question matches the same entries on both sides - "not needed" stands, and the second call is not
     * spent.
     */
    @Test void aNotNeededAnswerForAConceptQuestionStaysWithoutASecondCall() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("03-stack-queue"))).thenReturn(List.of("栈与队列"));
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        when(engine.capabilities("03-")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"stack.push[value]：栈入栈","capabilities":[
              {"capability":"stack.push","structure":"stack","operation":"push","label":"栈入栈","description":"新元素压到栈顶"}]}
            """)));
        when(engine.capabilities("")).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"prompt":"stack.push[value]：栈入栈","capabilities":[
              {"capability":"stack.push","structure":"stack","operation":"push","label":"栈入栈","description":"新元素压到栈顶"}]}
            """)));
        when(engine.resolve(any(),any())).thenReturn(java.util.Optional.of((ObjectNode) mapper.readTree("""
            {"status":"not-needed"}
            """)));

        AtomicInteger calls = new AtomicInteger();
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            calls.getAndIncrement();
            return new ModelResponse("{\"needed\":false,\"confidence\":0.9,\"capability\":\"\",\"purpose\":\"栈的定义是静态概念，讲一句话即可\",\"arguments\":{}}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        assertThatThrownBy(() -> controller.interpret(user,new AnimationIntentController.Input("03-stack-queue","栈是什么")))
            .hasMessageContaining("静态概念");
        assertThat(calls.get()).isEqualTo(1);
    }

    /**
     * No chapter means the whole textbook. The chat page used to send the first chapter instead, which
     * silently cut the capability table down to the introduction - and a linked-list reversal was then
     * refused as "linked_list 仅支持 append/delete/find/insert" while the entry sat in the full table.
     */
    @Test void aRequestWithoutAChapterSearchesTheWholeTextbook() {
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        ObjectNode capabilities = (ObjectNode) mapper.readTree("""
            {"prompt":"linked_list.reverse[initialData]：单链表逆置","capabilities":[
              {"capability":"linked_list.reverse","label":"单链表逆置","description":"逐结点反转 next 指针"}
            ]}
            """);
        when(engine.capabilities(anyString())).thenReturn(java.util.Optional.of(capabilities));
        ObjectNode resolution = (ObjectNode) mapper.readTree("""
            {"status":"ready","toolRequest":{"request":
              {"version":"1.0","structure":"linked_list","operation":"reverse","params":{},"initial_state":{}}}}
            """);
        when(engine.resolve(any(),any())).thenReturn(java.util.Optional.of(resolution));

        var model = new ClassroomModelJson((id,feature,key,request) -> {
            assertThat(request.messages().getLast().content()).contains("全部章节");
            return new ModelResponse("{\"needed\":true,\"confidence\":0.9,\"capability\":\"linked_list.reverse\",\"purpose\":\"看指针怎么反过来\",\"arguments\":{}}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        var result = controller.interpret(user,new AnimationIntentController.Input(null,"单链表逆置"));

        assertThat(result.path("operation").asText()).isEqualTo("reverse");
        // The empty scope is the whole registry, and no chapter row is read on the way there.
        verify(engine).capabilities("");
        verifyNoMoreInteractions(jdbc);
    }

    /** When the table offers nothing close, the refusal stands and costs no second call. */    @Test void aRefusalWithNothingCloseStandsWithoutASecondCall() {
        when(jdbc.queryForList(anyString(),eq(String.class),eq("06-tree"))).thenReturn(List.of("树与二叉树"));
        var engine = mock(DsvpLocalEngine.class);
        when(engine.enabled()).thenReturn(true);
        ObjectNode capabilities = (ObjectNode) mapper.readTree("""
            {"prompt":"bst.insert：二叉排序树插入","capabilities":[
              {"capability":"bst.insert","label":"二叉排序树插入","description":"按关键字插入结点"}
            ]}
            """);
        when(engine.capabilities(anyString())).thenReturn(java.util.Optional.of(capabilities));

        AtomicInteger calls = new AtomicInteger();
        var model = new ClassroomModelJson((id,feature,key,request) -> {
            calls.incrementAndGet();
            return new ModelResponse("{\"unsupported\":true,\"reason\":\"尚未实现AVL旋转\"}");
        },mapper);
        var controller = new AnimationIntentController(model,new DsvpAnimationAdapter(mapper,new AnimationValidator()),jdbc,mapper,engine);

        assertThatThrownBy(() -> controller.interpret(user,new AnimationIntentController.Input("06-tree","AVL旋转怎么调整")))
            .hasMessageContaining("尚未实现AVL旋转");
        assertThat(calls.get()).isEqualTo(1);
    }
}
