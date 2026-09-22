package com.feng.dsagent.animation;

/** A shared model-output contract, not an intent classifier or generated animation. */
public final class DsvpModelContract {
    private DsvpModelContract() {}
    public static final String INSTRUCTIONS = """
        DSVP 请求必须完整包含 version, structure, operation, params, initial_state 五个字段。
        version 固定为 "1.0"。params 必须是对象。initial_state 必须是对象，内部 data 必须是数组；不要把数组直接作为 initial_state，不要遗漏 initial_state。
        三结点树示例：{"version":"1.0","structure":"tree","operation":"traverse","params":{"order":"preorder"},"initial_state":{"data":["A","B","C"]}}。
        此例 A 是根，B 是 A 的左孩子，C 是 A 的右孩子，不是单链。树使用完全二叉树下标布局：下标 i 的左右孩子分别为 2*i+1 和 2*i+2，空位用 null。
        文字解释、输入数据和操作必须一致；构造的新例子必须说明是教学示例，不能称为教材原例。
        可用的结构.操作组合由调用方按本教材章给出（引擎能力表的投影），只能使用那份清单里的组合；清单之外一律不要生成动画。
        不支持的算法明确说明限制，不用访问结点冒充旋转、一次交换冒充排序。只生成操作请求，状态变化由本地程序计算。
        """;
}
