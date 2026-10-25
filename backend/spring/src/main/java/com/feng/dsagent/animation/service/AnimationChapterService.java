package com.feng.dsagent.animation.service;

import com.feng.dsagent.animation.mapper.ChapterLookupMapper;
import com.feng.dsagent.common.ApiException;
import org.springframework.http.HttpStatus;
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
            throw new ApiException(HttpStatus.NOT_FOUND, "CHAPTER_NOT_FOUND", "章节未发布或不存在");
        }
        return title;
    }
}
