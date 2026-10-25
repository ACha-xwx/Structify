package com.feng.dsagent.classroom;

import java.util.List;

/** A learner's saved steps. Every method is scoped by user id, so one account never reads another's. */
public interface ClassroomBookmarkRepository {

    public ClassroomBookmark save(long userId, ClassroomBookmarkDraft draft);

    public List<ClassroomBookmark> listByUser(long userId);

    public boolean delete(long userId, long id);
}
