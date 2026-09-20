const { AUX_SUPPORTED_PAIRS, simulateAuxiliaryOperation } = require("./textbook-animation-auxiliary");
const { SimulationInputError, ownOf, strictInt, normalizeParentArray, normalizeGraphSpec, requireVertex } = require("./textbook-validation");
const BASE_TEXTBOOK_SUPPORTED_PAIRS = Object.freeze({
  linked_list: new Set(["insert", "delete", "merge"]),
  doubly_linked_list: new Set(["insert", "delete"]),
  polynomial: new Set(["add"]),
  stack_app: new Set(["bracket_match", "expression_evaluate"]),
  recursion: new Set(["hanoi"]),
  circular_queue: new Set(["enqueue", "dequeue"]),
  string: new Set(["insert", "delete", "brute_force_match", "kmp_match"]),
  sparse_matrix: new Set(["transpose", "fast_transpose", "cross_list_build"]),
  generalized_list: new Set(["tail", "length", "depth", "atom_count", "copy"]),
  tree: new Set(["build", "preorder", "inorder", "postorder", "levelorder", "inorder_stack", "postorder_stack", "thread_inorder", "thread_predecessor", "thread_successor", "visit", "highlight"]),
  huffman: new Set(["build", "encode"]),
  union_find: new Set(["find", "union"]),
  graph: new Set(["dfs", "bfs", "path_search", "prim", "kruskal", "topological_sort", "critical_path", "dijkstra", "floyd", "visit", "highlight"]),
  search: new Set(["sequential", "binary", "block"]),
  bst: new Set(["search", "insert", "delete"]),
  avl: new Set(["insert"]),
  btree: new Set(["search", "insert", "delete"]),
  hash_table: new Set(["linear_probe_insert", "linear_probe_search", "quadratic_probe_insert", "random_probe_insert", "random_probe_search", "rehash_insert", "chaining_insert", "chaining_search"]),
  sort: new Set(["direct_insertion", "binary_insertion", "shell", "bubble", "quick", "simple_selection", "tournament_selection", "heap", "merge", "radix"]),
  external_sort: new Set(["two_way_merge", "replacement_selection", "multiway_merge"])
});
const TEXTBOOK_SUPPORTED_PAIRS = Object.freeze(Object.fromEntries(
  Array.from(new Set([...Object.keys(BASE_TEXTBOOK_SUPPORTED_PAIRS), ...Object.keys(AUX_SUPPORTED_PAIRS)]))
    .map((structure) => [
      structure,
      new Set([...(BASE_TEXTBOOK_SUPPORTED_PAIRS[structure] || []), ...(AUX_SUPPORTED_PAIRS[structure] || [])])
    ])
));

function deepClone(value) {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value));
}

function sanitizeJson(value, depth = 0) {
  if (depth > 6) return null;
  if (value === null || ["string", "number", "boolean"].includes(typeof value)) {
    if (typeof value === "number" && !Number.isFinite(value)) return null;
    return value;
  }
  // 不再静默截断数组：超限在 normalizeTextbookRequest 中显式报错
  if (Array.isArray(value)) return value.map((item) => sanitizeJson(item, depth + 1));
  if (value && typeof value === "object") {
    const out = {};
    for (const [key, item] of Object.entries(value).slice(0, 80)) out[String(key).slice(0, 80)] = sanitizeJson(item, depth + 1);
    return out;
  }
  return null;
}

/** 递归检查数组规模：超过单次动画上限时显式报错，而不是悄悄截断前 120 个。 */
function assertArrayLimits(value, path, ValidationError, depth = 0) {
  if (depth > 8) return;
  if (Array.isArray(value)) {
    if (value.length > 120) {
      throw new ValidationError(
        "INPUT_TOO_LARGE",
        `${path} 包含 ${value.length} 个元素，超过单次动画 120 个元素的上限，请缩小示例规模`,
        path
      );
    }
    for (let i = 0; i < value.length; i++) assertArrayLimits(value[i], `${path}[${i}]`, ValidationError, depth + 1);
  } else if (value && typeof value === "object") {
    for (const [key, item] of Object.entries(value)) assertArrayLimits(item, `${path}.${key}`, ValidationError, depth + 1);
  }
}

function normalizeTextbookRequest(input, ValidationError, version) {
  if (!input || typeof input !== "object" || Array.isArray(input)) {
    throw new ValidationError("INVALID_REQUEST", "request 必须是对象", "request");
  }
  const unexpectedFields = Object.keys(input).filter((key) =>
    !["version", "structure", "operation", "params", "initial_state", "options", "source_ref"].includes(key)
  );
  if (unexpectedFields.length) {
    throw new ValidationError("UNEXPECTED_FIELD", `request 包含未定义字段：${unexpectedFields.join("、")}`, unexpectedFields.join(","));
  }
  const structure = String(input.structure || "").trim();
  const operation = String(input.operation || "").trim();
  if (!ownOf(TEXTBOOK_SUPPORTED_PAIRS, structure)?.has(operation)) {
    throw new ValidationError("UNSUPPORTED_OPERATION", `教材动画暂不支持 ${structure}/${operation}`, `${structure}/${operation}`);
  }
  const params = sanitizeJson(input.params && typeof input.params === "object" ? input.params : {}) || {};
  const rawState = input.initial_state && typeof input.initial_state === "object" ? input.initial_state : { data: [] };
  const data = sanitizeJson(rawState.data ?? []);
  const metadata = sanitizeJson(rawState.metadata && typeof rawState.metadata === "object" ? rawState.metadata : {}) || {};
  assertArrayLimits(params, "params", ValidationError);
  assertArrayLimits(data, "initial_state.data", ValidationError);
  const serialized = JSON.stringify({ params, data, metadata });
  if (serialized.length > 80_000) throw new ValidationError("INITIAL_STATE_TOO_LARGE", "教材动画输入过大，请缩小示例规模", String(serialized.length));
  return {
    version,
    structure,
    operation,
    params,
    initial_state: { data, metadata },
    options: {
      language: String(input.options?.language || "c").slice(0, 16),
      explain_level: String(input.options?.explain_level || "beginner").slice(0, 24)
    },
    source_ref: String(input.source_ref || "").slice(0, 160)
  };
}

function viewState(kind, rows, meta = {}) {
  return { kind, view: deepClone(rows), meta: deepClone(meta) };
}

function row(role, values, extra = {}) {
  return { role, values: deepClone(values), ...deepClone(extra) };
}

function textbookStateValues(state) {
  return Array.isArray(state?.view) ? deepClone(state.view) : [];
}

function numberArray(value, fallback = []) {
  if (!Array.isArray(value)) return [...fallback];
  // 空数组保持为空（由各模拟器决定空表语义）；非法元素显式报错，不再静默丢弃。
  return value.map((item, index) => {
    const n = Number(item);
    if (!Number.isFinite(n)) {
      throw new SimulationInputError("INVALID_ELEMENT", `第 ${index + 1} 个元素 ${JSON.stringify(item)} 不是有效数字`, `data[${index}]`);
    }
    return n;
  });
}

function scalarArray(value, fallback = []) {
  if (!Array.isArray(value)) return [...fallback];
  return value.map((item, index) => {
    if (item === null || ["string", "number", "boolean"].includes(typeof item)) return item;
    throw new SimulationInputError(
      "INVALID_ELEMENT",
      `第 ${index + 1} 个元素 ${JSON.stringify(item)} 类型不支持：只接受数字、字符串、布尔值或 null`,
      `data[${index}]`
    );
  });
}

function intParam(params, name, fallback, min = -1e9, max = 1e9) {
  // 提供了就严格校验（越界报错），未提供才用 fallback——不再静默夹取。
  return strictInt(params, name, fallback, min, max);
}

function makeHelpers(api) {
  return {
    makeStep: api.makeStep,
    makeTrace: api.makeTrace,
    action: api.action,
    emptyHighlights: api.emptyHighlights,
    makeRuntimeError: api.makeRuntimeError
  };
}

function simulateLinkedList(request, api) {
  const { makeStep, makeTrace, action, emptyHighlights } = makeHelpers(api);
  const op = request.operation;
  if (op === "merge") {
    const raw = Array.isArray(request.initial_state.data) ? request.initial_state.data : [];
    const left = scalarArray(request.params.left ?? raw[0], [1, 3, 5]);
    const right = scalarArray(request.params.right ?? raw[1], [2, 4, 6]);
    let i = 0, j = 0;
    const result = [];
    const steps = [makeStep(1, "init", "准备两个有序链表", "两个头指针分别指向当前结点。", viewState("linked_list", [row("LA", left), row("LB", right), row("LC", []), row("meta", [], { i, j })]))];
    let sid = 2;
    while (i < left.length || j < right.length) {
      let source;
      if (j >= right.length || (i < left.length && Number(left[i]) <= Number(right[j]))) source = "LA";
      else source = "LB";
      const value = source === "LA" ? left[i++] : right[j++];
      result.push(value);
      steps.push(makeStep(sid++, "link", "比较并接入结点", `把 ${String(value)} 从 ${source} 接到结果链表尾部。`, viewState("linked_list", [row("LA", left), row("LB", right), row("LC", result), row("meta", [], { i, j, source })]), [action("link", "尾指针连接到选中的结点", { from: source, to: "LC", value })]));
    }
    return makeTrace(request, "有序单链表合并", `LA=[${left.join(",")}], LB=[${right.join(",")}]`, `LC=[${result.join(",")}]`, steps);
  }

  const items = scalarArray(request.initial_state.data, [10, 20, 30]);
  if (op === "delete" && items.length === 0) {
    return api.makeRuntimeError(request, "单链表删除", "LIST_UNDERFLOW", "链表为空，没有可以删除的结点。", viewState("linked_list", [row("L", []), row("meta", [], { operation: op })]), "空链表");
  }
  const position = intParam(request.params, "position", op === "insert" ? Math.min(2, items.length + 1) : Math.min(2, items.length), 1, op === "insert" ? items.length + 1 : Math.max(1, items.length));
  const index = position - 1;
  const initialRows = [row("L", items), row("meta", [], { position, current: 0, operation: op })];
  const steps = [makeStep(1, "init", "从头结点开始", `沿 next 指针寻找第 ${position} 个位置。`, viewState("linked_list", initialRows))];
  let sid = 2;
  for (let p = 0; p < Math.max(0, index); p++) {
    steps.push(makeStep(sid++, "traverse", "指针后移", `工作指针移动到第 ${p + 1} 个结点。`, viewState("linked_list", [row("L", items), row("meta", [], { position, current: p + 1, operation: op })]), [action("move", "沿 next 指针移动", { target: "p", value: p + 1 })]));
  }
  if (op === "insert") {
    const value = request.params.value ?? 15;
    const result = [...items]; result.splice(index, 0, value);
    steps.push(makeStep(sid++, "link", "修改指针连接", `先让新结点 ${String(value)} 指向原第 ${position} 个结点，再让前驱结点指向新结点。`, viewState("linked_list", [row("L", result), row("meta", [], { position, current: index, operation: op, value })]), [action("link", "先接后继，再改前驱 next", { target: "next", value })], { ...emptyHighlights(), pointers: [{ role: "changed", name: "next" }] }));
    return makeTrace(request, "单链表插入", `L=[${items.join(",")}]`, `L=[${result.join(",")}]`, steps);
  }
  const removed = items[index];
  const result = [...items]; result.splice(index, 1);
  steps.push(makeStep(sid++, "unlink", "越过待删结点", `让前驱结点直接指向 ${String(removed)} 的后继结点，然后释放该结点。`, viewState("linked_list", [row("L", result), row("meta", [], { position, current: Math.max(0,index-1), operation: op, removed })]), [action("unlink", "修改前驱 next，跳过待删结点", { target: "next", value: removed })], { ...emptyHighlights(), pointers: [{ role: "changed", name: "next" }] }));
  return makeTrace(request, "单链表删除", `L=[${items.join(",")}]`, `L=[${result.join(",")}]`, steps);
}

