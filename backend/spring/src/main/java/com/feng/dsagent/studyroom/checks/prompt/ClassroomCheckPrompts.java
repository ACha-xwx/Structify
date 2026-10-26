package com.feng.dsagent.studyroom.checks.prompt;

/** Fixed model prompts for classroom check / quiz generation. */
public final class ClassroomCheckPrompts {

    private ClassroomCheckPrompts() {}

    public static final String INSTRUCTION =
        "当前是知识点最后一个已确认片段。可附带checks数组，包含3道不同的相关单选小测题。每题{prompt,options:[{id,text}],answerOption,analysis}，2至4个唯一选项，只有一个正确答案；题目只依据当前目标和资料。没有把握时省略checks。正文仍须符合原协议。";
}
