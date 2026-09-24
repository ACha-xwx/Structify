"use strict";

/**
 * 教材动画共用的输入校验。
 *
 * 这些校验在模拟器执行阶段抛出；dsvp-service 会把带 code 的错误结构化地返回给调用方，
 * 所以这里所有错误都必须给出“用户能看懂、能自己改”的中文信息（指出哪个参数、当前值、合法范围）。
 */
class SimulationInputError extends Error {
  constructor(code, message, detail = "") {
    super(message);
    this.name = "SimulationInputError";
    this.code = code;
    this.detail = detail;
  }
}

/** 只读自有属性，避免 __proto__/constructor/toString 等原型链键绕过注册表判定。 */
function ownOf(map, key) {
  return Object.hasOwn(map, key) ? map[key] : undefined;
}

/**
 * 严格整数参数：未提供时用 fallback；提供了就必须是整数且落在 [min, max] 内，
 * 否则抛出带范围的错误（不再静默夹取）。
 */
function strictInt(params, name, fallback, min = -1e9, max = 1e9) {
  const raw = params?.[name];
  if (raw === undefined || raw === null || raw === "") return fallback;
  const n = Number(raw);
  if (!Number.isInteger(n)) {
    throw new SimulationInputError("INVALID_PARAM", `参数 ${name}=${JSON.stringify(raw)} 必须是整数`, name);
  }
  if (n < min || n > max) {
    throw new SimulationInputError("PARAM_OUT_OF_RANGE", `参数 ${name}=${n} 超出支持范围 [${min}, ${max}]`, name);
  }
  return n;
}

/**
 * 并查集 parent 数组校验：整数、正指针指向其他下标（根用负数表示集合大小）、无环。
 * 返回规范化后的数组；输入不是非空数组时返回 null，由调用方决定默认行为。
 */
function normalizeParentArray(raw) {
  if (!Array.isArray(raw) || raw.length === 0) return null;
  const n = raw.length;
  if (n > 60) {
    throw new SimulationInputError("INPUT_TOO_LARGE", `并查集最多演示 60 个元素，当前 ${n} 个，请缩小示例`, "parent");
  }
  const parent = raw.map((value, index) => {
    const x = Number(value);
    if (!Number.isInteger(x)) {
      throw new SimulationInputError("INVALID_PARENT", `parent[${index}]=${JSON.stringify(value)} 必须是整数`, `parent[${index}]`);
    }
    if (x >= n) {
      throw new SimulationInputError("INVALID_PARENT", `parent[${index}]=${x} 越界：下标范围是 0~${n - 1}`, `parent[${index}]`);
    }
    if (x >= 0 && x === index) {
      throw new SimulationInputError(
        "INVALID_PARENT",
        `parent[${index}]=${x} 自指无效：正指针必须指向其他元素，集合的根应以负数（集合大小）表示`,
        `parent[${index}]`
      );
    }
    if (x < 0 && -x > n) {
      throw new SimulationInputError(
        "INVALID_PARENT",
        `parent[${index}]=${x} 的负值表示集合大小，不能超过元素总数 ${n}`,
        `parent[${index}]`
      );
    }
    return x;
  });
  for (let i = 0; i < n; i++) {
    let cur = i;
    let hops = 0;
    while (parent[cur] >= 0) {
      cur = parent[cur];
      hops++;
      if (hops > n) {
        throw new SimulationInputError(
          "INVALID_PARENT",
          `parent 数组存在环：从元素 ${i} 出发沿指针走了 ${hops} 步仍未到达根（根应以负数表示）`,
          `parent[${i}]`
        );
      }
    }
  }
  return parent;
}

/**
 * 图输入规范化：顶点非空且不重复、边端点必须都在顶点集中、规模有上限、directed 统一读取。
 * 未提供顶点/边时不再静默回填演示数据，而是报错让调用方补全。
 */