function simulateDoublyList(request, api) {
  const { makeStep, makeTrace, action, emptyHighlights } = makeHelpers(api);
  const items = scalarArray(request.initial_state.data, [10,20,30]);
  const op = request.operation;
  if (op === "delete" && items.length === 0) {
    return api.makeRuntimeError(request, "双向链表删除", "LIST_UNDERFLOW", "链表为空，没有可以删除的结点。", viewState("doubly_linked_list", [row("L", []), row("meta", [], { operation: op })]), "空链表");
  }
  const maxPos = op === "insert" ? items.length + 1 : items.length;
  const position = intParam(request.params, "position", Math.min(2,maxPos), 1, Math.max(1,maxPos));
  const index = position - 1;
  const steps = [makeStep(1,"init","定位位置","沿 next 指针定位操作位置，同时保留前驱关系。",viewState("doubly_linked_list",[row("L",items),row("meta",[],{position,operation:op})]))];
  if (op === "insert") {
    const value = request.params.value ?? 15;
    const result=[...items]; result.splice(index,0,value);
    steps.push(makeStep(2,"link","连接新结点的前后指针",`设置新结点 ${value} 的 prior 和 next。`,viewState("doubly_linked_list",[row("L",result),row("meta",[],{position,operation:op,value,phase:"new-links"})]),[action("link","new.prior=p; new.next=p.next",{target:"prior,next"})],{...emptyHighlights(),pointers:[{role:"changed",name:"prior"},{role:"changed",name:"next"}]}));
    steps.push(makeStep(3,"link","修改相邻结点指针","让前驱和后继分别指向新结点。",viewState("doubly_linked_list",[row("L",result),row("meta",[],{position,operation:op,value,phase:"neighbor-links"})]),[action("link","同步修改两侧指针",{target:"neighbors"})]));
    return makeTrace(request,"双向链表插入",`L=[${items.join(",")}]`,`L=[${result.join(",")}]`,steps);
  }
  const removed=items[index]; const result=[...items]; result.splice(index,1);
  steps.push(makeStep(2,"unlink","前驱越过待删结点","修改前驱 next 指向后继。",viewState("doubly_linked_list",[row("L",result),row("meta",[],{position,operation:op,removed,phase:"forward"})]),[action("unlink","p.prior.next=p.next",{target:"next"})]));
  steps.push(makeStep(3,"unlink","后继越过待删结点","修改后继 prior 指向前驱。",viewState("doubly_linked_list",[row("L",result),row("meta",[],{position,operation:op,removed,phase:"backward"})]),[action("unlink","p.next.prior=p.prior",{target:"prior"})]));
  return makeTrace(request,"双向链表删除",`L=[${items.join(",")}]`,`L=[${result.join(",")}]`,steps);
}

function normalizeTerms(value, fallback) {
  const src = Array.isArray(value) ? value : fallback;
  return src.map((t, i) => {
    if (!t || typeof t !== "object" || Array.isArray(t)) {
      throw new SimulationInputError(
        "INVALID_TERM",
        `第 ${i + 1} 项 ${JSON.stringify(t)} 格式不正确：多项式项应为 { "coef": 数字, "exp": 数字 }（如 { "coef": 3, "exp": 4 }）`,
        `terms[${i}]`
      );
    }
    const coef = Number(t.coef ?? t.coefficient);
    const exp = Number(t.exp ?? t.exponent);
    if (!Number.isFinite(coef)) {
      throw new SimulationInputError("INVALID_TERM", `第 ${i + 1} 项的系数必须是有限数字（当前 ${JSON.stringify(t.coef ?? t.coefficient)}）`, `terms[${i}]`);
    }
    if (!Number.isInteger(exp) || exp < 0) {
      throw new SimulationInputError("INVALID_TERM", `第 ${i + 1} 项的指数必须是非负整数（当前 ${JSON.stringify(t.exp ?? t.exponent)}）`, `terms[${i}]`);
    }
    return { coef, exp };
  }).sort((a, b) => b.exp - a.exp);
}

function simulatePolynomial(request, api) {
  const { makeStep, makeTrace, action } = makeHelpers(api);
  const left=normalizeTerms(request.params.left ?? request.initial_state.data?.[0],[{coef:3,exp:3},{coef:2,exp:1}]);
  const right=normalizeTerms(request.params.right ?? request.initial_state.data?.[1],[{coef:4,exp:2},{coef:-2,exp:1},{coef:5,exp:0}]);
  const result=[]; let i=0,j=0,sid=1;
  const steps=[makeStep(sid++,"init","准备两个多项式链表","按指数从高到低比较当前项。",viewState("polynomial",[row("PA",left),row("PB",right),row("PC",result),row("meta",[],{i,j})]))];
  while(i<left.length||j<right.length){
    if(j>=right.length || (i<left.length&&left[i].exp>right[j].exp)){ result.push({...left[i]}); i++; }
    else if(i>=left.length || right[j].exp>left[i].exp){ result.push({...right[j]}); j++; }
    else { const coef=left[i].coef+right[j].coef; const exp=left[i].exp; i++; j++; if(coef!==0) result.push({coef,exp}); }
    steps.push(makeStep(sid++,"merge","比较指数并处理当前项",`当前结果：${result.map(t=>`${t.coef}x^${t.exp}`).join(" + ")||"0"}`,viewState("polynomial",[row("PA",left),row("PB",right),row("PC",result),row("meta",[],{i,j})]),[action("compare","比较两个当前结点指数",{target:"exp"})]));
  }
  return makeTrace(request,"一元多项式相加","两个多项式链表","得到合并后的多项式",steps);
}

function simulateBracketMatch(request, api) {
  const { makeStep, makeTrace, action } = makeHelpers(api);
  const text=String(request.params.text ?? request.initial_state.data ?? "{[()]}").slice(0,80);
  const stack=[]; const steps=[makeStep(1,"init","准备空栈",`从左到右扫描 ${text}`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:-1})]))];
  const pairs={")":"(","]":"[","}":"{"}; let ok=true,sid=2;
  for(let i=0;i<text.length;i++){
    const ch=text[i];
    if("([{<".includes(ch)){ stack.push(ch); steps.push(makeStep(sid++,"push","左括号入栈",`${ch} 入栈。`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:i,current:ch})]),[action("push","左括号入栈",{value:ch})])); }
    else if(")]}>".includes(ch)){
      const expected=pairs[ch] ?? ({">":"<"})[ch]; const top=stack[stack.length-1];
      if(top===expected){ stack.pop(); steps.push(makeStep(sid++,"pop","匹配并出栈",`${top} 与 ${ch} 匹配，栈顶出栈。`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:i,current:ch})]),[action("pop","匹配括号出栈",{value:top})])); }
      else { ok=false; steps.push(makeStep(sid++,"error","发现不匹配",`当前位置 ${ch} 与栈顶 ${String(top??"空")} 不匹配。`,viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{index:i,current:ch,error:true})]))); break; }
    }
  }
  if(stack.length) ok=false;
  steps.push(makeStep(sid,"done","匹配结束",ok?"扫描结束且栈空，括号匹配。":"扫描结束后仍有不匹配括号。",viewState("stack_app",[row("input",text.split("")),row("stack",stack),row("meta",[],{done:true,ok})])));
  return makeTrace(request,"括号匹配",text,ok?"匹配成功":"匹配失败",steps);
}

function tokenizeExpression(expr){
  const tokens=String(expr).replace(/\s+/g,"").match(/\d+(?:\.\d+)?|[()+\-*/]/g)||[];
  return tokens.slice(0,80);
}
function precedence(op){ return op==="+"||op==="-"?1:op==="*"||op==="/"?2:0; }
function applyOp(a,b,op){ if(op==="+")return a+b;if(op==="-")return a-b;if(op==="*")return a*b;if(op==="/")return b===0?NaN:a/b;return NaN; }
function simulateExpression(request, api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const rawExpr=String(request.params.expression??request.initial_state.data??"3+5*2");
  if (rawExpr.length > 120) throw new SimulationInputError("INPUT_TOO_LONG", `表达式长度 ${rawExpr.length} 超过 120 字符上限`, "expression");
  if (!/^[\d+\-*/().\s]*$/.test(rawExpr)) throw new SimulationInputError("INVALID_EXPRESSION", "表达式含不支持的字符：只接受数字、+ - * / ( ) 和空白");
  if (!/\d/.test(rawExpr)) throw new SimulationInputError("INVALID_EXPRESSION", "表达式为空或不含操作数");
  const expr=rawExpr; const tokens=tokenizeExpression(expr);
  const vals=[],ops=[]; let sid=1; const steps=[makeStep(sid++,"init","准备两个栈","操作数栈和运算符栈均为空。",viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:-1})]))];
  const reduce=()=>{ const op=ops.pop(),b=vals.pop(),a=vals.pop(),r=applyOp(a,b,op); vals.push(r); return {op,a,b,r}; };
  for(let i=0;i<tokens.length;i++){
    const t=tokens[i];
    if(/^\d/.test(t)){ vals.push(Number(t)); steps.push(makeStep(sid++,"push","操作数入栈",`${t} 入操作数栈。`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})]),[action("push","操作数入栈",{value:Number(t)})])); continue; }
    if(t==="("){ops.push(t);continue;}
    if(t===")"){ while(ops.length&&ops[ops.length-1]!=="("){const x=reduce();steps.push(makeStep(sid++,"compute","计算栈顶运算",`${x.a}${x.op}${x.b}=${x.r}`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})])));} ops.pop(); continue; }
    while(ops.length&&precedence(ops[ops.length-1])>=precedence(t)){const x=reduce();steps.push(makeStep(sid++,"compute","先计算高优先级运算",`${x.a}${x.op}${x.b}=${x.r}`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})])));} ops.push(t);
    steps.push(makeStep(sid++,"push","运算符入栈",`${t} 入运算符栈。`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{index:i,current:t})])));
  }
  while(ops.length){const x=reduce();steps.push(makeStep(sid++,"compute","完成剩余运算",`${x.a}${x.op}${x.b}=${x.r}`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{done:false})])));}
  const result=vals[0]; steps.push(makeStep(sid,"done","表达式求值完成",`结果为 ${String(result)}。`,viewState("stack_app",[row("tokens",tokens),row("values",vals),row("operators",ops),row("meta",[],{done:true,result})])));
  return makeTrace(request,"无括号算术表达式求值",expr,String(result),steps);
}

function simulateHanoi(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  // 注册表声明的是 n；diskCount 是历史参数名，保留兼容。两个名字共用 1~6 的范围（盘数超过 6 步数会超出演示上限）。
  const n=intParam(request.params,"n",intParam(request.params,"diskCount",3,1,6),1,6); const towers={A:Array.from({length:n},(_,i)=>n-i),B:[],C:[]}; let sid=1;
  const steps=[makeStep(sid++,"init","汉诺塔初始状态",`${n} 个盘片位于 A 柱。`,viewState("hanoi",[row("A",towers.A),row("B",towers.B),row("C",towers.C)]))];
  function move(k,from,aux,to){if(k===0)return;move(k-1,from,to,aux);const disk=towers[from].pop();towers[to].push(disk);steps.push(makeStep(sid++,"move","移动盘片",`将盘片 ${disk} 从 ${from} 移到 ${to}。`,viewState("hanoi",[row("A",towers.A),row("B",towers.B),row("C",towers.C),row("meta",[],{disk,from,to})]),[action("move","移动盘片",{from,to,value:disk})]));move(k-1,aux,from,to);} move(n,"A","B","C");
  return makeTrace(request,"汉诺塔递归过程",`${n} 个盘片在 A`,`全部移动到 C`,steps);
}

