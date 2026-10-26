package com.feng.dsagent.compiler.support;

import java.util.List;
import org.springframework.stereotype.Component;

import com.feng.dsagent.compiler.domain.ClassroomCodeLesson;
@Component
public final class ClassroomCodeStudyroomCatalog {

    private final ClassroomCodeCatalog catalog;

    public ClassroomCodeStudyroomCatalog(ClassroomCodeCatalog catalog) {
        this.catalog = catalog;
    }

    public List<ClassroomCodeLesson> lessons() {
        return catalog.lessons();
    }
}