function normalizeGraphSpec(request, defaults = {}) {
  const params = request.params || {};
  const state = request.initial_state?.data;
  const stateObj = state && typeof state === "object" && !Array.isArray(state) ? state : {};
  // 核心契约（contracts/dsvp.schema.json）把顶点值放在 initial_state.data、边端点用下标；
  // 教材路径把顶点/边都放 params.nodes/params.edges。两处都认。
  const rawNodes = params.nodes !== undefined ? params.nodes
    : stateObj.nodes !== undefined ? stateObj.nodes
    : Array.isArray(state) ? state : undefined;
  const rawEdges = params.edges !== undefined ? params.edges : stateObj.edges;
  if (!Array.isArray(rawNodes) || rawNodes.length === 0) {
    throw new SimulationInputError("EMPTY_GRAPH", "顶点列表 nodes 不能为空：请提供图的全部顶点", "nodes");
  }
  if (rawNodes.length > 16) {
    throw new SimulationInputError(
      "INPUT_TOO_LARGE",
      `图动画最多演示 16 个顶点，当前 ${rawNodes.length} 个，请缩小示例`,
      "nodes"
    );
  }
  const nodes = [];
  const nodeSet = new Set();
  for (const item of rawNodes) {
    const label = String(item);
    if (nodeSet.has(label)) {
      throw new SimulationInputError("DUPLICATE_NODE", `顶点重复：${label}（每个顶点只能出现一次）`, label);
    }
    nodeSet.add(label);
    nodes.push(label);
  }
  if (rawEdges !== undefined && !Array.isArray(rawEdges)) {
    throw new SimulationInputError("INVALID_EDGES", "边列表 edges 必须是数组", "edges");
  }
  const rawEdgeList = Array.isArray(rawEdges) ? rawEdges : [];
  if (rawEdgeList.length > 40) {
    throw new SimulationInputError("INPUT_TOO_LARGE", `图动画最多演示 40 条边，当前 ${rawEdgeList.length} 条，请缩小示例`, "edges");
  }
  const edges = [];
  rawEdgeList.forEach((edge, index) => {
    let from;
    let to;
    let weight;
    if (Array.isArray(edge)) {
      [from, to, weight] = edge;
      if (weight === undefined) weight = 1;
    } else if (edge && typeof edge === "object") {
      from = edge.from;
      to = edge.to;
      weight = edge.weight === undefined ? 1 : edge.weight;
    } else {
      throw new SimulationInputError(
        "INVALID_EDGES",
        `第 ${index + 1} 条边 ${JSON.stringify(edge)} 格式不正确：应为 [起点, 终点, 权值] 或 {from, to, weight}`,
        `edges[${index}]`
      );
    }
    // 边端点兼容两种写法：顶点标签，或指向 initial_state.data/params.nodes 的整数下标。
    // 只有当下标不在顶点集内、且落在顶点下标范围内时才按下标解释，避免与纯数字标签冲突。
    const resolveEndpoint = (endpoint) => {
      if (typeof endpoint === "number" && Number.isInteger(endpoint) && !nodeSet.has(String(endpoint))
        && endpoint >= 0 && endpoint < nodes.length) {
        return nodes[endpoint];
      }
      return endpoint;
    };
    from = String(resolveEndpoint(from));
    to = String(resolveEndpoint(to));
    if (!nodeSet.has(from)) {
      throw new SimulationInputError(
        "UNKNOWN_VERTEX",
        `第 ${index + 1} 条边引用了不存在的顶点 ${from}（顶点集：${nodes.join("、")}）`,
        `edges[${index}]`
      );
    }
    if (!nodeSet.has(to)) {
      throw new SimulationInputError(
        "UNKNOWN_VERTEX",
        `第 ${index + 1} 条边引用了不存在的顶点 ${to}（顶点集：${nodes.join("、")}）`,
        `edges[${index}]`
      );
    }
    const w = Number(weight);
    if (!Number.isFinite(w)) {
      throw new SimulationInputError("INVALID_EDGES", `第 ${index + 1} 条边的权值 ${JSON.stringify(weight)} 不是有限数字`, `edges[${index}]`);
    }
    edges.push([from, to, w]);
  });
  // 布尔参数从界面过来是**文本**（参数框里填 "true"/"false"），严格 `=== true` 会把 "true" 判成 false：
  // 有向图演示会悄悄变成无向图，而"计算各顶点入度"这类要求有向的动画直接报 UNDIRECTED_INDEGREE
  // （2026-09-24 真机扫描发现：这条动画从课堂点进去从来没能跑起来）。
  const asBoolean = (value) => value === true || value === 1 || value === "true" || value === "1";
  const directed = params.directed === undefined ? Boolean(defaults.directed) : asBoolean(params.directed);
  return { nodes, edges, directed, nodeSet };
}

/** 校验某个顶点标签确实在图中；用于 start/target 等参数。 */
function requireVertex(spec, label, name) {
  if (!spec.nodeSet.has(label)) {
    throw new SimulationInputError(
      "UNKNOWN_VERTEX",
      `${name} ${label} 不在顶点列表中（顶点集：${spec.nodes.join("、")}）`,
      name
    );
  }
}

module.exports = {
  SimulationInputError,
  ownOf,
  strictInt,
  normalizeParentArray,
  normalizeGraphSpec,
  requireVertex
};
