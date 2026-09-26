package com.feng.dsagent.classroom;

public enum ClassroomAction {
    ASK,
    ANSWER,
    /** A nudge towards the answer that leaves the question open and the cursor where it is. */
    HINT,
    /** Resolves the question with the explanation instead of an answer, within a per-lesson budget. */
    SKIP,
    PAUSE,
    RESUME,
    CONTINUE,
    FINISH
}
