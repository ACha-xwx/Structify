package com.feng.dsagent.learning;

import java.util.List;

public interface LearningProgressRepository {

    public List<ChapterLearningProgress> progress(long userId);
}
