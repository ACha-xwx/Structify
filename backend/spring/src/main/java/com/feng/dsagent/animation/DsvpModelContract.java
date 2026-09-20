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
        支持 stack push/pop/peek，queue enqueue/dequeue/peek，sequential_list insert/delete/merge，linked_list append/insert/delete/find，array set/insert/delete/swap/get，heap insert/extract/peek（小顶堆），hash put/get/delete（逻辑键值表），tree traverse/visit，graph bfs/dfs。
        插入/删除/数组访问需要 index（0起）；插入和 find 需要 value；swap 需要 i,j；merge 的 data 是两个非递减数值数组，capacity 必须足够容纳；hash data 为 {key,val} 数组，params 使用 key/val；tree traverse 的 order 为 preorder/inorder/postorder/levelorder；graph data 为顶点值数组，params.edges 是下标边数组、node 是起点。
        不支持的算法明确说明限制，不用访问结点冒充旋转、一次交换冒充排序。只生成操作请求，状态变化由本地程序计算。
        """;
}
