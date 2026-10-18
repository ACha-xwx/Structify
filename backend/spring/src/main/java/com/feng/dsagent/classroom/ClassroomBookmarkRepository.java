package com.feng.dsagent.classroom;

import java.util.List;

/** A learner's saved steps. Every method is scoped by user id, so one account never reads another's. */
interface ClassroomBookmarkRepository {

    ClassroomBookmark save(long userId, ClassroomBookmarkDraft draft);

    List<ClassroomBookmark> listByUser(long userId);

    boolean delete(long userId, long id);
}