function circularQueueState(buffer,front,rear,count,capacity,extra={}){return viewState("circular_queue",[row("buffer",buffer),row("meta",[],{front,rear,count,capacity,...extra})]);}
function simulateCircularQueue(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api); const capacity=intParam(request.params,"capacity",5,2,20); const initial=scalarArray(request.initial_state.data,[4,7]);
  if (initial.length > capacity-1) throw new SimulationInputError("INITIAL_STATE_OVERFLOW", `初始元素 ${initial.length} 个超过循环队列可容纳上限 ${capacity-1}（需保留一个空单元区分队空与队满），请增大 capacity 或减少初始元素`, "initialData");
  let front=0,count=initial.length,rear=count%capacity; const buffer=Array(capacity).fill(null); initial.forEach((v,i)=>buffer[i]=v); const op=request.operation;
  const steps=[makeStep(1,"init","循环队列初始状态",`front=${front}, rear=${rear}。`,circularQueueState(buffer,front,rear,count,capacity))];
  if(op==="enqueue"){
    if(count>=capacity-1)return api.makeRuntimeError(request,"循环队列入队","QUEUE_OVERFLOW","保留一个空单元时队列已满。",circularQueueState(buffer,front,rear,count,capacity),`count=${count}`);
    const value=request.params.value??9; buffer[rear]=value; steps.push(makeStep(2,"write","写入 rear 位置",`将 ${value} 写入下标 ${rear}。`,circularQueueState(buffer,front,rear,count,capacity,{current:rear}),[action("enqueue","写入队尾",{target:rear,value})])); rear=(rear+1)%capacity; count++; steps.push(makeStep(3,"move","rear 循环后移",`rear=(rear+1)%${capacity}=${rear}。`,circularQueueState(buffer,front,rear,count,capacity),[action("move","rear 取模后移",{target:"rear",value:rear})]));
    return makeTrace(request,"循环队列入队","初始循环队列",`front=${front},rear=${rear}`,steps);
  }
  if(count===0)return api.makeRuntimeError(request,"循环队列出队","QUEUE_UNDERFLOW","当前循环队列为空。",circularQueueState(buffer,front,rear,count,capacity),"空队列");
  const removed=buffer[front]; buffer[front]=null; steps.push(makeStep(2,"read","取出 front 元素",`读取并删除 ${removed}。`,circularQueueState(buffer,front,rear,count,capacity,{current:front,removed}),[action("dequeue","删除队首",{target:front,value:removed})])); front=(front+1)%capacity; count--; steps.push(makeStep(3,"move","front 循环后移",`front=(front+1)%${capacity}=${front}。`,circularQueueState(buffer,front,rear,count,capacity),[action("move","front 取模后移",{target:"front",value:front})]));
  return makeTrace(request,"循环队列出队","初始循环队列",`front=${front},rear=${rear}`,steps);
}

function simulateString(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api); const op=request.operation; const text=String(request.params.text??request.initial_state.data??"DATASTRUCTURE").slice(0,80); const chars=[...text];
  if(op==="insert"||op==="delete"){
    const position=intParam(request.params,"position",Math.min(3,chars.length+1),1,op==="insert"?chars.length+1:Math.max(1,chars.length)); const idx=position-1; const steps=[makeStep(1,"init","原字符串",text,viewState("string",[row("text",chars),row("meta",[],{position,operation:op})]))];
    if(op==="insert"){const value=String(request.params.value??"AI");const result=[...chars];result.splice(idx,0,...value);steps.push(makeStep(2,"shift","为新串腾出位置",`第 ${position} 个字符及其后内容整体后移。`,viewState("string",[row("text",result),row("meta",[],{position,operation:op,value})]),[action("move","后移字符",{target:idx})]));return makeTrace(request,"顺序串插入",text,result.join(""),steps);}
    const count=intParam(request.params,"count",1,1,chars.length-idx); const result=[...chars];const removed=result.splice(idx,count).join("");steps.push(makeStep(2,"shift","删除并前移",`删除 ${removed}，后续字符前移。`,viewState("string",[row("text",result),row("meta",[],{position,count,operation:op,removed})]),[action("delete","删除字符区间",{target:idx,value:removed})]));return makeTrace(request,"顺序串删除",text,result.join(""),steps);
  }
  const rawPattern=String(request.params.pattern??"STRUCT"); if (rawPattern.length>40) throw new SimulationInputError("INPUT_TOO_LONG",`模式串长度 ${rawPattern.length} 超过 40 字符上限`,"pattern"); if (!rawPattern.length) throw new SimulationInputError("EMPTY_PATTERN","模式串不能为空"); const pattern=rawPattern; const p=[...pattern]; const steps=[makeStep(1,"init","准备模式匹配",`主串=${text}，模式串=${pattern}`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:0,j:0,operation:op})]))]; let sid=2;
  if(op==="brute_force_match"){
    for(let start=0;start<=chars.length-p.length;start++){
      let j=0; while(j<p.length&&chars[start+j]===p[j]){steps.push(makeStep(sid++,"compare","字符相等",`${chars[start+j]} = ${p[j]}`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:start+j,j,start,operation:op})]),[action("compare","比较当前字符",{from:start+j,to:j})]));j++;}
      if(j===p.length){steps.push(makeStep(sid,"done","匹配成功",`模式串首次出现在位置 ${start+1}。`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:start,j,match:start,operation:op})])));return makeTrace(request,"Brute-Force 模式匹配",text,`位置 ${start+1}`,steps);}
      steps.push(makeStep(sid++,"shift","模式串后移一位",`在起点 ${start+1} 处失配，下一轮从 ${start+2} 开始。`,viewState("string_match",[row("text",chars),row("pattern",p),row("meta",[],{i:start+j,j,start,operation:op,mismatch:true})])));
    }
    return makeTrace(request,"Brute-Force 模式匹配",text,"未找到",steps);
  }
  const next=Array(p.length).fill(0); for(let i=1,j=0;i<p.length;i++){while(j>0&&p[i]!==p[j])j=next[j-1];if(p[i]===p[j])j++;next[i]=j;}
  let i=0,j=0; while(i<chars.length){ if(chars[i]===p[j]){steps.push(makeStep(sid++,"compare","字符相等",`${chars[i]} = ${p[j]}`,viewState("string_match",[row("text",chars),row("pattern",p),row("next",next),row("meta",[],{i,j,operation:op})])));i++;j++;if(j===p.length){const pos=i-j;steps.push(makeStep(sid,"done","匹配成功",`KMP 找到位置 ${pos+1}。`,viewState("string_match",[row("text",chars),row("pattern",p),row("next",next),row("meta",[],{i,j,match:pos,operation:op})])));return makeTrace(request,"KMP 模式匹配",text,`位置 ${pos+1}`,steps);}} else if(j>0){const old=j;j=next[j-1];steps.push(makeStep(sid++,"fallback","利用 next 回退模式指针",`j 从 ${old} 回退到 ${j}，主串指针 i 不回退。`,viewState("string_match",[row("text",chars),row("pattern",p),row("next",next),row("meta",[],{i,j,operation:op})])));} else i++; }
  return makeTrace(request,"KMP 模式匹配",text,"未找到",steps);
}

