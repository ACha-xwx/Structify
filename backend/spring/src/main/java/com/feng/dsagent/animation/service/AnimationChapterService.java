package com.feng.dsagent.animation.service;

import com.feng.dsagent.animation.error.AnimationErrorCode;
import com.feng.dsagent.animation.mapper.ChapterLookupMapper;
import org.springframework.stereotype.Service;

@Service
public class AnimationChapterService {

    private final ChapterLookupMapper chapters;

    public AnimationChapterService(ChapterLookupMapper chapters) {
        this.chapters = chapters;
    }

    public String requirePublishedTitle(String chapterId) {
        String title = chapters.findPublishedTitle(chapterId);
        if (title == null || title.isBlank()) {
            throw AnimationErrorCode.CHAPTER_NOT_FOUND.toException();
        }
        return title;
    }
}
