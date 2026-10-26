package com.feng.dsagent.compiler.controller;

import com.feng.dsagent.compiler.vo.ClassroomCodeSamplesVO;
import com.feng.dsagent.compiler.vo.TextbookCodeLibraryVO;
import com.feng.dsagent.compiler.support.ClassroomCodeCatalog;
import com.feng.dsagent.compiler.domain.ClassroomCodeLesson;
import com.feng.dsagent.compiler.support.TextbookCodeLibrary;

import com.feng.dsagent.compiler.error.CompilerErrorCode;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only code catalogue for the editor: the runnable classroom samples and the class listings
 * (each with its runnable example). Deliberately anonymous, like the rest of
 * {@code /api/v1/code/**}: the editor may be opened without signing in.
 */
@RestController
@RequestMapping("/api/v1/code")
public final class ClassroomCodeController {

    private final ClassroomCodeCatalog catalog;
    private final TextbookCodeLibrary library;

    @Autowired
    public ClassroomCodeController(ClassroomCodeCatalog catalog, TextbookCodeLibrary library) {
        this.catalog = catalog;
        this.library = library;
    }

    @GetMapping("/samples")
    public ClassroomCodeSamplesVO samples(
        @RequestParam(name = "coursewareKey", required = false) String coursewareKey
    ) {
        if (coursewareKey == null || coursewareKey.isBlank()) {
            return new ClassroomCodeSamplesVO(catalog.lessons(), catalog.sampleCount());
        }
        ClassroomCodeLesson lesson = catalog.lesson(coursewareKey)
            .orElseThrow(() -> CompilerErrorCode.CODE_SAMPLES_LESSON_UNKNOWN.toException());
        return new ClassroomCodeSamplesVO(List.of(lesson), lesson.samples().size());
    }

    /**
     * Every listing that ships with the app, so a lesson without a runnable sample still leaves the
     * learner with the class code - and a runnable example around it - in front of them.
     */
    @GetMapping("/library")
    public TextbookCodeLibraryVO library() {
        return new TextbookCodeLibraryVO(library.chapters(), library.fragmentCount());
    }
}
