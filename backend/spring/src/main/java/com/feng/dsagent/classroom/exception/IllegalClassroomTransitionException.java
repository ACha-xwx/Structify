package com.feng.dsagent.classroom.exception;

import com.feng.dsagent.classroom.domain.ClassroomAction;
import com.feng.dsagent.classroom.domain.ClassroomState;
public final class IllegalClassroomTransitionException extends IllegalStateException {

    private final ClassroomState state;
    private final ClassroomAction action;

    public IllegalClassroomTransitionException(
            ClassroomState state,
            ClassroomAction action,
            String reason) {
        super("Cannot apply " + action + " while classroom is " + state + ": " + reason);
        this.state = state;
        this.action = action;
    }

    public ClassroomState state() {
        return state;
    }

    public ClassroomAction action() {
        return action;
    }
}
