package com.feng.dsagent.studyroom.checks;

import com.feng.dsagent.studyroom.draft.OutlineJson;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ClassroomQuestionBankTest {
    @TempDir Path directory;
    @Test void exactBindingsAndDraftOptInUseCanonicalAnswer() throws Exception {
        var json=new OutlineJson(new ObjectMapper());
        var question=new ClassroomQuestionBank.Question("synthetic","single","Synthetic prompt",List.of(new ClassroomQuestionBank.BankOption("a","One"),new ClassroomQuestionBank.BankOption("b","Two")),List.of("b"),"Synthetic canonical analysis",List.of("KP-SYNTHETIC"),"DRAFT");
        var path=directory.resolve("bank.json");
        Files.writeString(path,json.write(new ClassroomQuestionBank.Catalog(List.of(question))));
        assertTrue(new ClassroomQuestionBank(json,path.toString(),false).select(Set.of("KP-SYNTHETIC"),3).isEmpty());
        var bank=new ClassroomQuestionBank(json,path.toString(),true);
        assertEquals("b",bank.select(Set.of("KP-SYNTHETIC"),3).getFirst().answer().getFirst());
        assertTrue(bank.select(Set.of("KP-OTHER"),3).isEmpty());
        assertEquals("BANK_NOT_BOUND",assertThrows(IllegalStateException.class,()->bank.selectBound("u","Synthetic title")).getMessage());
        var bindings=directory.resolve("bindings.json");Files.writeString(bindings,json.write(Map.of("Synthetic title",List.of("KP-SYNTHETIC"))));
        org.springframework.test.util.ReflectionTestUtils.setField(bank,"bindingsPath",bindings.toString());
        assertEquals(question,bank.selectBound("u","Synthetic title").getFirst());
        assertThrows(IllegalStateException.class,()->bank.selectBound("u","Synthetic"));
    }
    private ClassroomCheckService.Proposed q(String title,String answer) {
        return new ClassroomCheckService.Proposed(title,List.of(new ClassroomCheckService.Option("a","One"),new ClassroomCheckService.Option("b","Two")),answer,"Frozen analysis");
    }
    @Test void canonicalQuestionsWinAndAiOnlyFillsMissingDistinctSlots() {
        var service=new ClassroomCheckService(null,null,null);
        var bank=List.of(q("bank1","b"),q("bank2","a"),q("bank3","b"));
        assertEquals(bank,service.merge(bank,List.of()));
        assertEquals(bank,service.merge(bank,List.of(q("bank1","a"))));
        var partial=service.merge(bank.subList(0,1),List.of(q("bank1","a"),q("ai2","a"),q("ai3","b")));
        assertEquals("b",partial.getFirst().answerOption());assertEquals("ai2",partial.get(1).prompt());
        assertEquals(3,service.merge(List.of(),bank).size());
        assertThrows(IllegalArgumentException.class,()->service.merge(List.of(bank.getFirst()),List.of(q("bank1","a"))));
    }
    @Test void bankCannotBypassPublicationOptionValidation() {
        var service=new ClassroomCheckService(null,new OutlineJson(new ObjectMapper()),null);
        var duplicate=new ClassroomCheckService.Proposed("Related",List.of(new ClassroomCheckService.Option("a","One"),new ClassroomCheckService.Option("a","Two")),"a","Explanation");
        assertThrows(IllegalArgumentException.class,()->service.validate(List.of(duplicate)));
        var duplicateText=new ClassroomCheckService.Proposed("Related",List.of(new ClassroomCheckService.Option("a","One"),new ClassroomCheckService.Option("b","One")),"a","Explanation");
        assertThrows(IllegalArgumentException.class,()->service.validate(List.of(duplicateText)));
        assertThrows(IllegalArgumentException.class,()->service.validate(List.of(q("x".repeat(801),"a"))));
    }
}
