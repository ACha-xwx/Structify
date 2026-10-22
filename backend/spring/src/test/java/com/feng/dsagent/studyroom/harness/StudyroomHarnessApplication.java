package com.feng.dsagent.studyroom.harness;

import com.feng.dsagent.DsAgentServerApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** Test-classpath-only server for browser regression; never shipped in the application jar. */
public final class StudyroomHarnessApplication {
    public static void main(String[] args) {
        new SpringApplicationBuilder(DsAgentServerApplication.class,StudyroomHarnessConfiguration.class)
            .profiles("test","harness").run(args);
    }
}
