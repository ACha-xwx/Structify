package com.feng.dsagent.studyroom;

import java.util.Map;
import org.bsc.langgraph4j.state.AgentState;

class StudyroomGraphState extends AgentState {

    static final String USER_ID = "userId";
    static final String CHAPTER_ID = "chapterId";
    static final String QUESTION = "question";
    static final String AUDIENCE = "audience";
    static final String REQUEST_ID = "requestId";
    static final String EVIDENCE_IDS = "evidenceIds";
    static final String EVIDENCE_CONTEXT = "evidenceContext";
    static final String RESOURCE_SCOPE = "resourceScope";
    static final String CANDIDATES_JSON = "candidatesJson";
    static final String OUTPUT_JSON = "outputJson";
    static final String VALIDATION_ERROR = "validationError";
    static final String REPAIR_COUNT = "repairCount";
    static final String ROOM_ID = "roomId";
    static final String FAILURE_CODE = "failureCode";
    static final String FAILURE_STATUS = "failureStatus";
    static final String FAILURE_MESSAGE = "failureMessage";

    StudyroomGraphState(Map<String, Object> data) {
        super(data);
    }

    String string(String key) {
        Object value = value(key).orElse("");
        return value instanceof String text ? text : String.valueOf(value);
    }

    long number(String key) {
        Object value = value(key).orElse(0L);
        return value instanceof Number number ? number.longValue() : Long.parseLong(String.valueOf(value));
    }
}
