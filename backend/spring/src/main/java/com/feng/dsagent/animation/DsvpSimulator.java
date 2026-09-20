package com.feng.dsagent.animation;

import com.feng.dsagent.common.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Executes bounded teaching operations. The model never supplies state snapshots. */
final class DsvpSimulator {
    record Result(List<Object> initial, List<AnimationStep> steps, List<List<Object>> states,
                  List<List<Integer>> edges, List<Integer> visited) {}
    private final List<Object> values;
    private final List<AnimationStep> steps = new ArrayList<>();
    private final List<List<Object>> states = new ArrayList<>();
    private final List<List<Integer>> edges = new ArrayList<>();
    private final List<Integer> visited = new ArrayList<>();
    private final JsonNode params;
    private final int capacity;
    private final ObjectMapper mapper;

    private DsvpSimulator(List<Object> initial, JsonNode params, int capacity, ObjectMapper mapper) {
        this.values = new ArrayList<>(initial); this.params = params; this.capacity = capacity; this.mapper = mapper;
    }
    static Result run(String structure, String operation, JsonNode input, int capacity, ObjectMapper mapper) {
        List<Object> initial = new ArrayList<>();
        for (JsonNode item : input.path("initial_state").path("data")) initial.add(mapper.convertValue(item, Object.class));
        DsvpSimulator sim = new DsvpSimulator(initial, input.path("params"), capacity, mapper);
        if (structure.equals("sequential_list") && operation.equals("merge")) {
            if (initial.size() != 2 || !(initial.get(0) instanceof List<?> left) || !(initial.get(1) instanceof List<?> right)) fail("合并需要两个有序数组");
            List<?> left = (List<?>) initial.get(0), right = (List<?>) initial.get(1);
            ordered(left); ordered(right);
            if (left.size() + right.size() > capacity) fail("合并结果超过容量");
            sim.values.clear(); initial = List.of();
            int i = 0, j = 0;
            while (i < left.size() || j < right.size()) {
                boolean takeLeft = j == right.size() || (i < left.size() && compare(left.get(i), right.get(j)) <= 0);
                Object value = takeLeft ? left.get(i++) : right.get(j++);
                sim.values.add(value);
                sim.emit("insert", "归并写入", "比较两表当前元素，取" + (takeLeft ? "左表" : "右表") + "元素；相等时先取左表，保留重复值。", value, sim.values.size() - 1);
            }
        } else if (structure.equals("graph")) sim.graph(operation, input.path("initial_state").path("metadata"));
        else if (structure.equals("tree")) sim.tree(operation);
        else if (structure.equals("heap")) sim.heap(operation);
        else if (structure.equals("hash")) sim.hash(operation);
        else sim.linear(structure, operation);
        if (sim.steps.isEmpty()) sim.emit("get", "完成", "结构没有发生变化。", null, null);
        return new Result(Collections.unmodifiableList(new ArrayList<>(initial)), List.copyOf(sim.steps),
            List.copyOf(sim.states), List.copyOf(sim.edges), List.copyOf(sim.visited));
    }
    private static void ordered(List<?> values) {
        for (int i = 0; i < values.size(); i++) {
            compare(values.get(i), values.get(i));
            if (i > 0 && compare(values.get(i - 1), values.get(i)) > 0) fail("合并输入必须非递减有序");
        }
    }
    private static int compare(Object a, Object b) {
        if (!(a instanceof Number x) || !(b instanceof Number y)) throw error("此算法需要有限数值");
        if (!Double.isFinite(x.doubleValue()) || !Double.isFinite(y.doubleValue())) fail("此算法需要有限数值");
        return new java.math.BigDecimal(x.toString()).compareTo(new java.math.BigDecimal(y.toString()));
    }
    private int integer(String key, Integer fallback) {
        JsonNode node = params.path(key);
        if (node.isMissingNode()) { if (fallback == null) throw error("缺少参数 " + key); return fallback; }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) throw error(key + " 必须是整数");
        return node.asInt();
    }
    private int index(boolean insert) {
        int index = params.has("index") ? integer("index", null) : integer("position", null) - 1;
        if (index < 0 || index >= values.size() + (insert ? 1 : 0)) fail("操作位置超出当前结构范围");
        return index;
    }
    private Object value() {
        JsonNode node = params.get("value");
        if (node == null || node.isNull() || node.isArray() || node.isObject()) throw error("value 必须是非空标量");
        return mapper.convertValue(node, Object.class);
    }
    private void room() { if (values.size() >= capacity) fail("结构已满，不能继续插入"); }
    private void nonempty() { if (values.isEmpty()) fail("空结构不能执行该操作"); }
    private void emit(String op, String label, String note, Object value, Integer index) {
        if (steps.size() >= 256) fail("演示步骤过多，请减小输入规模");
        steps.add(new AnimationStep(op, label, note, value, index, index, null, null, null, null, values));
        states.add(Collections.unmodifiableList(new ArrayList<>(values)));
    }
    private void linear(String structure, String op) {
        switch (op) {
            case "push", "enqueue", "append", "insert" -> {
                room(); Object value = value();
                int at = op.equals("insert") ? index(true) : values.size();
                if (structure.equals("linked_list")) {
                    values.add(at, value); emit(op, "连接新结点", "先让新结点指向原后继，再让前驱连接新结点；无需移动其他结点的数据。", value, at); break;
                }
                // Materialize each shift so movement direction is visible and testable.
                if (at < values.size()) {
                    values.add(values.getLast());
                    emit("insert", "复制表尾", "从后向前移动，避免覆盖尚未复制的元素。", values.getLast(), values.size() - 1);
                    for (int k = values.size() - 2; k > at; k--) {
                        values.set(k, values.get(k - 1)); emit("set", "元素后移", "将前一个位置复制到当前位置。", values.get(k), k);
                    }
                    values.set(at, value); emit("set", "写入新元素", "所有待移动元素已保存，再写入目标位置。", value, at);
                } else { values.add(value); emit(op, "插入元素", "在末尾写入新元素。", value, at); }
            }
            case "pop", "dequeue", "delete" -> {
                nonempty(); int at = op.equals("pop") ? values.size() - 1 : op.equals("dequeue") ? 0 : index(false);
                if (structure.equals("linked_list") || op.equals("pop") || op.equals("dequeue")) {
                    Object removed = values.remove(at); emit(op, "移除元素 " + removed, structure.equals("linked_list") ? "前驱跳过被删结点并连接其后继，不移动其他结点的数据。" : "从" + (op.equals("pop") ? "栈顶" : "队头") + "移除元素，展示剩余逻辑序列。", null, at); break;
                }
                for (int k = at; k < values.size() - 1; k++) {
                    values.set(k, values.get(k + 1)); emit("set", "元素前移", "从前向后复制后继，填补空位。", values.get(k), k);
                }
                int last = values.size() - 1; values.removeLast(); emit("delete", "缩短表长", "移除末尾的重复存储位置。", null, last);
            }
            case "set" -> { int at = index(false); Object value = value(); values.set(at, value); emit(op, "写入", "更新指定位置。", value, at); }
            case "swap" -> {
                int i = integer("i", null), j = integer("j", null);
                if (i < 0 || j < 0 || i >= values.size() || j >= values.size()) fail("交换下标越界");
                Collections.swap(values, i, j); emit("swap", "交换", "交换下标 " + i + " 与 " + j + " 的值。", null, i);
            }
            case "peek", "get" -> {
                nonempty(); int at = op.equals("get") ? index(false) : structure.equals("stack") ? values.size() - 1 : 0;
                emit(op, "读取", "只读取，不改变结构。", values.get(at), at);
            }
            case "find" -> {
                Object target = value(); boolean found = false;
                for (int i = 0; i < values.size(); i++) {
                    found = Objects.equals(values.get(i), target);
                    emit("find", found ? "找到元素" : "比较元素", found ? "目标位于下标 " + i : "当前元素不是目标，继续检查后继。", target, i);
                    if (found) break;
                }
                if (!found) emit("find", "未找到", "已检查全部元素，目标不存在。", target, null);
            }
            default -> fail("未支持的线性结构操作");
        }
    }
    private void heap(String op) {
        for (Object value : values) compare(value, value);
        for (int i = values.size() / 2 - 1; i >= 0; i--) sink(i);
        if (op.equals("insert")) {
            room(); Object value = value(); compare(value, value); values.add(value); emit("insert", "插入堆尾", "小顶堆：先放到完全二叉树的末尾。", value, values.size() - 1);
            for (int i = values.size() - 1; i > 0;) {
                int parent = (i - 1) / 2; if (compare(values.get(parent), values.get(i)) <= 0) break;
                Collections.swap(values, parent, i); emit("swap", "上浮", "孩子小于父结点，交换以恢复小顶堆序。", null, parent); i = parent;
            }
        } else if (op.equals("extract")) {
            nonempty(); Object root = values.getFirst(), tail = values.removeLast();
            if (!values.isEmpty()) values.set(0, tail);
            emit("extract", "取出堆顶", "取出 " + root + "，末尾元素补到根部。", null, 0); sink(0);
        } else { nonempty(); emit("peek", "读取堆顶", "小顶堆的最小元素位于根部。", values.getFirst(), 0); }
        for (int i = 1; i < values.size(); i++) edges.add(List.of((i - 1) / 2, i));
    }
    private void sink(int index) {
        while (index * 2 + 1 < values.size()) {
            int child = index * 2 + 1;
            if (child + 1 < values.size() && compare(values.get(child + 1), values.get(child)) < 0) child++;
            if (compare(values.get(index), values.get(child)) <= 0) break;
            Collections.swap(values, index, child); emit("swap", "下沉", "与较小的孩子交换，恢复小顶堆序。", null, child); index = child;
        }
    }
    private void hash(String op) {
        String key = params.path("key").asText("").trim(); if (key.isEmpty()) fail("缺少哈希键 key");
        // Initial entries are explicit {key,val} objects, not guessed from text.
        int found = -1;
        for (int i = 0; i < values.size(); i++) {
            if (!(values.get(i) instanceof Map<?, ?> item) || !item.containsKey("key") || !item.containsKey("val")) fail("哈希初始数据应为 {key,val} 对象数组");
            Map<?, ?> item = (Map<?, ?>) values.get(i);
            if (key.equals(String.valueOf(item.get("key")))) found = i;
        }
        if (op.equals("put")) {
            if (!params.has("val") || !params.get("val").isValueNode() || params.get("val").isNull()) fail("缺少哈希值 val");
            Map<String, Object> entry = Map.of("key", key, "val", mapper.convertValue(params.get("val"), Object.class));
            if (found < 0) { room(); values.add(entry); found = values.size() - 1; } else values.set(found, entry);
        } else if (op.equals("delete") && found >= 0) values.remove(found);
        emit(op, found < 0 ? "键不存在" : "处理键 " + key, "展示键值表的逻辑状态；不模拟特定哈希函数的桶布局。", null, found < 0 ? null : found);
    }
    private void tree(String op) {
        for (int i = 1; i < values.size(); i++) if (values.get(i) != null) {
            if (values.get((i - 1) / 2) == null) fail("非空树结点不能拥有空父结点"); edges.add(List.of((i - 1) / 2, i));
        }
        if (!op.equals("traverse")) { visit(integer("node", 0)); return; }
        String order = params.path("order").asText("inorder");
        if (!Set.of("preorder", "inorder", "postorder", "levelorder").contains(order)) fail("未知遍历顺序");
        if (order.equals("levelorder")) { for (int i = 0; i < values.size(); i++) if (values.get(i) != null) visit(i); }
        else walk(0, order);
    }
    private void walk(int node, String order) {
        if (node >= values.size() || values.get(node) == null) return;
        if (order.equals("preorder")) visit(node); walk(node * 2 + 1, order);
        if (order.equals("inorder")) visit(node); walk(node * 2 + 2, order);
        if (order.equals("postorder")) visit(node);
    }
    private void visit(int node) {
        if (node < 0 || node >= values.size() || values.get(node) == null) fail("访问结点不存在");
        visited.add(node); emit("visit", "访问结点 " + values.get(node), "访问次序：" + visited, null, node);
    }
    private void graph(String op, JsonNode metadata) {
        JsonNode source = params.has("edges") ? params.path("edges") : metadata.path("edges");
        if (!source.isArray()) fail("图演示需要 edges 邻接边数组");
        List<List<Integer>> adjacent = new ArrayList<>(); for (Object ignored : values) adjacent.add(new ArrayList<>());
        for (JsonNode edge : source) {
            if (!edge.isArray() || edge.size() != 2 || !edge.get(0).isIntegralNumber() || !edge.get(1).isIntegralNumber()) fail("每条边必须是 [起点下标,终点下标]");
            int a = edge.get(0).asInt(), b = edge.get(1).asInt();
            if (a < 0 || b < 0 || a >= values.size() || b >= values.size()) fail("图边引用了不存在的结点");
            edges.add(List.of(a,b)); adjacent.get(a).add(b);
            if (!params.path("directed").asBoolean(false)) adjacent.get(b).add(a);
        }
        if (values.isEmpty()) return;
        int start = integer("node", 0); if (start < 0 || start >= values.size()) fail("起点不存在");
        if (op.equals("visit") || op.equals("highlight")) { visit(start); return; }
        if (op.equals("dfs")) { dfs(start, adjacent, new HashSet<>()); return; }
        Set<Integer> seen = new HashSet<>(); Deque<Integer> queue = new ArrayDeque<>(); queue.add(start); seen.add(start);
        while (!queue.isEmpty()) { int node = queue.removeFirst(); visit(node); for (int next : adjacent.get(node)) if (seen.add(next)) queue.addLast(next); }
    }
    private void dfs(int node, List<List<Integer>> adjacent, Set<Integer> seen) {
        if (!seen.add(node)) return; visit(node); for (int next : adjacent.get(node)) dfs(next, adjacent, seen);
    }
    private static ApiException error(String message) { return new ApiException(HttpStatus.BAD_REQUEST, "DSVP_EXECUTION_INVALID", message); }
    private static void fail(String message) { throw error(message); }
}
