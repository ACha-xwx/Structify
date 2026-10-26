package com.feng.dsagent.studyroom.draft;

import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

import com.feng.dsagent.studyroom.draft.support.OutlineIncrementalParser;
import com.feng.dsagent.studyroom.draft.support.OutlineJson;
class OutlineIncrementalParserTest {
    final OutlineJson json=new OutlineJson(JsonMapper.builder().build());
    @Test void emitsOnlyClosedRootUnitsAcrossEveryCharacterBoundary() {
        var units=new ArrayList<Map<String,Object>>();
        var parser=new OutlineIncrementalParser(json,units::add);
        String prefix="{\"metadata\":{\"units\":[{\"title\":\"假单元\"}]},\"units\":[{\"title\":\"栈\\\"[]\",\"objective\":\"理解 {LIFO}\",\"materials\":[{\"kind\":\"PPT\"}]}";
        for(char c:prefix.toCharArray()) parser.accept(String.valueOf(c));
        assertEquals(1,units.size());assertEquals("栈\"[]",units.getFirst().get("title"));
        parser.accept(",{\"title\":\"未完成");assertEquals(1,units.size());
        parser.accept("\",\"objective\":\"实现\"}]}");assertEquals(2,units.size());
    }
    @Test void unfinishedObjectNeverBecomesPreview() {
        var units=new ArrayList<Map<String,Object>>();
        new OutlineIncrementalParser(json,units::add).accept("{\"units\":[{\"title\":\"栈\",\"objective\":\"理解\"");
        assertTrue(units.isEmpty());
    }
    @Test void refusesOversizedPreview() {
        var parser=new OutlineIncrementalParser(json,x->{});
        assertThrows(RuntimeException.class,()->parser.accept("{\"units\":[{\"title\":\""+"栈".repeat(6000)+"\",\"objective\":\"目标\"}]"));
    }
}
