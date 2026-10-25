package com.feng.dsagent.studyroom.events;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import com.feng.dsagent.studyroom.teaching.TeachingActionValidator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TeachingActionValidatorTest {
    @Test void optionalActionsCannotOverflowTheTerminalEventAndDiscardItsAnswer() {
        var validator=new TeachingActionValidator();String resource="资".repeat(160),scene="景".repeat(160);
        var action=new TeachingActionValidator.Proposed("MATERIAL_FOCUS",scene,resource,null,null,null);
        var valid=validator.validate(java.util.Collections.nCopies(20,action),"run","fragment",1,List.of(new TeachingActionValidator.Target(scene,resource,"PPT",0,0)));
        assertFalse(valid.isEmpty());assertTrue(valid.size()<20);
        assertTrue(new com.feng.dsagent.studyroom.draft.OutlineJson(tools.jackson.databind.json.JsonMapper.builder().build()).write(valid).getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=12000);
    }
    @Test void onlyAcceptsKnownBoundTargetsAndExistingPositions() {
        var validator=new TeachingActionValidator();
        var targets=List.of(new TeachingActionValidator.Target("code","c","C_CODE",10,0),new TeachingActionValidator.Target("anim","a","ANIMATION",0,4));
        var actions=List.of(new TeachingActionValidator.Proposed("CODE_HIGHLIGHT","code","c",2,4,null),new TeachingActionValidator.Proposed("CODE_HIGHLIGHT","code","foreign",1,3,null),new TeachingActionValidator.Proposed("CODE_HIGHLIGHT","code","c",1,11,null),new TeachingActionValidator.Proposed("ANIMATION_SEEK","anim","a",null,null,4),new TeachingActionValidator.Proposed("EXECUTE_JS","code","c",null,null,null));
        var valid=validator.validate(actions,"run","fragment",2,targets);assertEquals(1,valid.size());assertEquals("fragment",valid.getFirst().get("fragmentId"));assertEquals(2,valid.getFirst().get("contentRevision"));
    }
}
