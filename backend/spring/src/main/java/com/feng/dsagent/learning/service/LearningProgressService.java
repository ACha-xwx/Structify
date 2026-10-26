package com.feng.dsagent.learning.service;

import com.feng.dsagent.learning.converter.LearningProgressConverter;
import com.feng.dsagent.learning.mapper.LearningProgressMapper;
import com.feng.dsagent.learning.mapper.LearningProgressMapper.ActivityRow;
import com.feng.dsagent.learning.mapper.LearningProgressMapper.ChapterRow;
import com.feng.dsagent.learning.vo.ChapterLearningProgressVO;
import com.feng.dsagent.learning.vo.LearningProgressVO;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LearningProgressService {

    private final LearningProgressMapper mapper;
    private final LearningProgressConverter converter;

    public LearningProgressService(LearningProgressMapper mapper, LearningProgressConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    public LearningProgressVO progress(long userId) {
        Map<String, Accumulator> chapters = new LinkedHashMap<>();
        for (ChapterRow chapter : mapper.selectPublishedChapters()) {
            chapters.put(chapter.id(), new Accumulator(chapter.id(), chapter.chapterNumber(), chapter.title()));
        }
        add(chapters, mapper.selectChatActivity(userId), ActivityType.CHAT);
        add(chapters, mapper.selectClassroomActivity(userId), ActivityType.CLASSROOM);
        add(chapters, mapper.selectAnimationActivity(userId), ActivityType.ANIMATION);
        add(chapters, mapper.selectCodeActivity(userId), ActivityType.CODE);
        add(chapters, mapper.selectEventActivity(userId), ActivityType.EVENT);

        List<ChapterLearningProgressVO> views = chapters.values().stream()
            .map(chapter -> converter.toView(
                chapter.chapterId,
                chapter.chapterNumber,
                chapter.title,
                chapter.chats,
                chapter.classrooms,
                chapter.animations,
                chapter.codeRuns,
                chapter.events,
                chapter.lastActivityAt
            ))
            .toList();
        long total = views.stream().mapToLong(ChapterLearningProgressVO::totalActivities).sum();
        return new LearningProgressVO(total, views);
    }

    private void add(Map<String, Accumulator> chapters, List<ActivityRow> rows, ActivityType type) {
        for (ActivityRow row : rows) {
            Accumulator chapter = chapters.get(row.chapterId());
            if (chapter != null) {
                chapter.add(type, row.activityCount(), instant(row.lastAt()));
            }
        }
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private enum ActivityType {
        CHAT,
        CLASSROOM,
        ANIMATION,
        CODE,
        EVENT
    }

    private static final class Accumulator {
        private final String chapterId;
        private final int chapterNumber;
        private final String title;
        private long chats;
        private long classrooms;
        private long animations;
        private long codeRuns;
        private long events;
        private Instant lastActivityAt;

        private Accumulator(String chapterId, int chapterNumber, String title) {
            this.chapterId = chapterId;
            this.chapterNumber = chapterNumber;
            this.title = title;
        }

        private void add(ActivityType type, long count, Instant activityAt) {
            switch (type) {
                case CHAT -> chats += count;
                case CLASSROOM -> classrooms += count;
                case ANIMATION -> animations += count;
                case CODE -> codeRuns += count;
                case EVENT -> events += count;
            }
            if (activityAt != null && (lastActivityAt == null || activityAt.isAfter(lastActivityAt))) {
                lastActivityAt = activityAt;
            }
        }
    }
}