function denseMatrix(value){ if(!Array.isArray(value)||value.length===0) throw new SimulationInputError("INVALID_MATRIX","matrix 必须是非空二维数组（如 [[0,5,0],[2,0,3],[0,0,4]]）","matrix"); if(value.length>12) throw new SimulationInputError("INPUT_TOO_LARGE",`矩阵动画最多演示 12×12，当前 ${value.length} 行`,"matrix"); return value.map((r,i)=>{ if(!Array.isArray(r)) throw new SimulationInputError("INVALID_MATRIX",`第 ${i+1} 行不是数组`,"matrix"); if(r.length>12) throw new SimulationInputError("INPUT_TOO_LARGE",`矩阵动画最多演示 12×12，第 ${i+1} 行有 ${r.length} 列`,"matrix"); return r.map((x,j)=>{ const n=Number(x); if(!Number.isFinite(n)) throw new SimulationInputError("INVALID_MATRIX",`matrix[${i}][${j}]=${JSON.stringify(x)} 不是有效数字`,"matrix"); return n; }); }); }
function triplesFromMatrix(m){const out=[];for(let r=0;r<m.length;r++)for(let c=0;c<(m[r]||[]).length;c++)if(m[r][c]!==0)out.push([r,c,m[r][c]]);return out;}
function simulateSparseMatrix(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const m=denseMatrix(request.params.matrix??request.initial_state.data);const triples=triplesFromMatrix(m);const rows=m.length,cols=Math.max(0,...m.map(r=>r.length));const result=[];let sid=1;
  const steps=[makeStep(sid++,"init","三元组表",`非零元共有 ${triples.length} 个。`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",result),row("meta",[],{rows,cols,operation:request.operation})]))];
  if(request.operation==="cross_list_build"){
    const crossNodes=[];
    for(const [r,c,v] of triples){
      crossNodes.push({row:r,col:c,value:v});
      steps.push(makeStep(sid++,"link","建立十字链表结点",`为 a[${r}][${c}]=${v} 建结点，并分别接入第 ${r} 行链和第 ${c} 列链。`,viewState("sparse_matrix",[row("matrix",m),row("cross_nodes",crossNodes),row("meta",[],{rows,cols,current:[r,c],operation:request.operation})]),[action("link","同时连接 right/down 指针",{target:`(${r},${c})`,value:v})]));
    }
    return makeTrace(request,"稀疏矩阵十字链表建立",`非零元=${triples.length}`,`结点数=${crossNodes.length}`,steps);
  }
  if(request.operation==="transpose"){
    for(let c=0;c<cols;c++)for(const [r,cc,v] of triples)if(cc===c){result.push([cc,r,v]);steps.push(makeStep(sid++,"copy","按列扫描转置",`(${r},${cc},${v}) → (${cc},${r},${v})`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",result),row("meta",[],{column:c,operation:request.operation})]),[action("copy","交换行列下标",{value:v})]));}
  }else{
    const count=Array(cols).fill(0);triples.forEach(t=>count[t[1]]++);const start=Array(cols).fill(0);for(let i=1;i<cols;i++)start[i]=start[i-1]+count[i-1];const pos=[...start];const out=Array(triples.length);
    steps.push(makeStep(sid++,"count","统计各列非零元个数",`num=[${count.join(",")}]，cpot=[${start.join(",")}]。`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",[]),row("num",count),row("cpot",start),row("meta",[],{operation:request.operation})])));
    for(const [r,c,v] of triples){const k=pos[c]++;out[k]=[c,r,v];steps.push(makeStep(sid++,"place","一次定位写入",`原三元组 (${r},${c},${v}) 直接写入转置表位置 ${k}。`,viewState("sparse_matrix",[row("matrix",m),row("triples",triples),row("result",out.filter(Boolean)),row("num",count),row("cpot",start),row("meta",[],{current:k,operation:request.operation})]),[action("copy","按 cpot 定位",{target:k,value:v})]));} result.push(...out);
  }
  return makeTrace(request,request.operation==="fast_transpose"?"稀疏矩阵快速转置":"稀疏矩阵转置",`三元组数=${triples.length}`,`转置三元组数=${result.length}`,steps);
}

function generalizedDepth(x){if(!Array.isArray(x))return 0;if(x.length===0)return 1;return 1+Math.max(...x.map(generalizedDepth));}
function generalizedAtomCount(x){if(!Array.isArray(x))return 1;return x.reduce((sum,item)=>sum+generalizedAtomCount(item),0);}
function simulateGeneralizedList(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  const data=Array.isArray(request.initial_state.data)?request.initial_state.data:["a",["b","c"],["d",["e"]]];
  const op=request.operation;
  if(op==="tail"&&data.length===0){return api.makeRuntimeError(request,"求广义表表尾","EMPTY_LIST","空表没有表尾：广义表至少要有一个元素。",viewState("generalized_list",[row("list",[])]),"空广义表");}
  const steps=[makeStep(1,"init","广义表结构","按表头/表尾和子表层次观察广义表。",viewState("generalized_list",[row("list",data),row("meta",[],{operation:op})]))];
  if(op==="tail"){const tail=data.slice(1);steps.push(makeStep(2,"split","取表尾","去掉第一个表元素，剩余元素构成表尾。",viewState("generalized_list",[row("list",data),row("tail",tail),row("meta",[],{operation:op})]),[action("slice","去掉表头",{target:0})]));return makeTrace(request,"求广义表表尾","广义表",`tail=${JSON.stringify(tail)}`,steps);}
  if(op==="length"){const len=data.length;steps.push(makeStep(2,"count","统计最外层表元素",`最外层共有 ${len} 个表元素。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,length:len})])));return makeTrace(request,"求广义表长度","广义表",`length=${len}`,steps);}
  if(op==="depth"){const d=generalizedDepth(data);steps.push(makeStep(2,"recurse","递归比较子表深度",`最大子表深度加 1，得到深度 ${d}。`,viewState("generalized_list",[row("list",data),row("meta",[],{depth:d,operation:op})])));return makeTrace(request,"求广义表深度","广义表",`depth=${d}`,steps);}
  if(op==="atom_count"){const count=generalizedAtomCount(data);steps.push(makeStep(2,"recurse","递归统计原子结点",`逐层进入子表并累计原子，得到 ${count} 个原子。`,viewState("generalized_list",[row("list",data),row("meta",[],{operation:op,atomCount:count})]),[action("count","累计原子个数",{value:count})]));return makeTrace(request,"统计广义表原子个数","广义表",`atoms=${count}`,steps);}
  const copy=deepClone(data);steps.push(makeStep(2,"copy","递归复制原子和子表","逐层创建对应的新结点。",viewState("generalized_list",[row("list",data),row("copy",copy),row("meta",[],{operation:op})])));return makeTrace(request,"复制广义表","原广义表","复制完成",steps);
}

function normalizeTreeArray(value){const arr=Array.isArray(value)&&value.length?value:["A","B","C","D","E","F","G"];return arr.slice(0,31).map(v=>v===undefined?null:v);}
function treeChildren(arr,i){const l=2*i+1,r=2*i+2;return [l<arr.length&&arr[l]!==null?l:-1,r<arr.length&&arr[r]!==null?r:-1];}
function treeView(arr,visited=[],current=-1,extra={}){const nodes=arr.map((v,i)=>v===null?null:{id:i,label:String(v),index:i}).filter(Boolean);const edges=[];nodes.forEach(n=>{const [l,r]=treeChildren(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);});return viewState("tree",[row("tree",[],{nodes,edges}),row("visited",visited.map(i=>arr[i])),row("meta",[],{currentValue:current>=0?arr[current]:null,currentIndex:current,...extra})]);}
function traversalOrder(arr,mode){const out=[];function rec(i){if(i<0||i>=arr.length||arr[i]===null)return;const[l,r]=treeChildren(arr,i);if(mode==="pre")out.push(i);rec(l);if(mode==="in")out.push(i);rec(r);if(mode==="post")out.push(i);}if(mode==="level"){const q=[0];while(q.length){const i=q.shift();if(i<0||i>=arr.length||arr[i]===null)continue;out.push(i);const[l,r]=treeChildren(arr,i);if(l>=0)q.push(l);if(r>=0)q.push(r);}}else rec(0);return out;}
function simulateTree(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const arr=normalizeTreeArray(request.initial_state.data);const op=request.operation;
  if(op==="build"){const partial=Array(arr.length).fill(null);let sid=1;const steps=[makeStep(sid++,"init","开始建立二叉树","按层次序列逐个建立非空结点并连接孩子。",treeView(partial,[],-1,{operation:op}))];for(let i=0;i<arr.length;i++){if(arr[i]===null)continue;partial[i]=arr[i];steps.push(makeStep(sid++,"insert","建立结点并连接",`建立结点 ${arr[i]}${i?`，连接到父结点 ${arr[Math.floor((i-1)/2)]}`:"，作为根结点"}。`,treeView(partial,[],i,{operation:op}),[action("link","建立父子关系",{target:i,value:arr[i]})]));}return makeTrace(request,"建立二叉树","层次序列",`结点数=${arr.filter(x=>x!==null).length}`,steps);}
  // 核心契约的 tree visit/highlight（params.node 为层次下标）：单步定位并高亮一个结点。
  if(op==="visit"||op==="highlight"){
    const rawNode=request.params.node;
    const idx=intParam(request.params,"node",0,0,arr.length-1);
    if(arr[idx]===null)throw new SimulationInputError("EMPTY_NODE",`下标 ${idx} 处是空结点，无法访问（非空结点：${arr.map((x,i)=>x===null?null:i).filter(x=>x!==null).map(i=>`${i}:${arr[i]}`).join("、")}）`,"node");
    return makeTrace(request,op==="visit"?"访问指定结点":"高亮指定结点",`node=${rawNode===undefined?idx:rawNode}`,`访问 ${arr[idx]}`,[
      makeStep(1,"init","二叉树初始状态","观察当前树结构。",treeView(arr,[],-1,{operation:op})),
      makeStep(2,op,`${op==="visit"?"访问":"高亮"}结点 ${arr[idx]}`,`定位到层次下标 ${idx}。`,treeView(arr,[idx],idx,{operation:op}),[action("visit",op==="visit"?"访问结点":"高亮结点",{value:arr[idx]})])
    ]);
  }
  let order=[];
  if(op==="preorder")order=traversalOrder(arr,"pre");else if(["inorder","inorder_stack","thread_inorder","thread_predecessor","thread_successor"].includes(op))order=traversalOrder(arr,"in");else if(op==="postorder"||op==="postorder_stack")order=traversalOrder(arr,"post");else order=traversalOrder(arr,"level");
  const visited=[];let sid=1;const steps=[makeStep(sid++,"init","二叉树初始状态","准备按教材规定的次序访问结点。",treeView(arr,visited,-1,{operation:op}))];
  if(op==="inorder_stack"){
    const stack=[];let cur=0;while(cur>=0||stack.length){while(cur>=0&&arr[cur]!==null){stack.push(cur);steps.push(makeStep(sid++,"push","左链进栈",`${arr[cur]} 进栈，继续访问左孩子。`,treeView(arr,visited,cur,{operation:op,stack:stack.map(i=>arr[i])}),[action("push","结点指针进栈",{value:arr[cur]})]));cur=treeChildren(arr,cur)[0];}if(!stack.length)break;cur=stack.pop();visited.push(cur);steps.push(makeStep(sid++,"visit","退栈并访问",`访问 ${arr[cur]}。`,treeView(arr,visited,cur,{operation:op,stack:stack.map(i=>arr[i])}),[action("visit","访问结点",{value:arr[cur]})]));cur=treeChildren(arr,cur)[1];}
  } else if(op==="postorder_stack"){
    const stack=[[0,false]];while(stack.length){const [idx,expanded]=stack.pop();if(idx<0||idx>=arr.length||arr[idx]===null)continue;if(expanded){visited.push(idx);steps.push(makeStep(sid++,"visit","第二次退栈访问",`左右子树处理完毕，访问 ${arr[idx]}。`,treeView(arr,visited,idx,{operation:op,stack:stack.map(x=>arr[x[0]]).filter(Boolean)}),[action("visit","后序访问",{value:arr[idx]})]));continue;}stack.push([idx,true]);const[l,r]=treeChildren(arr,idx);if(r>=0)stack.push([r,false]);if(l>=0)stack.push([l,false]);steps.push(makeStep(sid++,"push","结点及孩子进栈",`记录 ${arr[idx]}，先处理左、右子树。`,treeView(arr,visited,idx,{operation:op,stack:stack.map(x=>arr[x[0]]).filter(Boolean)}),[action("push","保存待回访结点",{value:arr[idx]})]));}
  } else if(op==="thread_inorder"){
    let prev=-1;const threads=[];for(const idx of order){const[l]=treeChildren(arr,idx);if(l<0&&prev>=0)threads.push([idx,prev,"predecessor"]);if(prev>=0){const[,pr]=treeChildren(arr,prev);if(pr<0)threads.push([prev,idx,"successor"]);}visited.push(idx);steps.push(makeStep(sid++,"thread","建立中序线索",`访问 ${arr[idx]}，把空孩子指针改作前驱/后继线索。`,treeView(arr,visited,idx,{operation:op,threads:[...threads]}),[action("link","建立线索",{value:arr[idx]})]));prev=idx;}
  } else if(op==="thread_predecessor"||op==="thread_successor"){
    const target=String(request.params.target??arr[order[Math.floor(order.length/2)] ]);const pos=order.findIndex(i=>String(arr[i])===target);const neighbor=op==="thread_predecessor"?order[pos-1]:order[pos+1];steps.push(makeStep(sid++,"locate","定位给定结点",`在线索中序序列中定位 ${target}。`,treeView(arr,order.slice(0,Math.max(0,pos+1)),pos>=0?order[pos]:-1,{operation:op,target})));steps.push(makeStep(sid++,"follow","沿线索找到相邻结点",neighbor===undefined?`${target} 没有对应的${op.endsWith("predecessor")?"前驱":"后继"}。`:`${target} 的${op.endsWith("predecessor")?"前驱":"后继"}是 ${arr[neighbor]}。`,treeView(arr,order.slice(0,Math.max(0,pos+1)),neighbor??-1,{operation:op,target,neighbor:neighbor===undefined?null:arr[neighbor]}),[action("move","沿线索指针移动",{from:target,to:neighbor===undefined?null:arr[neighbor]})]));return makeTrace(request,op==="thread_predecessor"?"中序线索树求前驱":"中序线索树求后继",`target=${target}`,neighbor===undefined?"不存在":String(arr[neighbor]),steps);
  } else {for(const idx of order){visited.push(idx);steps.push(makeStep(sid++,"visit","访问结点",`访问 ${arr[idx]}。`,treeView(arr,visited,idx,{operation:op}),[action("visit","按遍历次序访问",{value:arr[idx]})]));}}
  return makeTrace(request,{preorder:"先序遍历",inorder:"中序遍历",postorder:"后序遍历",levelorder:"层序遍历",inorder_stack:"非递归中序遍历",postorder_stack:"非递归后序遍历",thread_inorder:"中序线索化"}[op]||"二叉树遍历","二叉树",`访问序列=${order.map(i=>arr[i]).join(" ")}`,steps);
}

function simulateHuffman(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const weights=numberArray(request.params.weights??request.initial_state.data,[2,3,4,7]);
  if(weights.length===0) throw new SimulationInputError("EMPTY_WEIGHTS","权值列表为空：哈夫曼树至少需要一个权值","weights");
  if(weights.length>12) throw new SimulationInputError("INPUT_TOO_LARGE",`哈夫曼动画最多演示 12 个权值，当前 ${weights.length} 个`,"weights");
  if(Array.isArray(request.params.symbols)&&request.params.symbols.length!==weights.length) throw new SimulationInputError("SYMBOL_MISMATCH",`symbols 数量（${request.params.symbols.length}）必须与 weights 数量（${weights.length}）一致`,"symbols");
  const symbols=Array.isArray(request.params.symbols)&&request.params.symbols.length===weights.length?request.params.symbols.map(String):weights.map((_,i)=>String.fromCharCode(65+i));
  let nodes=weights.map((w,i)=>({id:i,label:symbols[i],weight:w,left:null,right:null,parent:null}));let active=nodes.map(n=>n.id),nextId=nodes.length,sid=1;const rows=()=>[row("tree",[],{nodes:deepClone(nodes),edges:nodes.flatMap(n=>[[n.id,n.left],[n.id,n.right]].filter(e=>e[1]!==null))}),row("active",active.map(id=>nodes.find(n=>n.id===id)?.weight)),row("meta",[],{operation:request.operation})];const steps=[makeStep(sid++,"init","初始权值集合",`权值：${weights.join(", ")}`,viewState("huffman",rows()))];
  while(active.length>1){active.sort((a,b)=>nodes.find(n=>n.id===a).weight-nodes.find(n=>n.id===b).weight);const a=active.shift(),b=active.shift(),na=nodes.find(n=>n.id===a),nb=nodes.find(n=>n.id===b);const parent={id:nextId++,label:String(na.weight+nb.weight),weight:na.weight+nb.weight,left:a,right:b,parent:null};na.parent=parent.id;nb.parent=parent.id;nodes.push(parent);active.push(parent.id);steps.push(makeStep(sid++,"merge","选两个最小权值合并",`${na.weight}+${nb.weight}=${parent.weight}，生成新结点。`,viewState("huffman",rows()),[action("merge","合并两个最小权值",{from:`${na.weight},${nb.weight}`,to:parent.weight})]));}
  if(request.operation==="encode"){
    const root=active[0];const codes={};function walk(id,prefix){const n=nodes.find(x=>x.id===id);if(n.left===null&&n.right===null){codes[n.label]=prefix||"0";return;}if(n.left!==null)walk(n.left,prefix+"0");if(n.right!==null)walk(n.right,prefix+"1");}walk(root,"");steps.push(makeStep(sid++,"encode","沿左 0 右 1 生成编码",Object.entries(codes).map(([s,c])=>`${s}:${c}`).join("，"),viewState("huffman",[...rows(),row("codes",[],{codes})])));return makeTrace(request,"哈夫曼编码","权值集合",JSON.stringify(codes),steps);
  }
  return makeTrace(request,"构造哈夫曼树","权值集合",`WPL 构造完成`,steps);
}

function simulateUnionFind(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);
  // 用户显式传入的 parent 优先（注册表明明声明了这个参数，此前却被静默替换成 size 个单点集）。
  // normalizeParentArray 负责结构校验：整数、无自指、无环——保证 find 一定能走到根，杜绝死循环。
  // 直连 /simulate 的调用方可能把 parent 放在 params 或 initial_state.data，两处都要认。
  const provided=normalizeParentArray(request.params.parent)||normalizeParentArray(request.initial_state.data);
  let parent,n;
  if(provided){parent=provided;n=parent.length;}
  else{n=intParam(request.params,"size",6,2,20);parent=Array(n).fill(-1);}
  const steps=[makeStep(1,"init","并查集森林",`parent=[${parent.join(",")}]（负值 = 根，其绝对值为集合大小）`,viewState("union_find",[row("parent",parent),row("meta",[],{operation:request.operation,size:n})]))];
  function find(x){const path=[];while(parent[x]>=0){path.push(x);x=parent[x];}return {root:x,path};}
  if(request.operation==="find"){const x=intParam(request.params,"element",Math.min(2,Math.max(0,n-1)),0,n-1);const r=find(x);let sid=2;for(const p of r.path)steps.push(makeStep(sid++,"follow","沿双亲指针向根移动",`${p} → ${parent[p]}`,viewState("union_find",[row("parent",parent),row("path",r.path),row("meta",[],{current:p,root:r.root,operation:request.operation})]),[action("move","沿 parent 查找根",{from:p,to:parent[p]})]));steps.push(makeStep(sid,"done","找到代表元",`元素 ${x} 的根为 ${r.root}${r.path.length?`（沿 parent 走了 ${r.path.length} 步）`:"（它本身就是根）"}。`,viewState("union_find",[row("parent",parent),row("meta",[],{root:r.root,operation:request.operation})])));return makeTrace(request,"并查集查找",`element=${x}`,`root=${r.root}`,steps);}
  const a=intParam(request.params,"a",0,0,n-1),b=intParam(request.params,"b",Math.min(1,n-1),0,n-1);const ra=find(a).root,rb=find(b).root;
  if(ra===rb){steps.push(makeStep(2,"skip","两元素已在同一集合",`${a} 与 ${b} 的根都是 ${ra}，本次合并不改变 parent 数组。`,viewState("union_find",[row("parent",parent),row("meta",[],{a,b,operation:request.operation,unchanged:true})])));return makeTrace(request,"并查集合并",`a=${a},b=${b}`,"已在同一集合，parent 未改变",steps);}
  const sizeA=-parent[ra],sizeB=-parent[rb];if(sizeA>=sizeB){parent[ra]-=sizeB;parent[rb]=ra;}else{parent[rb]-=sizeA;parent[ra]=rb;}
  steps.push(makeStep(2,"union","合并两棵集合树",`根 ${ra}（大小 ${sizeA}）与根 ${rb}（大小 ${sizeB}）：较小树的根挂到较大树的根上。`,viewState("union_find",[row("parent",parent),row("meta",[],{a,b,operation:request.operation})]),[action("link","较小树挂到较大树根",{from:ra,to:rb})]));
  return makeTrace(request,"并查集合并",`a=${a},b=${b}`,`parent=[${parent.join(",")}]`,steps);
}

function graphInput(request,defaults={}){return normalizeGraphSpec(request,defaults);}
function graphRows(g,extra={}){return [row("graph",[],{nodes:g.nodes,edges:g.edges}),row("meta",[],{directed:g.directed,...extra})];}
function adjMap(g,directed=false){const m=new Map(g.nodes.map(n=>[n,[]]));for(const [a,b,w]of g.edges){m.get(a)?.push([b,w]);if(!directed)m.get(b)?.push([a,w]);}return m;}
function simulateGraph(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const op=request.operation;
  // 教材约定：拓扑排序/关键路径/Dijkstra 天然按有向图处理；Prim/Kruskal 按无向图处理；
  // 其余遍历/路径/建图操作由 directed 参数决定（缺省 false）。directed 全程写入 meta，界面上可见。
  const directedByDefault=new Set(["topological_sort","critical_path","dijkstra","floyd"]);
  const undirectedByDefault=new Set(["prim","kruskal"]);
  const g=graphInput(request,{directed:directedByDefault.has(op)});
  const directed=g.directed;const adj=adjMap(g,directed);
  // 起点支持两种写法：教材习惯的 params.start（顶点标签）与核心契约的 params.node（顶点下标）。
  let start;
  if(request.params.start!==undefined){start=String(request.params.start);requireVertex(g,start,"起点 start");}
  else if(request.params.node!==undefined){
    const ni=Number(request.params.node);
    if(!Number.isInteger(ni)||ni<0||ni>=g.nodes.length)throw new SimulationInputError("PARAM_OUT_OF_RANGE",`起点下标 node=${JSON.stringify(request.params.node)} 超出顶点范围 [0, ${g.nodes.length-1}]`,"node");
    start=g.nodes[ni];
  }
  else start=g.nodes[0];
  if(op==="path_search"){const target=String(request.params.target??g.nodes[g.nodes.length-1]);requireVertex(g,target,"终点 target");}
  if(op==="dijkstra"){for(const [a,b,w] of g.edges){if(w<0) throw new SimulationInputError("NEGATIVE_WEIGHT",`Dijkstra 不支持负权边：边 ${a}→${b} 的权值为 ${w}。带负权时请改用 Floyd 等算法`,"edges");}}
  let sid=1;const steps=[makeStep(sid++,"init","图的初始状态",`顶点 ${g.nodes.join(", ")}；${g.edges.length} 条边；${directed?"有向图":"无向图"}${undirectedByDefault.has(op)?"（最小生成树按无向图处理）":""}。`,viewState("graph",graphRows(g,{operation:op,visited:[],current:null})))];
  // 核心契约的 graph visit/highlight：单步定位并高亮起点顶点。
  if(op==="visit"||op==="highlight"){
    steps.push(makeStep(sid++,op,`${op==="visit"?"访问":"高亮"}顶点 ${start}`,`定位到顶点 ${start}。`,viewState("graph",graphRows(g,{operation:op,visited:[start],current:start})),[action("visit",op==="visit"?"访问顶点":"高亮顶点",{value:start})]));
    return makeTrace(request,op==="visit"?"访问指定顶点":"高亮指定顶点",`start=${start}`,start,steps);
  }
  if(op==="dfs"||op==="bfs"||op==="path_search"){
    const visited=[],seen=new Set();const target=String(request.params.target??g.nodes[g.nodes.length-1]);const parent={};const work=[start];
    while(work.length){const v=op==="bfs"?work.shift():work.pop();if(seen.has(v))continue;seen.add(v);visited.push(v);steps.push(makeStep(sid++,"visit","访问顶点",`访问 ${v}。`,viewState("graph",graphRows(g,{operation:op,visited:[...visited],current:v,frontier:[...work]})),[action("visit","访问顶点",{value:v})]));if(op==="path_search"&&v===target)break;const ns=(adj.get(v)||[]).map(x=>x[0]).filter(n=>!seen.has(n));if(op==="dfs")ns.reverse();for(const n of ns){if(parent[n]===undefined)parent[n]=v;work.push(n);}}
    let result=visited.join("→");if(op==="path_search"&&!seen.has(target)){steps.push(makeStep(sid++,"done","不存在可达路径",`从 ${start} 出发${directed?"沿有向边":"经过各条边"}无法到达 ${target}：两者之间没有路径。`,viewState("graph",graphRows(g,{operation:op,visited:[...visited],start,target})),[action("miss","无法到达目标",{from:start,to:target})]));return makeTrace(request,"图中简单路径搜索",`start=${start},target=${target}`,"不可达",steps);}if(op==="path_search"&&seen.has(target)){const path=[];let x=target;while(x!==undefined){path.push(x);if(x===start)break;x=parent[x];}result=path.reverse().join("→");steps.push(makeStep(sid++,"done","找到简单路径",result,viewState("graph",graphRows(g,{operation:op,visited:[...visited],path:result.split("→")}))));}
    return makeTrace(request,op==="dfs"?"图的深度优先遍历":op==="bfs"?"图的广度优先遍历":"图中简单路径搜索",`start=${start}`,result,steps);
  }
  if(op==="prim"){
    const selected=new Set([start]);const mst=[];let total=0;while(selected.size<g.nodes.length){let best=null;for(const [a,b,w]of g.edges){if(selected.has(a)&&!selected.has(b)||selected.has(b)&&!selected.has(a)){if(!best||w<best[2])best=[a,b,w];}}if(!best){const unreached=g.nodes.filter(x=>!selected.has(x));steps.push(makeStep(sid++,"error","图不连通",`已覆盖顶点 ${[...selected].join("、")}；剩余顶点 ${unreached.join("、")} 与已选部分之间没有边，Prim 无法继续，无法生成最小生成树。`,viewState("graph",graphRows(g,{operation:op,selected:[...selected],chosenEdges:[...mst],total}))));return makeTrace(request,"Prim 最小生成树",`start=${start}`,"图不连通，无法生成最小生成树",steps,[{code:"DISCONNECTED_GRAPH",message:`图不连通：只覆盖了 ${selected.size}/${g.nodes.length} 个顶点`,detail:unreached.join(","),recoverable:false}]);}const [a,b,w]=best;selected.add(selected.has(a)?b:a);mst.push(best);total+=w;steps.push(makeStep(sid++,"select","选择当前最小跨边",`${a}-${b}(${w}) 加入生成树。`,viewState("graph",graphRows(g,{operation:op,selected:[...selected],chosenEdges:[...mst],total})),[action("select","加入最小生成树边",{from:a,to:b,value:w})]));}return makeTrace(request,"Prim 最小生成树",`start=${start}`,`总权值=${total}`,steps);
  }
  if(op==="kruskal"){
    const parent=Object.fromEntries(g.nodes.map(n=>[n,n]));function find(x){while(parent[x]!==x){parent[x]=parent[parent[x]];x=parent[x];}return x;}const mst=[];let total=0;for(const e of [...g.edges].sort((a,b)=>a[2]-b[2])){const[a,b,w]=e,ra=find(a),rb=find(b);if(ra!==rb){parent[rb]=ra;mst.push(e);total+=w;steps.push(makeStep(sid++,"select","按权从小到大选边",`${a}-${b}(${w}) 不构成环，加入。`,viewState("graph",graphRows(g,{operation:op,chosenEdges:[...mst],total})),[action("select","加入边",{from:a,to:b,value:w})]));}else steps.push(makeStep(sid++,"skip","跳过成环边",`${a}-${b}(${w}) 会形成环，跳过。`,viewState("graph",graphRows(g,{operation:op,chosenEdges:[...mst],skipped:e,total}))));}
    if(mst.length<g.nodes.length-1){steps.push(makeStep(sid,"done","图不连通，得到生成森林",`连通的 ${g.nodes.length} 个顶点需要 ${g.nodes.length-1} 条边构成树，但只选出 ${mst.length} 条不构成环的边：图不连通，结果是生成森林而不是最小生成树。`,viewState("graph",graphRows(g,{operation:op,chosenEdges:[...mst],total,forest:true}))));return makeTrace(request,"Kruskal 最小生成树","边按权排序",`图不连通：生成森林，总权值=${total}`,steps,[],[{message:"图不连通，无法得到最小生成树，所选边构成生成森林"}]);}
    return makeTrace(request,"Kruskal 最小生成树","边按权排序",`总权值=${total}`,steps);
  }
  if(op==="topological_sort"||op==="critical_path"){
    const indeg=Object.fromEntries(g.nodes.map(n=>[n,0]));for(const[a,b]of g.edges)indeg[b]++;const q=g.nodes.filter(n=>indeg[n]===0);const topo=[];const ve=Object.fromEntries(g.nodes.map(n=>[n,0]));while(q.length){const v=q.shift();topo.push(v);steps.push(makeStep(sid++,"output","输出入度为 0 的顶点",`输出 ${v}，删除其出边并更新入度。`,viewState("graph",graphRows(g,{operation:op,topo:[...topo],indegree:{...indeg},current:v})),[action("output","输出顶点",{value:v})]));for(const [to,w]of adj.get(v)||[]){indeg[to]--;ve[to]=Math.max(ve[to],ve[v]+w);if(indeg[to]===0)q.push(to);}}
    if(topo.length<g.nodes.length){const remaining=g.nodes.filter(x=>!topo.includes(x));steps.push(makeStep(sid++,"error","检测到回路",`剩余顶点 ${remaining.join("、")} 的入度都不为 0，说明图中存在回路，拓扑排序无法完成。`,viewState("graph",graphRows(g,{operation:op,topo:[...topo],indegree:{...indeg},current:null}))));return makeTrace(request,"拓扑排序","AOV 网","存在回路，无法拓扑排序",steps,[{code:"CYCLE_DETECTED",message:`图中存在回路（卡住的顶点：${remaining.join("、")}）`,detail:remaining.join(","),recoverable:false}]);}
    if(op==="topological_sort")return makeTrace(request,"拓扑排序","AOV 网",topo.join("→"),steps);
    const maxTime=Math.max(...Object.values(ve));const vl=Object.fromEntries(g.nodes.map(n=>[n,maxTime]));for(let k=topo.length-1;k>=0;k--){const v=topo[k];for(const[to,w]of adj.get(v)||[])vl[v]=Math.min(vl[v],vl[to]-w);}const critical=[];for(const[a,b,w]of g.edges){const e=ve[a],l=vl[b]-w;if(e===l)critical.push([a,b,w]);}steps.push(makeStep(sid++,"critical","计算活动最早/最迟开始时间",`关键活动：${critical.map(e=>`${e[0]}→${e[1]}`).join("，")}`,viewState("graph",graphRows(g,{operation:op,topo,ve,vl,criticalEdges:critical}))));return makeTrace(request,"关键路径","AOE 网",`工期=${maxTime}`,steps);
  }
  if(op==="dijkstra"){
    const dist=Object.fromEntries(g.nodes.map(n=>[n,Infinity]));dist[start]=0;const done=new Set();const prev={};while(done.size<g.nodes.length){let u=null,best=Infinity;for(const n of g.nodes)if(!done.has(n)&&dist[n]<best){best=dist[n];u=n;}if(u===null)break;done.add(u);steps.push(makeStep(sid++,"select","确定当前最短顶点",`${u} 的最短距离确定为 ${dist[u]}。`,viewState("graph",graphRows(g,{operation:op,start,dist:{...dist},settled:[...done],current:u})),[action("select","加入已确定集合",{value:u})]));for(const[v,w]of adj.get(u)||[]){if(dist[u]+w<dist[v]){dist[v]=dist[u]+w;prev[v]=u;steps.push(makeStep(sid++,"relax","松弛边",`${u}→${v} 后 dist[${v}]=${dist[v]}。`,viewState("graph",graphRows(g,{operation:op,start,dist:{...dist},settled:[...done],current:v,relax:[u,v,w]})),[action("relax","更新最短距离",{from:u,to:v,value:dist[v]})]));}}}return makeTrace(request,"Dijkstra 单源最短路径",`start=${start}`,JSON.stringify(dist),steps);
  }
  const n=g.nodes.length;const idx=Object.fromEntries(g.nodes.map((x,i)=>[x,i]));const d=Array.from({length:n},(_,i)=>Array.from({length:n},(_,j)=>i===j?0:Infinity));for(const[a,b,w]of g.edges){d[idx[a]][idx[b]]=Math.min(d[idx[a]][idx[b]],w);if(!directed)d[idx[b]][idx[a]]=Math.min(d[idx[b]][idx[a]],w);}for(let k=0;k<n;k++){for(let i=0;i<n;i++)for(let j=0;j<n;j++)if(d[i][k]+d[k][j]<d[i][j])d[i][j]=d[i][k]+d[k][j];steps.push(makeStep(sid++,"intermediate","加入中间顶点",`允许 ${g.nodes[k]} 作为中间点后更新距离矩阵。`,viewState("graph",[...graphRows(g,{operation:op,k:g.nodes[k]}),row("matrix",d.map(r=>r.map(x=>Number.isFinite(x)?x:"∞"))) ])));}return makeTrace(request,"Floyd 各对顶点最短路径","初始距离矩阵","最终距离矩阵",steps);
}

function simulateSearch(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const arr=numberArray(request.initial_state.data,[7,13,18,24,31,42,55]);if(request.params.key!==undefined&&!Number.isFinite(Number(request.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");const key=Number(request.params.key??24);let sid=1;const steps=[makeStep(sid++,"init","准备查找",`查找关键字 ${key}。`,viewState("sequence",[row("array",arr),row("meta",[],{operation:request.operation,key})]))];if(request.operation==="sequential"){if(!arr.length){steps.push(makeStep(sid,"done","查找表为空","查找表中没有任何元素，顺序查找结束。",viewState("sequence",[row("array",[]),row("meta",[],{operation:request.operation,key})])));return makeTrace(request,"顺序查找",`key=${key}`,"未找到",steps);}for(let i=0;i<arr.length;i++){steps.push(makeStep(sid++,"compare","逐个比较",`${key} ${arr[i]===key?"=":"≠"} ${arr[i]}`,viewState("sequence",[row("array",arr),row("meta",[],{operation:request.operation,key,current:i})]),[action("compare","比较关键字",{target:i,value:arr[i]})]));if(arr[i]===key)return makeTrace(request,"顺序查找",`key=${key}`,`位置=${i+1}`,steps);}return makeTrace(request,"顺序查找",`key=${key}`,"未找到",steps);}if(request.operation==="binary"){if(!arr.length)return api.makeRuntimeError(request,"折半查找","EMPTY_TABLE","查找表为空：折半查找需要至少一个元素。",viewState("sequence",[row("array",[]),row("meta",[],{operation:request.operation,key})]),`key=${key}`);for(let i=1;i<arr.length;i++){if(arr[i-1]>arr[i])return api.makeRuntimeError(request,"折半查找","UNSORTED_INPUT",`折半查找要求有序表：a[${i-1}]=${arr[i-1]} > a[${i}]=${arr[i]}，当前输入不是非递减序列。请先排序，或改用顺序查找。`,viewState("sequence",[row("array",arr),row("meta",[],{operation:request.operation,key,violated:i})]),`key=${key}`);}const a=arr;let low=0,high=a.length-1;while(low<=high){const mid=Math.floor((low+high)/2);steps.push(makeStep(sid++,"compare","比较中间记录",`low=${low}, mid=${mid}, high=${high}，比较 ${key} 与 ${a[mid]}。`,viewState("sequence",[row("array",a),row("meta",[],{operation:request.operation,key,low,mid,high,current:mid})]),[action("compare","比较中间关键字",{target:mid,value:a[mid]})]));if(a[mid]===key)return makeTrace(request,"折半查找",`key=${key}`,`位置=${mid+1}`,steps);if(key<a[mid])high=mid-1;else low=mid+1;}return makeTrace(request,"折半查找",`key=${key}`,"未找到",steps);}if(!arr.length)return api.makeRuntimeError(request,"分块查找","EMPTY_TABLE","查找表为空：分块查找需要至少一个元素。",viewState("sequence",[row("array",[]),row("meta",[],{operation:request.operation,key})]),`key=${key}`);const blockSize=intParam(request.params,"blockSize",3,1,10);const blocks=[];for(let i=0;i<arr.length;i+=blockSize){const part=arr.slice(i,i+blockSize);blocks.push({start:i,end:i+part.length-1,max:Math.max(...part)});}for(let b=1;b<blocks.length;b++){const curMin=Math.min(...arr.slice(blocks[b].start,blocks[b].end+1));if(blocks[b-1].max>curMin)return api.makeRuntimeError(request,"分块查找","UNBLOCKED_ORDER",`分块查找要求“分块有序”：第 ${b} 块的最小值 ${curMin} 小于第 ${b} 块前一块的最大值 ${blocks[b-1].max}。请先排序或调整块划分。`,viewState("sequence",[row("array",arr),row("blocks",blocks),row("meta",[],{operation:request.operation,key,block:b})]),`key=${key}`);}let b=blocks.findIndex(x=>key<=x.max);steps.push(makeStep(sid++,"block","先查索引表",b>=0?`关键字应落在第 ${b+1} 块。`:"索引表中没有候选块。",viewState("sequence",[row("array",arr),row("blocks",blocks),row("meta",[],{operation:request.operation,key,block:b})])));if(b>=0)for(let i=blocks[b].start;i<=blocks[b].end;i++){steps.push(makeStep(sid++,"compare","块内顺序查找",`比较 ${key} 与 ${arr[i]}。`,viewState("sequence",[row("array",arr),row("blocks",blocks),row("meta",[],{operation:request.operation,key,block:b,current:i})])));if(arr[i]===key)return makeTrace(request,"分块查找",`key=${key}`,`位置=${i+1}`,steps);}return makeTrace(request,"分块查找",`key=${key}`,"未找到",steps);}

function bstBuild(values){let root=null;function insert(node,key){if(!node)return{key,left:null,right:null};if(key<node.key)node.left=insert(node.left,key);else if(key>node.key)node.right=insert(node.right,key);return node;}for(const k of values)root=insert(root,k);return root;}function bstRows(root,extra={}){const nodes=[],edges=[];let id=0;function walk(n,parent=null){if(!n)return null;const my=id++;nodes.push({id:my,label:String(n.key),key:n.key});if(parent!==null)edges.push([parent,my]);const l=walk(n.left,my),r=walk(n.right,my);return my;}walk(root);return [row("tree",[],{nodes,edges}),row("meta",[],extra)];}
function simulateBST(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const values=numberArray(request.initial_state.data,[45,24,53,12,28,90]);if(request.params.key!==undefined&&!Number.isFinite(Number(request.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");const key=Number(request.params.key??request.params.value??35);let root=bstBuild(values);let sid=1;const steps=[makeStep(sid++,"init","二叉排序树",`由 ${values.join(",")} 建树。`,viewState("tree",bstRows(root,{operation:request.operation,key})))];if(request.operation==="search"){let n=root;while(n){steps.push(makeStep(sid++,"compare","与当前结点比较",`${key} 与 ${n.key} 比较。`,viewState("tree",bstRows(root,{operation:request.operation,key,current:n.key})),[action("compare","比较关键字",{value:n.key})]));if(key===n.key)return makeTrace(request,"二叉排序树查找",`key=${key}`,"查找成功",steps);n=key<n.key?n.left:n.right;}return makeTrace(request,"二叉排序树查找",`key=${key}`,"未找到",steps);}if(request.operation==="insert"){let n=root;while(n){steps.push(makeStep(sid++,"compare","寻找插入位置",`${key} ${key<n.key?"<":key>n.key?">":"="} ${n.key}${key===n.key?"":`，转向${key<n.key?"左":"右"}子树`}。`,viewState("tree",bstRows(root,{operation:request.operation,key,current:n.key}))));if(key===n.key){steps.push(makeStep(sid,"skip","关键字已存在",`${key} 已在树中：二叉排序树不允许重复关键字，本次插入不改变树。`,viewState("tree",bstRows(root,{operation:request.operation,key,current:n.key})),[action("skip","重复关键字不插入",{value:key})]));return makeTrace(request,"二叉排序树插入",`key=${key}`,"未插入：关键字已存在",steps);}n=key<n.key?n.left:n.right;}function ins(node){if(!node)return{key,left:null,right:null};if(key<node.key)node.left=ins(node.left);else if(key>node.key)node.right=ins(node.right);return node;}root=ins(root);steps.push(makeStep(sid++,"insert","插入叶结点",`把 ${key} 插入空位置。`,viewState("tree",bstRows(root,{operation:request.operation,key,current:key})),[action("insert","新结点作为叶子插入",{value:key})]));return makeTrace(request,"二叉排序树插入",`key=${key}`,"插入完成",steps);}function del(node,k){if(!node)return null;if(k<node.key)node.left=del(node.left,k);else if(k>node.key)node.right=del(node.right,k);else if(!node.left)return node.right;else if(!node.right)return node.left;else{let s=node.right;while(s.left)s=s.left;node.key=s.key;node.right=del(node.right,s.key);}return node;}{let n=root;while(n){steps.push(makeStep(sid++,"compare","查找待删关键字",`${key} 与 ${n.key} 比较。`,viewState("tree",bstRows(root,{operation:request.operation,key,current:n.key})),[action("compare","比较关键字",{value:n.key})]));if(key===n.key)break;n=key<n.key?n.left:n.right;}if(!n){steps.push(makeStep(sid,"miss","未找到待删关键字",`树中没有 ${key}，删除操作不改变树。`,viewState("tree",bstRows(root,{operation:request.operation,key})),[action("miss","查找失败",{value:key})]));return makeTrace(request,"二叉排序树删除",`key=${key}`,"未找到关键字，未删除",steps);}const caseDesc=!n.left&&!n.right?"叶子结点，直接删除":(n.left&&n.right)?"双子树，用中序后继替代后删除":"单子树，孩子顶替被删结点";root=del(root,key);steps.push(makeStep(sid++,"delete","按删除情况调整",`被删关键字 ${key}：${caseDesc}。`,viewState("tree",bstRows(root,{operation:request.operation,key})),[action("delete","删除目标结点并保持中序有序",{value:key})]));return makeTrace(request,"二叉排序树删除",`key=${key}`,"删除完成",steps);}}

function h(n){return n?1+Math.max(h(n.left),h(n.right)):0;}function rotR(y){const x=y.left,t=x.right;x.right=y;y.left=t;return x;}function rotL(x){const y=x.right,t=y.left;y.left=x;x.right=t;return y;}function avlInsert(node,key,events){if(!node){events.push({type:"insert",key});return{key,left:null,right:null};}if(key<node.key)node.left=avlInsert(node.left,key,events);else if(key>node.key)node.right=avlInsert(node.right,key,events);else return node;const bf=h(node.left)-h(node.right);if(bf>1&&key<node.left.key){events.push({type:"LL",at:node.key});return rotR(node);}if(bf<-1&&key>node.right.key){events.push({type:"RR",at:node.key});return rotL(node);}if(bf>1&&key>node.left.key){events.push({type:"LR",at:node.key});node.left=rotL(node.left);return rotR(node);}if(bf<-1&&key<node.right.key){events.push({type:"RL",at:node.key});node.right=rotR(node.right);return rotL(node);}return node;}
function simulateAVL(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const values=numberArray(request.initial_state.data,[30,20,40,10]);const key=Number(request.params.key??request.params.value??5);if(!Number.isFinite(key))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");let root=null;for(const v of values)root=avlInsert(root,v,[]);const events=[];let sid=1;const steps=[makeStep(sid++,"init","初始 AVL 树",`准备插入 ${key}。`,viewState("tree",bstRows(root,{operation:"avl_insert",key})))];if(values.includes(key)){steps.push(makeStep(sid,"skip","关键字已存在",`${key} 已在树中：AVL 插入不允许重复关键字，树保持不变。`,viewState("tree",bstRows(root,{operation:"avl_insert",key})),[action("skip","重复关键字不插入",{value:key})]));return makeTrace(request,"AVL 树插入与平衡调整",`key=${key}`,"未插入：关键字已存在",steps);}root=avlInsert(root,key,events);for(const e of events){if(e.type==="insert")steps.push(makeStep(sid++,"insert","按 BST 规则插入",`${key} 先作为叶结点插入。`,viewState("tree",bstRows(root,{operation:"avl_insert",key,phase:e.type}))));else steps.push(makeStep(sid++,"rotate",`${e.type} 型失衡调整`,`在结点 ${e.at} 附近执行 ${e.type} 调整恢复平衡。`,viewState("tree",bstRows(root,{operation:"avl_insert",key,rotation:e.type,current:e.at})),[action("rotate","执行平衡旋转",{target:e.at,value:e.type})]));}return makeTrace(request,"AVL 树插入与平衡调整",`key=${key}`,"保持平衡",steps);}

function simpleBTree(keys,order=3){const maxKeys=order-1;const sorted=[...new Set(keys)].sort((a,b)=>a-b);const leaves=[];for(let i=0;i<sorted.length;i+=maxKeys)leaves.push({keys:sorted.slice(i,i+maxKeys),children:[]});if(leaves.length<=1)return{keys:leaves[0]?.keys||[],children:[]};return{keys:leaves.slice(1).map(x=>x.keys[0]),children:leaves};}
function btreeRows(tree,extra={}){const nodes=[],edges=[];let id=0;function walk(n,parent=null,depth=0){const my=id++;nodes.push({id:my,label:(n.keys||[]).join(" | "),keys:n.keys||[],depth});if(parent!==null)edges.push([parent,my]);for(const c of n.children||[])walk(c,my,depth+1);}walk(tree);return[row("tree",[],{nodes,edges,multiKey:true}),row("meta",[],extra)];}
function simulateBTree(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const keys=numberArray(request.initial_state.data,[10,20,30,40,50,60]);const key=Number(request.params.key??35);if(!Number.isFinite(key))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(request.params.key)} 必须是数字`,"key");const order=intParam(request.params,"order",3,3,6);if(request.operation!=="insert"&&keys.length===0)return api.makeRuntimeError(request,"B 树"+(request.operation==="search"?"查找":request.operation==="delete"?"删除":"操作"),"EMPTY_TREE","B 树为空：请先在 initialData 中提供关键字。",viewState("btree",btreeRows({keys:[],children:[]},{operation:request.operation,key,order})),`key=${key}`);let tree=simpleBTree(keys,order);const steps=[makeStep(1,"init","B 树初始状态",`${order} 阶 B 树。`,viewState("btree",btreeRows(tree,{operation:request.operation,key,order})))];if(request.operation==="search"){let candidates=[tree],sid=2;while(candidates.length){const n=candidates.shift();steps.push(makeStep(sid++,"compare","在结点内查找区间",`比较关键字 ${key} 与 [${n.keys.join(",")}]。`,viewState("btree",btreeRows(tree,{operation:request.operation,key,current:n.keys})),[action("compare","结点内顺序/折半比较",{value:key})]));if(n.keys.includes(key))return makeTrace(request,"B 树查找",`key=${key}`,"查找成功",steps);if(n.children?.length){let idx=n.keys.findIndex(k=>key<k);if(idx<0)idx=n.children.length-1;candidates.push(n.children[Math.min(idx,n.children.length-1)]);}}return makeTrace(request,"B 树查找",`key=${key}`,"未找到",steps);}let next=[...keys];if(request.operation==="insert"&&!next.includes(key))next.push(key);if(request.operation==="delete")next=next.filter(x=>x!==key);const nextTree=simpleBTree(next,order);steps.push(makeStep(2,request.operation,"执行结点调整",request.operation==="insert"?"插入后若结点关键字过多则分裂并向上提升中间关键字。":"删除后若关键字过少则借关键字或合并兄弟结点。",viewState("btree",btreeRows(nextTree,{operation:request.operation,key,order,adjusted:true})),[action(request.operation,request.operation==="insert"?"插入/分裂":"删除/借位/合并",{value:key})]));return makeTrace(request,request.operation==="insert"?"B 树插入":"B 树删除",`key=${key}`,"调整完成",steps);}

function hashIndex(key,m){const n=typeof key==="number"?key:[...String(key)].reduce((h,c)=>h*31+c.charCodeAt(0),0);return Math.abs(n)%m;}
function simulateHash(request,api){
  const {makeStep,makeTrace,action}=makeHelpers(api);const m=intParam(request.params,"tableSize",11,5,29);const existing=scalarArray(request.initial_state.data,[18,41,22,44,59]);const key=request.params.key??69;const op=request.operation;let sid=1;const table=Array(m).fill(null);const chains=Array.from({length:m},()=>[]);
  const offsets=(kind)=>{if(kind==="quadratic")return Array.from({length:m},(_,i)=>i===0?0:Math.ceil(i/2)**2*(i%2?1:-1));if(kind==="random"){let x=7;const a=[0];for(let i=1;i<m;i++){x=(x*17+11)%m;a.push(x);}return [...new Set(a)];}return Array.from({length:m},(_,i)=>i);};
  function probeSequence(k,kind){const h=hashIndex(k,m);if(kind==="rehash"){const step=1+(hashIndex(k,m-2)%(m-2));return Array.from({length:m},(_,i)=>(h+i*step)%m);}return offsets(kind).map(d=>(h+d+m)%m);}
  function probeInsert(k,kind="linear"){for(const idx of probeSequence(k,kind)){if(table[idx]===null){table[idx]=k;return idx;}}return-1;}
  const mode=op.startsWith("quadratic")?"quadratic":op.startsWith("random")?"random":op.startsWith("rehash")?"rehash":"linear";
  for(const k of existing){if(op.startsWith("chaining"))chains[hashIndex(k,m)].push(k);else probeInsert(k,mode);}
  const rows=()=>op.startsWith("chaining")?[row("buckets",chains.map((c)=>c.map(x=>({key:String(x),val:""})))),row("meta",[],{operation:op,key,tableSize:m})]:[row("table",table),row("meta",[],{operation:op,key,tableSize:m})];
  const steps=[makeStep(sid++,"init","哈希表初始状态",`表长 ${m}。`,viewState("hash_table",rows()))];
  if(op==="chaining_insert"){const h=hashIndex(key,m);chains[h].push(key);steps.push(makeStep(sid++,"insert","链地址法插入",`H(${key})=${h}，把关键字接入第 ${h} 个同义词链。`,viewState("hash_table",rows()),[action("insert","接入同义词链",{target:h,value:key})]));return makeTrace(request,"链地址法处理冲突",`key=${key}`,`bucket=${h}`,steps);}
  if(op==="chaining_search"){const h=hashIndex(key,m);for(let i=0;i<chains[h].length;i++){steps.push(makeStep(sid++,"compare","桶内比较",`比较 ${key} 与 ${chains[h][i]}。`,viewState("hash_table",[...rows(),row("probe",[],{bucket:h,index:i})])));if(chains[h][i]===key)return makeTrace(request,"链地址哈希查找",`key=${key}`,"查找成功",steps);}return makeTrace(request,"链地址哈希查找",`key=${key}`,"未找到",steps);}
  const search=op.endsWith("_search");const seq=probeSequence(key,mode);for(let t=0;t<seq.length;t++){const idx=seq[t];steps.push(makeStep(sid++,"probe","探测哈希地址",`第 ${t+1} 次探测地址 ${idx}。`,viewState("hash_table",[...rows(),row("probe",[],{index:idx,attempt:t,mode})]),[action("probe","按冲突处理规则计算下一地址",{target:idx})]));if(search){if(table[idx]===key)return makeTrace(request,`${mode} 探测哈希查找`,`key=${key}`,"查找成功",steps);if(table[idx]===null)return makeTrace(request,`${mode} 探测哈希查找`,`key=${key}`,"未找到",steps);}else if(table[idx]===null){table[idx]=key;steps.push(makeStep(sid++,"insert","写入空单元",`${key} 写入地址 ${idx}。`,viewState("hash_table",rows()),[action("insert","写入哈希表",{target:idx,value:key})]));const title={linear:"线性探测再散列",quadratic:"二次探测再散列",random:"伪随机探测再散列",rehash:"再哈希法"}[mode];return makeTrace(request,title,`key=${key}`,`address=${idx}`,steps);}}
  return makeTrace(request,"哈希探测",`key=${key}`,"探测结束",steps);
}

function sequenceRows(a,extra={}){return[row("array",a),row("meta",[],extra)];}
function simulateSort(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const a=numberArray(request.initial_state.data,[49,38,65,97,76,13,27,49]);const initialText=`[${a.join(",")}]`;const op=request.operation;let sid=1;const steps=[makeStep(sid++,"init","待排序序列",`[${a.join(", ")}]`,viewState("sort",sequenceRows(a,{operation:op})))];if(!a.length){steps.push(makeStep(sid,"done","序列为空","没有待排序记录，排序结束。",viewState("sort",sequenceRows(a,{operation:op,done:true}))));return makeTrace(request,`教材排序演示：${op}`,initialText,"[]",steps);}function snap(title,note,extra={},act=null){steps.push(makeStep(sid++,extra.phase||"sort",title,note,viewState("sort",sequenceRows(a,{operation:op,...extra})),act?[action(act,"排序操作",extra)]:[]));}
  if(op==="direct_insertion"||op==="binary_insertion"){
    for(let i=1;i<a.length;i++){const key=a[i];let pos=i;if(op==="binary_insertion"){let l=0,r=i-1;while(l<=r){const m=Math.floor((l+r)/2);snap("折半定位",`比较待插记录 ${key} 与 a[${m}]=${a[m]}。`,{current:m,low:l,high:r});if(key<a[m])r=m-1;else l=m+1;}pos=l;}else{while(pos>0&&a[pos-1]>key)pos--;}
      for(let j=i;j>pos;j--)a[j]=a[j-1];a[pos]=key;snap("插入到有序区",`把 ${key} 插入位置 ${pos}。`,{current:pos,sortedEnd:i},"insert");}
  } else if(op==="shell"){
    const gaps=Array.isArray(request.params.gaps)?request.params.gaps.map(Number).filter(x=>Number.isInteger(x)&&x>0):[Math.floor(a.length/2),1];for(const gap0 of gaps){const gap=Math.min(gap0,a.length-1);if(gap<1)continue;for(let i=gap;i<a.length;i++){const temp=a[i];let j=i;while(j>=gap&&a[j-gap]>temp){a[j]=a[j-gap];j-=gap;}a[j]=temp;}snap("完成一趟希尔插入",`增量 gap=${gap}。`,{gap},"group_sort");}
  } else if(op==="bubble"){
    for(let end=a.length-1;end>0;end--){let swapped=false;for(let i=0;i<end;i++){if(a[i]>a[i+1]){[a[i],a[i+1]]=[a[i+1],a[i]];swapped=true;snap("交换相邻记录",`交换下标 ${i} 与 ${i+1}。`,{i,j:i+1},"swap");}}snap("一趟冒泡结束",`最大元素已到位置 ${end}。`,{sortedStart:end});if(!swapped)break;}
  } else if(op==="quick"){
    function q(l,r){if(l>=r)return;const pivot=a[l];let i=l,j=r;while(i<j){while(i<j&&a[j]>=pivot)j--;if(i<j)a[i++]=a[j];while(i<j&&a[i]<=pivot)i++;if(i<j)a[j--]=a[i];}a[i]=pivot;snap("完成一次划分",`枢轴 ${pivot} 落在位置 ${i}。`,{low:l,high:r,pivotIndex:i},"partition");q(l,i-1);q(i+1,r);}q(0,a.length-1);
  } else if(op==="simple_selection"){
    for(let i=0;i<a.length-1;i++){let min=i;for(let j=i+1;j<a.length;j++)if(a[j]<a[min])min=j;if(min!==i)[a[i],a[min]]=[a[min],a[i]];snap("选择最小记录",`第 ${i+1} 个位置确定为 ${a[i]}。`,{current:i,selected:min},"select");}
  } else if(op==="tournament_selection"){
    const remain=a.map((v,i)=>({v,i})),out=[];while(remain.length){remain.sort((x,y)=>x.v-y.v);const win=remain.shift();out.push(win.v);snap("锦标赛选出当前最小值",`${win.v} 胜出并输出。`,{winner:win.v,output:[...out]},"select");}a.splice(0,a.length,...out);
  } else if(op==="heap"){
    function down(n,i){while(true){let largest=i,l=2*i+1,r=2*i+2;if(l<n&&a[l]>a[largest])largest=l;if(r<n&&a[r]>a[largest])largest=r;if(largest===i)break;[a[i],a[largest]]=[a[largest],a[i]];snap("向下调整堆",`交换 ${i} 与 ${largest}。`,{i,j:largest,heapSize:n},"swap");i=largest;}}
    for(let i=Math.floor(a.length/2)-1;i>=0;i--)down(a.length,i);snap("建立初始大根堆","从最后一个非叶结点向前调整。",{heapSize:a.length},"heapify");for(let end=a.length-1;end>0;end--){[a[0],a[end]]=[a[end],a[0]];snap("堆顶与末尾交换",`最大元素固定到位置 ${end}。`,{i:0,j:end,heapSize:end},"swap");down(end,0);}
  } else if(op==="merge"){
    const temp=Array(a.length);for(let width=1;width<a.length;width*=2){for(let l=0;l<a.length;l+=2*width){const m=Math.min(l+width,a.length),r=Math.min(l+2*width,a.length);let i=l,j=m,k=l;while(i<m&&j<r)temp[k++]=a[i]<=a[j]?a[i++]:a[j++];while(i<m)temp[k++]=a[i++];while(j<r)temp[k++]=a[j++];for(let x=l;x<r;x++)a[x]=temp[x];snap("归并相邻有序段",`合并区间 [${l},${m}) 和 [${m},${r})。`,{left:l,mid:m,right:r,width},"merge");}}
  } else if(op==="radix"){
    let exp=1,max=Math.max(...a.map(Math.abs));while(Math.floor(max/exp)>0){const buckets=Array.from({length:10},()=>[]);for(const v of a)buckets[Math.floor(Math.abs(v)/exp)%10].push(v);snap("按当前位分配",`按 ${exp===1?"个位":exp===10?"十位":"更高位"} 分到 10 个队列。`,{exp,buckets:deepClone(buckets)},"distribute");a.splice(0,a.length,...buckets.flat());snap("按桶序收集","依次收集 0~9 号队列。",{exp},"collect");exp*=10;}
  }
  snap("排序完成",`[${a.join(", ")}]`,{done:true},"done");return makeTrace(request,`教材排序演示：${op}`,initialText,`[${a.join(",")}]`,steps);}

function simulateExternalSort(request,api){const {makeStep,makeTrace,action}=makeHelpers(api);const rawRuns=request.params.runs!==undefined?request.params.runs:request.initial_state.data;const runs=Array.isArray(rawRuns)&&rawRuns.every(Array.isArray)?rawRuns.map(r=>numberArray(r,[])):[[1,7,13],[2,8,12],[3,6,15]];if(request.operation!=="replacement_selection"&&runs.length===0)throw new SimulationInputError("EMPTY_RUNS","归并段列表为空：请提供至少一个初始归并段","runs");let sid=1;const steps=[makeStep(sid++,"init","初始归并段",`${runs.length} 个归并段。`,viewState("external_sort",[row("runs",runs),row("meta",[],{operation:request.operation})]))];if(request.operation==="replacement_selection"){const input=numberArray(request.params.input??(Array.isArray(request.initial_state.data)&&!request.initial_state.data.every(Array.isArray)?request.initial_state.data:undefined),[12,7,18,3,15,9,20,4]);if(request.params.input!==undefined&&!input.length)throw new SimulationInputError("EMPTY_INPUT","输入记录为空：置换选择至少需要一条记录","input");const memSize=intParam(request.params,"memorySize",3,2,8);const pool=input.slice(0,memSize),rest=input.slice(memSize),out=[];let last=-Infinity;while(pool.length){pool.sort((a,b)=>a-b);let idx=pool.findIndex(x=>x>=last);if(idx<0){steps.push(makeStep(sid++,"new_run","开始新的初始归并段","内存中剩余记录均小于当前输出下界。",viewState("external_sort",[row("memory",pool),row("output",out),row("input",rest),row("meta",[],{operation:request.operation})])));last=-Infinity;idx=0;}const v=pool.splice(idx,1)[0];out.push(v);last=v;if(rest.length)pool.push(rest.shift());steps.push(makeStep(sid++,"output","输出当前可选最小记录",`输出 ${v}，并读入下一条记录。`,viewState("external_sort",[row("memory",pool),row("output",out),row("input",rest),row("meta",[],{operation:request.operation,last})]),[action("output","生成初始归并段",{value:v})]));}return makeTrace(request,"置换选择生成初始归并段","输入文件",`输出=${out.join(",")}`,steps);}let current=runs.map(r=>[...r]);const k=request.operation==="multiway_merge"?Math.max(2,intParam(request.params,"ways",3,2,8)):2;while(current.length>1){const next=[];for(let i=0;i<current.length;i+=k){const group=current.slice(i,i+k),pos=Array(group.length).fill(0),merged=[];while(true){let best=-1,bv=Infinity;for(let g=0;g<group.length;g++)if(pos[g]<group[g].length&&group[g][pos[g]]<bv){bv=group[g][pos[g]];best=g;}if(best<0)break;merged.push(group[best][pos[best]++]);}next.push(merged);steps.push(makeStep(sid++,"merge","归并一组顺串",`${group.length} 路输入归并得到 [${merged.join(",")}]。`,viewState("external_sort",[row("runs",current),row("outputRuns",next),row("meta",[],{operation:request.operation,ways:k})]),[action("merge","多路选择当前最小记录",{value:merged.length})]));}current=next;}return makeTrace(request,request.operation==="multiway_merge"?"多路归并外排序":"二路归并外排序","初始归并段",`最终顺串=[${current[0]?.join(",")||""}]`,steps);}

function simulateTextbookOperation(request, api) {
  if (ownOf(AUX_SUPPORTED_PAIRS, request.structure)?.has(request.operation)) return simulateAuxiliaryOperation(request, api);
  if (request.structure === "linked_list") return simulateLinkedList(request, api);
  if (request.structure === "doubly_linked_list") return simulateDoublyList(request, api);
  if (request.structure === "polynomial") return simulatePolynomial(request, api);
  if (request.structure === "stack_app" && request.operation === "bracket_match") return simulateBracketMatch(request, api);
  if (request.structure === "stack_app") return simulateExpression(request, api);
  if (request.structure === "recursion") return simulateHanoi(request, api);
  if (request.structure === "circular_queue") return simulateCircularQueue(request, api);
  if (request.structure === "string") return simulateString(request, api);
  if (request.structure === "sparse_matrix") return simulateSparseMatrix(request, api);
  if (request.structure === "generalized_list") return simulateGeneralizedList(request, api);
  if (request.structure === "tree") return simulateTree(request, api);
  if (request.structure === "huffman") return simulateHuffman(request, api);
  if (request.structure === "union_find") return simulateUnionFind(request, api);
  if (request.structure === "graph") return simulateGraph(request, api);
  if (request.structure === "search") return simulateSearch(request, api);
  if (request.structure === "bst") return simulateBST(request, api);
  if (request.structure === "avl") return simulateAVL(request, api);
  if (request.structure === "btree") return simulateBTree(request, api);
  if (request.structure === "hash_table") return simulateHash(request, api);
  if (request.structure === "sort") return simulateSort(request, api);
  if (request.structure === "external_sort") return simulateExternalSort(request, api);
  throw new Error(`未实现教材动画 ${request.structure}/${request.operation}`);
}

module.exports = {
  TEXTBOOK_SUPPORTED_PAIRS,
  normalizeTextbookRequest,
  simulateTextbookOperation,
  textbookStateValues
};
