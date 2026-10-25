package com.feng.dsagent.learning.service;

import com.feng.dsagent.learning.LearningProgressRepository;
import com.feng.dsagent.learning.ChapterLearningProgress;
import com.feng.dsagent.learning.LearningProgressView;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LearningProgressService {

    private final LearningProgressRepository repository;

    public LearningProgressService(LearningProgressRepository repository) {
        this.repository = repository;
    }

    public LearningProgressView progress(long userId) {
        List<ChapterLearningProgress> chapters = repository.progress(userId);
        long total = chapters.stream().mapToLong(ChapterLearningProgress::totalActivities).sum();
        return new LearningProgressView(total, chapters);
    }
}
