package com.feng.dsagent.compiler;

/**
 * The header every interactive build is compiled with.
 *
 * <p>A C program writing to a pipe buffers its output, so a prompt like {@code printf("请输入")}
 * sits in the buffer and never reaches the console. Switching standard output to unbuffered mode
 * before {@code main} runs fixes that without allocating a pseudo terminal, which is what makes
 * this sandbox cheap enough to run inside the API process.
 */
final class InteractiveRuntimeHeader {

    static final String CONTENT = """
        #ifndef STRUCTIFY_IO_H
        #define STRUCTIFY_IO_H
        #include <stdio.h>
        __attribute__((constructor)) static void structify_unbuffer(void) {
            setvbuf(stdout, NULL, _IONBF, 0);
            setvbuf(stderr, NULL, _IONBF, 0);
        }
        #endif
        """;

    private InteractiveRuntimeHeader() {
    }
}
