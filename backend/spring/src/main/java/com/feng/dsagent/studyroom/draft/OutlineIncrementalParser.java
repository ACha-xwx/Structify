package com.feng.dsagent.studyroom.draft;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/** Publishes closed objects in the root units array, never repairs an incomplete JSON prefix. */
public final class OutlineIncrementalParser {
    private final OutlineJson json;
    private final Consumer<Map<String,Object>> consumer;
    private final StringBuilder buffer=new StringBuilder();
    private int depth, unitsDepth=-1, objectStart=-1, emitted;
    private boolean quoted, escaped, unitsSeen;
    private String rootKey;
    private final StringBuilder token=new StringBuilder();
    public OutlineIncrementalParser(OutlineJson json,Consumer<Map<String,Object>> consumer) {this.json=json;this.consumer=consumer;}
    public void accept(String delta) {
        for(char c:delta.toCharArray()) {
            buffer.append(c);
            OutlineJson.require(buffer.length()<=64000,"MODEL_OUTPUT_TOO_LARGE","大纲输出过长");
            if(quoted) {
                if(escaped) {token.append(c);escaped=false;}
                else if(c=='\\') {token.append(c);escaped=true;}
                else if(c=='"') {quoted=false;if(depth==1)rootKey=token.toString();}
                else token.append(c);
                continue;
            }
            if(c=='"') {quoted=true;token.setLength(0);continue;}
            if(c=='{' || c=='[') {
                if(c=='[' && depth==1 && "units".equals(rootKey)) {
                    OutlineJson.require(!unitsSeen,"OUTLINE_PREVIEW_INVALID","大纲重复定义知识点");
                    unitsSeen=true;unitsDepth=depth+1;
                } else if(c=='{' && depth==unitsDepth) objectStart=buffer.length()-1;
                depth++;
            } else if(c=='}' || c==']') {
                if(c=='}' && objectStart>=0 && depth==unitsDepth+1) {
                    String raw=buffer.substring(objectStart);
                    OutlineJson.require(raw.getBytes(StandardCharsets.UTF_8).length<=16384 && emitted<24,"OUTLINE_PREVIEW_TOO_LARGE","大纲预览超出上限");
                    var unit=json.object(raw);
                    OutlineJson.require(unit.get("title") instanceof String && unit.get("objective") instanceof String,"OUTLINE_PREVIEW_INVALID","知识点预览缺少标题或目标");
                    consumer.accept(unit);emitted++;objectStart=-1;
                }
                if(c==']' && depth==unitsDepth)unitsDepth=-1;
                depth--;
            } else if(c==',' && depth==1) rootKey=null;
        }
    }
}
