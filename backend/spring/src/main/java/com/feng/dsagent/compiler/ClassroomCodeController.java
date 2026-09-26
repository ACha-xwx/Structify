package com.feng.dsagent.compiler;

import com.feng.dsagent.common.ApiException;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public ClassroomCodeSamplesResponse samples(
        @RequestParam(name = "coursewareKey", required = false) String coursewareKey
    ) {
        if (coursewareKey == null || coursewareKey.isBlank()) {
            return new ClassroomCodeSamplesResponse(catalog.lessons(), catalog.sampleCount());
        }
        ClassroomCodeLesson lesson = catalog.lesson(coursewareKey)
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND,
                "CODE_SAMPLES_LESSON_UNKNOWN",
                "该课时还没有配套的课堂代码"
            ));
        return new ClassroomCodeSamplesResponse(List.of(lesson), lesson.samples().size());
    }

    /**
     * Every listing that ships with the app, so a lesson without a runnable sample still leaves the
     * learner with the class code - and a runnable example around it - in front of them.
     */
    @GetMapping("/library")
    public TextbookCodeLibraryResponse library() {
        return new TextbookCodeLibraryResponse(library.chapters(), library.fragmentCount());
    }
}
