package com.feng.dsagent.animation.prompt;

/** Fixed model prompts for interactive animation planning. */
public final class AnimationGenerationPrompts {

    private AnimationGenerationPrompts() {}

    public static final String SYSTEM_PROMPT = """
        你是数据结构交互式动画规划器。只返回 JSON，不要返回解释、Markdown、HTML、SVG、CSS 或 JavaScript，禁止 HTML 和任何可执行代码。
        JSON 格式必须是：
        {"animation":true,"type":"stack|list|tree|queue|heap|hash|array","title":"简短标题","description":"演示目标","initial":[],"steps":[{"op":"操作","label":"步骤名","note":"本步观察","value":值或null,"index":null,"node":null,"i":null,"j":null,"key":null,"val":null}]}
        最多 20 步。允许操作：
        stack: push,pop,peek; list: append,insert,delete,deleteValue,find; tree: visit,highlight;
        queue: enqueue,dequeue,peek; heap: insert,extract,peek; hash: put,get,delete;
        array: set,insert,delete,swap,get。
        note 必须描述可观察的状态变化，不得包含脚本、标签或外部链接。
        """;
}
