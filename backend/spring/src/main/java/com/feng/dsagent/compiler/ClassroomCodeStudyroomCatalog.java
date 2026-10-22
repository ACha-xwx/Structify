package com.feng.dsagent.compiler;

import java.util.List;
import org.springframework.stereotype.Component;

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
