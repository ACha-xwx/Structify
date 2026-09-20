const AUX_SUPPORTED_PAIRS = Object.freeze({
  linked_list: new Set(["initialize","build_head","build_tail","search_position","search_value","length","reverse"]),
  circular_linked_list: new Set(["initialize","build","merge_head_pointer","merge_tail_pointer"]),
  static_linked_list: new Set(["initialize","allocate","free"]),
  polynomial: new Set(["build"]),
  stack: new Set(["initialize","peek"]),
  double_stack: new Set(["initialize","push_left","push_right","pop_left","pop_right"]),
  linked_stack: new Set(["push","pop"]),
  recursion: new Set(["factorial_recursive","factorial_iterative","fibonacci_recursive","fibonacci_iterative"]),
  linked_queue: new Set(["initialize","enqueue","dequeue"]),
  circular_queue: new Set(["initialize"]),
  queue_app: new Set(["yanghui_triangle"]),
  circular_buffer: new Set(["process_input"]),
  string: new Set(["compare"]),
  heap_string: new Set(["insert","assign"]),
  special_matrix: new Set(["compress_map"]),
  generalized_list: new Set(["head"]),
  tree: new Set(["leaf_output","leaf_count","build_extended_preorder","height_postorder","height_preorder","thread_first","thread_traverse","path_to_node","similarity"]),
  forest: new Set(["to_binary_tree","binary_tree_to_forest"]),
  union_find: new Set(["initialize","path_compress_find"]),
  graph: new Set(["build_adjacency_matrix","build_adjacency_list","build_cross_list","dfs_nonrecursive","connected_components","compute_indegree"]),
  search: new Set(["sequential_sentinel"]),
  bst: new Set(["create","search_recursive","search_nonrecursive"]),
  avl: new Set(["rotate_ll","rotate_rr","rotate_lr","rotate_rl"]),
  btree: new Set(["locate_position","node_insert","split"]),
  hash_function: new Set(["digit_analysis","mid_square","folding_shift","folding_boundary","division_remainder","pseudo_random"]),
  sort: new Set(["quick_partition","heap_adjust","heap_build","merge_two","dutch_flag","linked_radix"]),
  external_sort: new Set(["disk_buffer_merge"])
});

const { SimulationInputError, ownOf, strictInt, normalizeParentArray, normalizeGraphSpec, requireVertex } = require("./textbook-validation");

function clone(v){return v===undefined?undefined:JSON.parse(JSON.stringify(v));}
function row(role,values,extra={}){return {role,values:clone(values),...clone(extra)};}
function view(kind,rows,meta={}){return {kind,view:clone(rows),meta:clone(meta)};}
function scalarArray(v,f=[]){if(!Array.isArray(v))return clone(f);return v.map((item,index)=>{if(item===null||["string","number","boolean"].includes(typeof item))return item;throw new SimulationInputError("INVALID_ELEMENT",`第 ${index+1} 个元素 ${JSON.stringify(item)} 类型不支持：只接受数字、字符串、布尔值或 null`,`data[${index}]`);});}
function numArray(v,f=[]){if(!Array.isArray(v))return clone(f);return v.map((item,index)=>{const n=Number(item);if(!Number.isFinite(n))throw new SimulationInputError("INVALID_ELEMENT",`第 ${index+1} 个元素 ${JSON.stringify(item)} 不是有效数字`,`data[${index}]`);return n;});}
function int(v,f,min=-1e9,max=1e9,name="参数"){if(v===undefined||v===null||v==="")return f;const n=Number(v);if(!Number.isInteger(n))throw new SimulationInputError("INVALID_PARAM",`参数 ${name}=${JSON.stringify(v)} 必须是整数`,name);if(n<min||n>max)throw new SimulationInputError("PARAM_OUT_OF_RANGE",`参数 ${name}=${n} 超出支持范围 [${min}, ${max}]`,name);return n;}
function helpers(api){return {makeStep:api.makeStep,makeTrace:api.makeTrace,action:api.action};}

function simulateLinkedListAux(req,api){
  const {makeStep,makeTrace,action}=helpers(api); const op=req.operation;
  const data=scalarArray(req.initial_state.data,[10,20,30,40]); let sid=1;
  const state=(items,extra={})=>view("linked_list",[row("L",items),row("meta",[],{operation:op,...extra})]);
  const steps=[makeStep(sid++,"init","单链表状态",data.length?`当前结点：${data.join(" → ")}`:"当前为空表。",state(data))];
  if(op==="initialize"){steps.push(makeStep(sid,"init","建立头结点","申请头结点并令 next=NULL。",state([], {head:true}),[action("allocate","申请头结点",{target:"head"})]));return makeTrace(req,"初始化单链表","未初始化","空表 L",steps);}
  if(op==="build_head"||op==="build_tail"){
    const input=scalarArray(req.params.values??data,[1,2,3,4]); const out=[];
    for(const x of input){ if(op==="build_head")out.unshift(x);else out.push(x); steps.push(makeStep(sid++,"link",op==="build_head"?"头插新结点":"尾插新结点",op==="build_head"?`把 ${x} 插到头结点之后。`:`把 ${x} 接到尾指针之后并更新尾指针。`,state(out,{input:x}),[action("link",op==="build_head"?"头插":"尾插",{value:x})])); }
    return makeTrace(req,op==="build_head"?"头插法建立单链表":"尾插法建立单链表",`输入=${input.join(",")}`,`L=${out.join("→")}`,steps);
  }
  if(op==="search_position"){
    const position=int(req.params.position,2,1,Math.max(1,data.length));
    for(let i=0;i<data.length&&i<position;i++)steps.push(makeStep(sid++,"traverse","工作指针后移",`当前到达第 ${i+1} 个结点 ${data[i]}。`,state(data,{current:i,position}),[action("move","沿 next 后移",{target:i,value:data[i]})]));
    return makeTrace(req,"单链表按位置查找",`i=${position}`,position<=data.length?`找到 ${data[position-1]}`:"不存在",steps);
  }
  if(op==="search_value"){
    const key=req.params.key??data[Math.min(2,data.length-1)]; let found=-1;
    for(let i=0;i<data.length;i++){steps.push(makeStep(sid++,"compare","比较结点值",`${String(data[i])} ${data[i]===key?"=":"≠"} ${String(key)}`,state(data,{current:i,key}),[action("compare","比较 data 与 key",{target:i,value:key})]));if(data[i]===key){found=i;break;}}
    return makeTrace(req,"单链表按值查找",`key=${String(key)}`,found>=0?`第 ${found+1} 个结点`:`未找到`,steps);
  }
  if(op==="length"){
    for(let i=0;i<data.length;i++)steps.push(makeStep(sid++,"count","计数并后移",`已数到第 ${i+1} 个数据结点。`,state(data,{current:i,count:i+1}),[action("count","结点计数加 1",{value:i+1})]));
    return makeTrace(req,"求单链表长度","从头结点后开始计数",`length=${data.length}`,steps);
  }
  const work=[...data]; let prev=null,cur=0;
  while(cur<work.length){steps.push(makeStep(sid++,"reverse","反转当前 next",`让结点 ${work[cur]} 的 next 指向前驱 ${prev===null?"NULL":work[prev]}。`,state(work,{current:cur,previous:prev===null?null:prev,reversedThrough:cur}),[action("link","next 指向前驱",{from:cur,to:prev})]));prev=cur;cur++;}
  const out=[...work].reverse();steps.push(makeStep(sid,"done","头指针改指向原尾结点",`反转完成：${out.join(" → ")}`,state(out,{done:true})));return makeTrace(req,"单链表逆置",`L=${data.join("→")}`,`L=${out.join("→")}`,steps);
}

function simulateCircularList(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation; const raw=Array.isArray(req.initial_state.data)?req.initial_state.data:[];
  const left=scalarArray(req.params.left??raw[0],[1,3,5]),right=scalarArray(req.params.right??raw[1],[2,4,6]); let sid=1;
  const state=(la,lb=[],extra={})=>view("circular_linked_list",[row("LA",la),row("LB",lb),row("meta",[],{operation:op,circular:true,...extra})]);
  if(op==="initialize"){return makeTrace(req,"初始化循环单链表","未初始化","head.next=head",[makeStep(1,"init","建立头结点并自环","空循环链表中头结点 next 指向自身。",state([],[],{headSelfLoop:true}),[action("link","head.next=head",{from:"head",to:"head"})])]);}
  if(op==="build"){const vals=scalarArray(req.params.values??raw,[1,2,3,4]),out=[];const steps=[makeStep(sid++,"init","建立循环表头","先建立空循环链表。",state(out,[],{headSelfLoop:true}))];for(const x of vals){out.push(x);steps.push(makeStep(sid++,"link","尾插并保持首尾相接",`插入 ${x}，新尾结点 next 指回头结点。`,state(out,[],{tail:out.length-1}),[action("link","尾结点接回 head",{value:x})]));}return makeTrace(req,"建立循环单链表",`输入=${vals.join(",")}`,`循环表=${out.join("→")}→head`,steps);}
  const steps=[makeStep(sid++,"init","两个循环单链表",`LA=${left.join("→")}，LB=${right.join("→")}`,state(left,right))];
  steps.push(makeStep(sid++,"locate","定位两个表尾",op==="merge_head_pointer"?"分别从头指针沿 next 找到表尾。":"尾指针已直接指向两个表尾。",state(left,right,{tailsLocated:true}),[action("move","定位表尾",{target:"tail"})]));
  const merged=[...left,...right];steps.push(makeStep(sid++,"link","首尾重新连接",op==="merge_head_pointer"?"LA 尾结点连接 LB 首数据结点，LB 尾结点再接回 LA 头结点。":"利用尾指针直接交换/重接头尾链接。",state(merged,[],{merged:true}),[action("link","连接两个循环表",{from:"LA.tail",to:"LB.first"})]));
  return makeTrace(req,op==="merge_head_pointer"?"循环单链表合并（头指针）":"循环单链表合并（尾指针）","两个循环表",`L=${merged.join("→")}→head`,steps);
}

function simulateStaticList(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation; const size=int(req.params.size,8,4,30,"size");let sid=1;
  let nodes=Array.from({length:size},(_,i)=>({index:i,data:null,cursor:i+1<size?i+1:0,free:true}));
  const st=(extra={})=>view("static_linked_list",[row("nodes",nodes),row("meta",[],{operation:op,...extra})]);
  if(op==="initialize")return makeTrace(req,"静态单链表初始化",`空间=${size}`,"备用链建立",[makeStep(1,"init","串联备用结点","用 cursor 把未使用结点串成备用链。",st({available:0}),[action("link","建立备用链",{target:"cursor"})])]);
  const used=Array.isArray(req.params.usedIndices)?req.params.usedIndices.map((v,i)=>{const n=Number(v);if(!Number.isInteger(n)||n<0||n>=size)throw new SimulationInputError("INVALID_INDEX",`usedIndices 第 ${i+1} 项 ${JSON.stringify(v)} 必须是 0~${size-1} 的下标`,`usedIndices[${i}]`);return n;}):[1,2,3];for(const i of used){nodes[i].free=false;nodes[i].data=`D${i}`;}
  const freeIdx=nodes.findIndex((n,i)=>i>0&&n.free);
  const steps=[makeStep(sid++,"init","静态链表空间","显示已用结点与备用结点。",st({used}))];
  if(op==="allocate"){if(freeIdx<0)return makeTrace(req,"静态链表申请结点","备用链","无空闲结点",steps);nodes[freeIdx].free=false;nodes[freeIdx].data=req.params.value??"NEW";steps.push(makeStep(sid,"allocate","从备用链摘下首结点",`申请下标 ${freeIdx}，备用链头移到下一空闲结点。`,st({allocated:freeIdx}),[action("allocate","摘下备用结点",{target:freeIdx,value:nodes[freeIdx].data})]));return makeTrace(req,"静态链表申请空间","备用链",`index=${freeIdx}`,steps);}
  const target=int(req.params.index,used[0]??1,1,size-1,"index");if(nodes[target].free)throw new SimulationInputError("INVALID_INDEX",`下标 ${target} 的结点不在已用状态，无法释放（已用下标：${used.join("、")}）`,"index");nodes[target].free=true;nodes[target].data=null;steps.push(makeStep(sid,"free","归还结点到备用链","把被释放结点插回备用链表头。",st({freed:target}),[action("free","归还空间",{target})]));return makeTrace(req,"静态链表释放空间",`index=${target}`,"已归还备用链",steps);
}

function normalizeTerms(v,f=[{coef:3,exp:3},{coef:2,exp:1},{coef:1,exp:0}]){const a=Array.isArray(v)?v:f;return a.map((t,i)=>{if(!t||typeof t!=="object"||Array.isArray(t))throw new SimulationInputError("INVALID_TERM",`第 ${i+1} 项 ${JSON.stringify(t)} 格式不正确：多项式项应为 { "coef": 数字, "exp": 数字 }（如 { "coef": 3, "exp": 4 }）`,`terms[${i}]`);const coef=Number(t.coef??t.coefficient),exp=Number(t.exp??t.exponent);if(!Number.isFinite(coef))throw new SimulationInputError("INVALID_TERM",`第 ${i+1} 项的系数必须是有限数字（当前 ${JSON.stringify(t.coef??t.coefficient)}）`,`terms[${i}]`);if(!Number.isInteger(exp)||exp<0)throw new SimulationInputError("INVALID_TERM",`第 ${i+1} 项的指数必须是非负整数（当前 ${JSON.stringify(t.exp??t.exponent)}）`,`terms[${i}]`);return {coef,exp};});}
function simulatePolynomialBuild(req,api){const {makeStep,makeTrace,action}=helpers(api);const terms=normalizeTerms(req.params.terms??req.initial_state.data);const list=[];let sid=1;const steps=[makeStep(sid++,"init","空多项式链表","按指数有序插入各项。",view("polynomial",[row("P",list),row("meta",[],{operation:req.operation})]))];for(const t of terms){let pos=list.findIndex(x=>x.exp<t.exp);if(pos<0)pos=list.length;list.splice(pos,0,t);steps.push(makeStep(sid++,"insert","插入多项式项",`${t.coef}x^${t.exp} 插入指数有序位置。`,view("polynomial",[row("P",list),row("meta",[],{current:t})]),[action("insert","插入项结点",{target:pos,value:`${t.coef}x^${t.exp}`})]));}return makeTrace(req,"建立一元多项式链表","输入项",`P=${list.map(t=>`${t.coef}x^${t.exp}`).join("+")}`,steps);}

function simulateStackAux(req,api){const {makeStep,makeTrace,action}=helpers(api);const data=scalarArray(req.initial_state.data,[2,5,7]);const op=req.operation;if(op==="initialize"){const capacity=int(req.params.capacity,10,1,100);return makeTrace(req,"顺序栈初始化",`capacity=${capacity}`,"top=-1",[makeStep(1,"init","建立空顺序栈","令 top=-1，表示栈中还没有元素。",view("stack",[row("stack",[]),row("meta",[],{top:-1,capacity,operation:op})]),[action("assign","设置栈顶指针",{target:"top",value:-1})])]);}const top=data.length-1;const steps=[makeStep(1,"init","顺序栈状态",`top=${top}`,view("stack",[row("stack",data),row("meta",[],{top,operation:"peek"})]))];steps.push(makeStep(2,"read","读取栈顶但不修改 top",data.length?`读取 ${data[top]}，top 仍为 ${top}。`:"空栈不能读取栈顶。",view("stack",[row("stack",data),row("meta",[],{top,peek:data[top]??null,operation:"peek"})]),[action("read","读取栈顶",{target:top,value:data[top]??null})]));return makeTrace(req,"读取栈顶元素",`top=${top}`,data.length?String(data[top]):"空栈",steps);}

function simulateDoubleStack(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const capacity=int(req.params.capacity,8,4,30,"capacity");const raw=req.initial_state.data;const left=scalarArray(req.params.left??raw?.[0],[1,2]),right=scalarArray(req.params.right??raw?.[1],[9,8]);let sid=1;const st=(extra={})=>view("double_stack",[row("left",left),row("right",right),row("meta",[],{capacity,topLeft:left.length-1,topRight:capacity-right.length,...extra})]);const steps=[makeStep(sid++,"init","双端顺序栈",`两个栈从数组两端向中间增长。`,st({operation:op}))];if(op==="initialize")return makeTrace(req,"双端顺序栈初始化","共享数组","两端栈顶就位",steps);if(op.startsWith("push")){if(left.length+right.length>=capacity)return api.makeRuntimeError(req,"双端顺序栈进栈","STACK_OVERFLOW",`共享数组已满（capacity=${capacity}，两栈共占 ${left.length+right.length} 个单元），不能再进栈。`,st({operation:op}),"栈满");const side=op.endsWith("left")?left:right;const val=req.params.value??(side===left?3:7);side.push(val);steps.push(makeStep(sid,"push",side===left?"左栈进栈":"右栈进栈",`${val} 从${side===left?"左":"右"}端进入共享数组。`,st({operation:op}),[action("push","双端栈进栈",{value:val})]));}else{const side=op.endsWith("left")?left:right;if(!side.length)return api.makeRuntimeError(req,"双端顺序栈出栈","STACK_UNDERFLOW",`${op.endsWith("left")?"左":"右"}栈为空，不能出栈。`,st({operation:op}),"空栈");const val=side.pop();steps.push(makeStep(sid,"pop",side===left?"左栈出栈":"右栈出栈",`${String(val)} 从${side===left?"左":"右"}端退出。`,st({operation:op}),[action("pop","双端栈出栈",{value:val})]));}return makeTrace(req,"双端顺序栈操作","共享空间","操作完成",steps);}

function simulateLinkedStack(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const data=scalarArray(req.initial_state.data,[2,5,7]);let sid=1;const st=(extra={})=>view("linked_stack",[row("stack",data),row("meta",[],{top:data.length?0:null,operation:op,...extra})]);const steps=[makeStep(sid++,"init","链栈状态","栈顶指针指向链表首结点。",st())];if(op==="push"){const v=req.params.value??9;data.unshift(v);steps.push(makeStep(sid,"link","新结点作为栈顶",`申请结点 ${v}，其 next 指向原栈顶。`,st({value:v}),[action("link","新结点接到链首",{value:v})]));}else{if(!data.length)return api.makeRuntimeError(req,"链栈出栈","STACK_UNDERFLOW","链栈为空，不能出栈。",st({operation:op}),"空栈");const v=data.shift();steps.push(makeStep(sid,"unlink","删除栈顶结点",`取出 ${String(v)}，top 改指向后继。`,st({removed:v}),[action("unlink","释放原栈顶",{value:v})]));}return makeTrace(req,op==="push"?"链栈进栈":"链栈出栈","链栈","操作完成",steps);}

function simulateRecursionAux(req, api) {
  const { makeStep, makeTrace, action } = helpers(api);
  const op = req.operation;
  // 各操作的动画步数上限不同：递归展开按指数增长，越界直接报错而不是悄悄夹小。
  const RECURSION_LIMITS = {
    factorial_recursive: [0, 12],
    factorial_iterative: [0, 20],
    fibonacci_recursive: [1, 8],
    fibonacci_iterative: [1, 20]
  };
  const [nMin, nMax] = RECURSION_LIMITS[op] || [0, 12];
  const n = int(req.params.n, Math.min(5, nMax), nMin, nMax, "n");
  let sid = 1;
  const steps = [];

  if (op === "factorial_recursive") {
    const stack = [];
    steps.push(makeStep(
      sid++, "init", "递归求阶乘", `计算 ${n}!。`,
      view("recursion", [row("call_stack", stack), row("meta", [], { n, operation: op })])
    ));
    for (let k = n; k > 1; k--) {
      stack.push(k);
      steps.push(makeStep(
        sid++, "call", "递归压栈", `Fact(${k}) 等待 Fact(${k - 1}) 返回。`,
        view("recursion", [row("call_stack", stack), row("meta", [], { current: k })]),
        [action("push", "保存递归层", { value: k })]
      ));
    }
    let result = 1;
    while (stack.length) {
      const k = stack.pop();
      result *= k;
      steps.push(makeStep(
        sid++, "return", "递归返回", `乘以 ${k}，当前结果 ${result}。`,
        view("recursion", [row("call_stack", stack), row("meta", [], { result, current: k })]),
        [action("pop", "返回一层", { value: k })]
      ));
    }
    return makeTrace(req, "阶乘递归调用过程", `n=${n}`, `${n}!=${result}`, steps);
  }

  if (op === "factorial_iterative") {
    let result = 1;
    steps.push(makeStep(
      sid++, "init", "循环求阶乘", `从 1 开始累计到 ${n}。`,
      view("recursion", [row("meta", [], { n, result, operation: op })])
    ));
    for (let k = 2; k <= n; k++) {
      result *= k;
      steps.push(makeStep(
        sid++, "iterate", "循环累乘", `result *= ${k} → ${result}`,
        view("recursion", [row("meta", [], { n, k, result })]),
        [action("compute", "累乘", { value: result })]
      ));
    }
    return makeTrace(req, "阶乘非递归过程", `n=${n}`, `${n}!=${result}`, steps);
  }

  if (op === "fibonacci_iterative") {
    let a = 0, b = 1;
    const seq = n === 0 ? [0] : [0, 1];
    steps.push(makeStep(
      sid++, "init", "迭代计算 Fibonacci", "F(0)=0,F(1)=1。",
      view("recursion", [row("sequence", seq), row("meta", [], { n, a, b, operation: op })])
    ));
    for (let k = 2; k <= n; k++) {
      [a, b] = [b, a + b];
      seq.push(b);
      steps.push(makeStep(
        sid++, "iterate", "滚动更新两个前驱值", `F(${k})=${b}`,
        view("recursion", [row("sequence", seq), row("meta", [], { k, a, b })]),
        [action("compute", "由前两项相加", { value: b })]
      ));
    }
    const result = n === 0 ? 0 : n === 1 ? 1 : b;
    return makeTrace(req, "Fibonacci 非递归过程", `n=${n}`, `F(${n})=${result}`, steps);
  }

  const stack = [];
  function fib(k) {
    stack.push(k);
    steps.push(makeStep(
      sid++, "call", "递归调用", `进入 Fib(${k})。`,
      view("recursion", [row("call_stack", stack), row("meta", [], { current: k, operation: op })]),
      [action("push", "递归压栈", { value: k })]
    ));
    let result;
    if (k <= 1) result = k;
    else result = fib(k - 1) + fib(k - 2);
    stack.pop();
    steps.push(makeStep(
      sid++, "return", "递归返回", `Fib(${k})=${result}。`,
      view("recursion", [row("call_stack", stack), row("meta", [], { current: k, result })]),
      [action("pop", "递归返回", { value: result })]
    ));
    return result;
  }
  const result = fib(n);
  return makeTrace(req, "Fibonacci 递归调用过程", `n=${n}`, `F(${n})=${result}`, steps);
}

function simulateLinkedQueue(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const data=scalarArray(req.initial_state.data,[4,7,9]);let sid=1;const st=(extra={})=>view("linked_queue",[row("queue",data),row("meta",[],{front:data.length?0:null,rear:data.length?data.length-1:null,operation:op,...extra})]);const steps=[makeStep(sid++,"init","链队列状态","front 指向头结点，rear 指向队尾。",st())];if(op==="initialize")return makeTrace(req,"链队列初始化","未初始化","front=rear=head",steps);if(op==="enqueue"){const v=req.params.value??12;data.push(v);steps.push(makeStep(sid,"link","新结点接入队尾",`rear.next 指向 ${v}，rear 后移到新结点。`,st({value:v}),[action("link","尾插新结点",{value:v})]));}else{if(!data.length)return api.makeRuntimeError(req,"链队列出队","QUEUE_UNDERFLOW","队列为空，不能出队。",st({operation:op}),"空队列");const v=data.shift();steps.push(makeStep(sid,"unlink","队首结点出队",`头结点后的首数据结点 ${String(v)} 被摘下。`,st({removed:v}),[action("unlink","front 越过队首数据结点",{value:v})]));}return makeTrace(req,op==="enqueue"?"链队列入队":"链队列出队","链队列","操作完成",steps);}

function simulateCircularQueueInit(req,api){const {makeStep,makeTrace}=helpers(api);const cap=int(req.params.capacity,6,2,30);return makeTrace(req,"循环队列初始化",`capacity=${cap}`,"front=rear=0",[makeStep(1,"init","设置队首队尾指针","令 front=rear=0，队列为空。",view("circular_queue",[row("buffer",Array(cap).fill(null)),row("meta",[],{capacity:cap,front:0,rear:0,count:0,operation:"initialize"})]))]);}

function simulateYanghui(req,api){const {makeStep,makeTrace,action}=helpers(api);const n=int(req.params.rows,6,2,12);let prev=[1],sid=1;const all=[prev];const steps=[makeStep(sid++,"init","杨辉三角第 1 行","队列保存当前行数据。",view("queue_app",[row("current",prev),row("rows",all),row("meta",[],{row:1,operation:req.operation})]))];for(let r=2;r<=n;r++){const next=[1];for(let i=0;i<prev.length-1;i++)next.push(prev[i]+prev[i+1]);next.push(1);all.push(next);steps.push(makeStep(sid++,"queue","由上一行生成下一行",`第 ${r} 行：${next.join(" ")}`,view("queue_app",[row("current",next),row("rows",all),row("meta",[],{row:r})]),[action("compute","相邻两个队列元素相加",{value:r})]));prev=next;}return makeTrace(req,"利用队列生成杨辉三角",`n=${n}`,`生成 ${n} 行`,steps);}

function simulateCircularBuffer(req,api){const {makeStep,makeTrace,action}=helpers(api);const cap=int(req.params.capacity,6,3,20,"capacity");const rawInput=String(req.params.input??"ABCDEFGH");if(rawInput.length>30)throw new SimulationInputError("INPUT_TOO_LONG",`输入长度 ${rawInput.length} 超过 30 字符上限`,"input");const input=[...rawInput];const buf=Array(cap).fill(null);let front=0,rear=0,count=0,sid=1;const steps=[makeStep(sid++,"init","循环输入缓冲区","front=rear=0。",view("circular_buffer",[row("buffer",buf),row("meta",[],{front,rear,count,capacity:cap})]))];for(const ch of input){if(count===cap){const removed=buf[front];buf[front]=null;front=(front+1)%cap;count--;steps.push(makeStep(sid++,"consume","缓冲区满，先消费最早字符",`读出 ${removed}，front 循环后移。`,view("circular_buffer",[row("buffer",buf),row("meta",[],{front,rear,count,current:removed})]),[action("dequeue","读出字符",{value:removed})]));}buf[rear]=ch;rear=(rear+1)%cap;count++;steps.push(makeStep(sid++,"input","写入一个键盘字符",`${ch} 写入 rear 原位置，rear 取模后移。`,view("circular_buffer",[row("buffer",buf),row("meta",[],{front,rear,count,current:ch})]),[action("enqueue","写入缓冲区",{value:ch})]));}return makeTrace(req,"键盘输入循环缓冲区",`input=${input.join("")}`,`缓冲区保留 ${count} 个字符`,steps);}

function simulateStringCompare(req,api){const {makeStep,makeTrace,action}=helpers(api);const a=[...String(req.params.left??"DATA")],b=[...String(req.params.right??"DATE")];let sid=1;const steps=[makeStep(sid++,"init","两个字符串","从第一个字符开始比较。",view("string_match",[row("left",a),row("right",b),row("meta",[],{i:0,operation:req.operation})]))];let result=0,i=0;for(;i<Math.min(a.length,b.length);i++){steps.push(makeStep(sid++,"compare","逐字符比较",`${a[i]} ${a[i]===b[i]?"=":a[i]<b[i]?"<":">"} ${b[i]}`,view("string_match",[row("left",a),row("right",b),row("meta",[],{i})]),[action("compare","比较对应字符",{target:i})]));if(a[i]!==b[i]){result=a[i]<b[i]?-1:1;break;}}if(result===0)result=a.length===b.length?0:a.length<b.length?-1:1;return makeTrace(req,"串比较",a.join(""),result===0?"相等":result<0?"左串较小":"左串较大",steps);}

function simulateHeapString(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const text=String(req.params.text??req.initial_state.data??"DATA"),value=String(req.params.value??"STRUCT"),pos=int(req.params.position,Math.min(3,text.length+1),1,text.length+1);let sid=1;const blocks=[...text];const steps=[makeStep(sid++,"init","堆串当前值",text,view("heap_string",[row("chars",blocks),row("meta",[],{length:blocks.length,operation:op})]))];if(op==="assign"){steps.push(makeStep(sid++,"free","释放原串值空间","旧串值空间先归还堆。",view("heap_string",[row("chars",[]),row("meta",[],{operation:op,phase:"free"})]),[action("free","释放旧空间",{value:blocks.length})]));const next=[...value];steps.push(makeStep(sid,"allocate","申请新空间并复制",`申请 ${next.length} 个字符空间，写入 ${value}。`,view("heap_string",[row("chars",next),row("meta",[],{length:next.length,operation:op})]),[action("allocate","动态申请串值空间",{value:next.length})]));return makeTrace(req,"堆串赋值",text,value,steps);}const next=[...text];next.splice(pos-1,0,...value);steps.push(makeStep(sid++,"allocate","申请扩大后的串值空间",`新长度 ${next.length}。`,view("heap_string",[row("chars",next),row("meta",[],{position:pos,length:next.length,operation:op})]),[action("allocate","申请新空间",{value:next.length})]));steps.push(makeStep(sid,"copy","复制前段、插入串和后段",`在第 ${pos} 个位置插入 ${value}。`,view("heap_string",[row("chars",next),row("meta",[],{position:pos,length:next.length,operation:op})]),[action("insert","复制并插入",{target:pos-1,value})]));return makeTrace(req,"堆串插入",text,next.join(""),steps);}

function simulateSpecialMatrix(req,api){const {makeStep,makeTrace,action}=helpers(api);const n=int(req.params.n,5,2,20),kind=String(req.params.kind??"lower_triangular"),i=int(req.params.i,4,1,n),j=int(req.params.j,2,1,n);let k,stored=true,formula="";if(kind==="symmetric"){const r=Math.max(i,j),c=Math.min(i,j);k=(r-1)*r/2+(c-1);formula="对称元素统一映射到下三角";}else if(kind==="upper_triangular"){if(i<=j){k=(i-1)*(2*n-i+2)/2+(j-i);formula="按行存储上三角";}else{stored=false;k=n*(n+1)/2;formula="下三角常量区";}}else if(kind==="tridiagonal"){if(Math.abs(i-j)<=1){k=2*i+j-3;formula="三对角带按行紧凑存储";}else{stored=false;k=-1;formula="带外元素为 0，不进入压缩数组";}}else{if(i>=j){k=(i-1)*i/2+(j-1);formula="按行存储下三角";}else{stored=false;k=n*(n+1)/2;formula="上三角常量区";}}const rows=[row("matrix_index",[],{i,j,n,kind}),row("compressed",[],{index:k,stored,formula})];const steps=[makeStep(1,"locate","定位矩阵元素",`定位 A[${i},${j}]。`,view("special_matrix",rows)),makeStep(2,"map","计算压缩下标",stored?`${formula}，映射到一维数组下标 k=${k}。`:`${formula}${k>=0?`，公共常量位置 k=${k}`:""}。`,view("special_matrix",rows),[action("map","二维下标映射到一维下标",{from:`${i},${j}`,to:k})])];return makeTrace(req,"特殊矩阵压缩存储映射",`A[${i},${j}]`,stored?`B[${k}]`:(k>=0?`常量 B[${k}]`:"不存储"),steps);}

function simulateGeneralizedHead(req,api){const {makeStep,makeTrace,action}=helpers(api);const data=Array.isArray(req.initial_state.data)?req.initial_state.data:["a",["b","c"]];if(!data.length)return api.makeRuntimeError(req,"求广义表表头","EMPTY_LIST","空表没有表头：广义表至少要有一个元素。",view("generalized_list",[row("list",[])]),"空广义表");const head=data[0];return makeTrace(req,"求广义表表头","广义表",`head=${JSON.stringify(head)}`,[makeStep(1,"init","广义表结构","观察最外层第一个表元素。",view("generalized_list",[row("list",data)])),makeStep(2,"select","取表头",`最外层第一个元素 ${JSON.stringify(head)} 即表头。`,view("generalized_list",[row("list",data),row("head",head)]),[action("select","选择第一个表元素",{target:0})])]);}

function treeArray(v){if(!Array.isArray(v)||v.length===0)throw new SimulationInputError("EMPTY_TREE","二叉树为空：initialData 需要至少一个结点（空子树用 null 表示）","initialData");if(v.length>31)throw new SimulationInputError("INPUT_TOO_LARGE",`二叉树动画最多演示 31 个结点（5 层完全二叉树），当前 ${v.length} 个`,"initialData");return v.map(x=>x===undefined?null:x);}
function child(arr,i){const l=2*i+1,r=2*i+2;return [l<arr.length&&arr[l]!==null?l:-1,r<arr.length&&arr[r]!==null?r:-1];}
function treeRows(arr,extra={}){const nodes=arr.map((x,i)=>x===null?null:{id:i,label:String(x),index:i}).filter(Boolean),edges=[];for(const n of nodes){const[l,r]=child(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);}return [row("tree",[],{nodes,edges}),row("meta",[],extra)];}
function treeHeight(arr,i=0){if(i<0||i>=arr.length||arr[i]===null)return 0;const[l,r]=child(arr,i);return 1+Math.max(treeHeight(arr,l),treeHeight(arr,r));}
function preorder(arr){const out=[];function f(i){if(i<0||i>=arr.length||arr[i]===null)return;out.push(i);const[l,r]=child(arr,i);f(l);f(r);}f(0);return out;}
function inorder(arr){const out=[];function f(i){if(i<0||i>=arr.length||arr[i]===null)return;const[l,r]=child(arr,i);f(l);out.push(i);f(r);}f(0);return out;}
function simulateTreeAux(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  if(op==="build_extended_preorder"){const rawSeq=String(req.params.sequence??"AB##CD###");if(rawSeq.length>63)throw new SimulationInputError("INPUT_TOO_LONG",`扩展先序序列长度 ${rawSeq.length} 超过 63 字符上限（对应最多 31 个结点）`,"sequence");if(!rawSeq.length)throw new SimulationInputError("EMPTY_SEQUENCE","扩展先序序列不能为空","sequence");const seq=rawSeq;const arr=[];let sid=1;const steps=[makeStep(sid++,"init","准备按扩展先序建树","按序列递归建立二叉链表。",view("tree",treeRows(arr,{operation:op})))];const built=[];let k=0;function rec(index){if(k>=seq.length)return;const ch=seq[k++];if(ch==="#")return;while(built.length<=index)built.push(null);built[index]=ch;steps.push(makeStep(sid++,"build","读取扩展先序符号",`${ch} 建立为结点；# 表示空子树。`,view("tree",treeRows(built,{operation:op,readIndex:k-1,current:index})),[action("insert","递归建立结点",{target:index,value:ch})]));rec(2*index+1);rec(2*index+2);}rec(0);if(!built.some(x=>x!==null))throw new SimulationInputError("EMPTY_TREE","序列中没有任何结点符号（只有 #）：无法建立二叉树","sequence");return makeTrace(req,"由扩展先序序列建立二叉树",seq,`结点数=${built.filter(x=>x!==null).length}`,steps);}
  const arr=treeArray(req.initial_state.data);let sid=1;const steps=[makeStep(sid++,"init","二叉树","观察当前树结构。",view("tree",treeRows(arr,{operation:op})))];if(op==="leaf_output"||op==="leaf_count"){let count=0;for(const idx of preorder(arr)){const[l,r]=child(arr,idx),leaf=l<0&&r<0;if(leaf)count++;steps.push(makeStep(sid++,"visit",leaf?"发现叶子结点":"访问非叶结点",leaf?`${arr[idx]} 没有左右孩子，是第 ${count} 个叶子。`:`${arr[idx]} 还有孩子，继续遍历。`,view("tree",treeRows(arr,{operation:op,current:idx,leaf,count})),[action("visit","遍历并判断叶子",{value:arr[idx]})]));}return makeTrace(req,op==="leaf_output"?"先序遍历输出叶子结点":"统计二叉树叶子结点数","二叉树",`leafCount=${count}`,steps);}
  if(op==="height_postorder"||op==="height_preorder"){const h=treeHeight(arr);const order=op==="height_postorder"?[...preorder(arr)].reverse():preorder(arr);let maxDepth=0;for(const idx of order){const depth=Math.floor(Math.log2(idx+1))+1;maxDepth=Math.max(maxDepth,depth);steps.push(makeStep(sid++,"measure",op==="height_postorder"?"由子树高度向上合并":"按当前层次更新最大深度",op==="height_postorder"?`处理结点 ${arr[idx]}，高度由左右子树高度决定。`:`访问 ${arr[idx]}，当前层次 ${depth}。`,view("tree",treeRows(arr,{operation:op,current:idx,depth,maxDepth})),[action("compute","更新高度",{value:op==="height_postorder"?treeHeight(arr,idx):maxDepth})]));}return makeTrace(req,op==="height_postorder"?"后序遍历求二叉树高度":"先序遍历求二叉树高度","二叉树",`height=${h}`,steps);}
  if(op==="thread_first"||op==="thread_traverse"){const order=inorder(arr);if(op==="thread_first"){const first=order[0];steps.push(makeStep(sid,"locate","沿左孩子找到中序第一个结点",`第一个结点为 ${arr[first]}。`,view("tree",treeRows(arr,{operation:op,current:first})),[action("move","沿左孩子/线索定位",{to:first})]));return makeTrace(req,"中序线索树求第一个结点","线索树",String(arr[first]),steps);}const visited=[];for(const idx of order){visited.push(arr[idx]);steps.push(makeStep(sid++,"thread","沿后继线索遍历",`访问 ${arr[idx]}，再按孩子/后继线索寻找下一结点。`,view("tree",treeRows(arr,{operation:op,current:idx,visited:[...visited]})),[action("move","沿中序后继移动",{value:arr[idx]})]));}return makeTrace(req,"遍历中序线索二叉树","线索树",visited.join(" "),steps);}
  if(op==="path_to_node"){const target=String(req.params.target??arr[arr.length-1]);const idx=arr.findIndex(x=>String(x)===target);if(idx<0)return api.makeRuntimeError(req,"根到指定结点路径","TARGET_NOT_FOUND",`目标 ${target} 不在这棵二叉树中（结点：${arr.filter(x=>x!==null&&x!==undefined).join("、")}），请检查 target 参数。`,view("tree",treeRows(arr,{operation:op,target})),`target=${target}`);const path=[];let p=idx;while(p>=0){path.unshift(p);if(p===0)break;p=Math.floor((p-1)/2);}for(let i=0;i<path.length;i++)steps.push(makeStep(sid++,"path","沿根到目标路径前进",`到达 ${arr[path[i]]}。`,view("tree",treeRows(arr,{operation:op,current:path[i],path:path.slice(0,i+1)})),[action("move","路径前进",{to:path[i]})]));return makeTrace(req,"根到指定结点路径",`target=${target}`,path.map(i=>arr[i]).join("→"),steps);}
  const other=treeArray(req.params.other);const max=Math.max(arr.length,other.length);let same=true;for(let i=0;i<max;i++){const a=arr[i]??null,b=other[i]??null;steps.push(makeStep(sid++,"compare","比较对应位置",`${String(a)} ${a===b?"=":"≠"} ${String(b)}`,view("tree_compare",[row("left",arr),row("right",other),row("meta",[],{operation:op,index:i,equal:a===b})]),[action("compare","比较对应结点",{target:i})]));if((a===null)!==(b===null)){same=false;break;}}return makeTrace(req,"判断两棵二叉树结构相似","两棵树",same?"结构相似":"结构不同",steps);}

function simulateForest(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const groups=Array.isArray(req.params.trees)?req.params.trees:[["A","B","C"],["D","E"],["F"]];if(!groups.length)throw new SimulationInputError("EMPTY_FOREST","森林至少需要一棵树（trees 为空）","trees");if(groups.length>8)throw new SimulationInputError("INPUT_TOO_LARGE",`森林动画最多演示 8 棵树，当前 ${groups.length} 棵`,"trees");groups.forEach((g,i)=>{if(!Array.isArray(g)||!g.length)throw new SimulationInputError("EMPTY_TREE",`第 ${i+1} 棵树为空：每棵树至少要有一个结点`,`trees[${i}]`);if(g.length>10)throw new SimulationInputError("INPUT_TOO_LARGE",`第 ${i+1} 棵树有 ${g.length} 个结点，超过单棵 10 个的上限`,`trees[${i}]`);});let sid=1;const steps=[makeStep(sid++,"init",op==="to_binary_tree"?"森林":"二叉树表示","按孩子-兄弟对应关系转换。",view("forest",[row("trees",groups),row("meta",[],{operation:op})]))];const labels=groups.flat();steps.push(makeStep(sid++,"link",op==="to_binary_tree"?"第一孩子作为左孩子，下一兄弟作为右孩子":"左链还原孩子，右链分隔兄弟",`建立孩子-兄弟链接关系：${labels.join("、")}。`,view("forest",[row("trees",groups),row("binaryRepresentation",labels),row("meta",[],{operation:op,converted:true})]),[action("link","按孩子兄弟规则转换",{value:labels.length})]));return makeTrace(req,op==="to_binary_tree"?"森林转换为二叉树":"二叉树还原为森林","孩子兄弟关系","转换完成",steps);}

function simulateUnionFindAux(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const n=int(req.params.size,6,2,30,"size");if(op==="initialize"){const parent=Array(n).fill(-1);return makeTrace(req,"并查集初始化",`n=${n}`,`parent=[${parent.join(",")}]`,[makeStep(1,"init","每个元素自成一个集合","parent[i]=-1 表示每个结点都是独立根。",view("union_find",[row("parent",parent),row("meta",[],{operation:op})]))]);}const rawParent=req.params.parent!==undefined?req.params.parent:req.initial_state.data;const provided=normalizeParentArray(rawParent);const parent=provided??[-6,0,0,2,3,4];const size=parent.length;const x=int(req.params.element,Math.min(5,size-1),0,size-1,"element");let cur=x,sid=1;const path=[];const steps=[makeStep(sid++,"init","并查集父指针",`parent=[${parent.join(",")}]（负值 = 根，其绝对值为集合大小）。先沿 parent 找根，再压缩路径。`,view("union_find",[row("parent",parent),row("meta",[],{operation:op,element:x})]))];while(parent[cur]>=0){path.push(cur);steps.push(makeStep(sid++,"find","沿父指针向根移动",`${cur} → ${parent[cur]}`,view("union_find",[row("parent",parent),row("meta",[],{current:cur,path:[...path]})]),[action("move","沿 parent 向根移动",{from:cur,to:parent[cur]})]));cur=parent[cur];}for(const p of path){parent[p]=cur;steps.push(makeStep(sid++,"compress","路径压缩",`parent[${p}]=${cur}`,view("union_find",[row("parent",parent),row("meta",[],{root:cur,compressed:p})]),[action("link","直接连接根",{from:p,to:cur})]));}return makeTrace(req,"并查集路径压缩查找",`element=${x}`,`root=${cur}`,steps);}

function graphData(req,defaults={}){return normalizeGraphSpec(req,defaults);}
function graphRows(g,extra={}){return[row("graph",[],{nodes:g.nodes.map((x,i)=>({id:x,label:x,index:i})),edges:g.edges}),row("meta",[],{directed:g.directed,...extra})];}
function simulateGraphAux(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  // compute_indegree：入度是有向图概念，缺省按有向图处理，显式 directed=false 直接报错；
  // 建图/遍历操作缺省按无向图；连通分量无论 directed 均按无向边处理。
  const g=graphData(req,{directed:op==="compute_indegree"});
  if(op==="build_cross_list"&&req.params.directed===false)throw new SimulationInputError("INVALID_PARAM","十字链表用于存储有向图：directed 不能为 false","directed");
  if(op==="compute_indegree"&&!g.directed)return api.makeRuntimeError(req,"计算各顶点入度","UNDIRECTED_INDEGREE","入度是针对有向图的概念：请设置 directed=true，或改用求连通分量等无向图算法。",view("graph",graphRows(g,{operation:op})),"无向图");
  let sid=1;const steps=[makeStep(sid++,"init","图结构",`${g.nodes.length} 个顶点，${g.edges.length} 条边；${g.directed?"有向图":"无向图"}。`,view("graph",graphRows(g,{operation:op})))];
  if(op.startsWith("build_")){
    if(!g.edges.length)throw new SimulationInputError("EMPTY_EDGES","建图演示至少需要一条边（edges 为空）","edges");
    const built=[];
    for(const e of g.edges){built.push(e);const title=op==="build_adjacency_matrix"?"写入邻接矩阵":"建立边结点并挂接指针";
      steps.push(makeStep(sid++,title==="写入邻接矩阵"?"build":"build",title,op==="build_adjacency_matrix"?`设置 ${e[0]}→${e[1]} 的矩阵单元${g.directed?"":"（无向图同时设置对称单元）"}。`:`加入边 ${e[0]}→${e[1]}${op==="build_cross_list"?"，同时接入出边链和入边链":g.directed?" 到起点邻接链":"（无向图同时挂到终点邻接链）"}。`,view("graph",[row("graph",[],{nodes:g.nodes.map(x=>({id:x,label:x})),edges:[...built],directed:g.directed}),row("meta",[],{operation:op,current:e,directed:g.directed})]),[action("link","写入图存储结构",{from:e[0],to:e[1],value:e[2]})]));}
    return makeTrace(req,{build_adjacency_matrix:"建立图的邻接矩阵",build_adjacency_list:"建立图的邻接表",build_cross_list:"建立有向图十字链表"}[op],"边集合","建立完成",steps);}
  const adjForDfs=new Map(g.nodes.map(n=>[n,[]]));for(const [u,v] of g.edges){adjForDfs.get(u)?.push(v);if(!g.directed)adjForDfs.get(v)?.push(u);}
  if(op==="compute_indegree"){const indegree=Object.fromEntries(g.nodes.map(n=>[n,0]));for(const [u,v] of g.edges){if(v in indegree)indegree[v]++;steps.push(makeStep(sid++,"count","统计入度",`处理边 ${u}→${v}，indegree[${v}]=${indegree[v]}。`,view("graph",[...graphRows(g,{operation:op,current:[u,v]}),row("indegree",Object.entries(indegree).map(([vertex,value])=>({vertex,value})))]),[action("count","终点入度加 1",{target:v,value:indegree[v]})]));}return makeTrace(req,"计算各顶点入度","有向图",Object.entries(indegree).map(([v,d])=>`${v}:${d}`).join(", "),steps);}
  if(op==="dfs_nonrecursive"){const start=String(req.params.start??g.nodes[0]);requireVertex(g,start,"起点 start");const stack=[start],seen=new Set(),order=[];while(stack.length){const u=stack.pop();if(seen.has(u))continue;seen.add(u);order.push(u);steps.push(makeStep(sid++,"visit","显式栈退栈访问",`访问 ${u}。`,view("graph",[...graphRows(g,{operation:op,current:u,visited:[...order]}),row("stack",stack)]),[action("visit","DFS 访问顶点",{value:u})]));const ns=[...(adjForDfs.get(u)||[])].reverse();for(const v of ns)if(!seen.has(v))stack.push(v);}return makeTrace(req,"非递归深度优先搜索",`start=${start}`,order.join("→"),steps);}
  // connected_components：按无向边求连通分量（教材定义），不受 directed 影响。
  const adjUndirected=new Map(g.nodes.map(n=>[n,[]]));for(const [u,v] of g.edges){adjUndirected.get(u)?.push(v);adjUndirected.get(v)?.push(u);}
  const seen=new Set();let component=0;for(const start of g.nodes){if(seen.has(start))continue;component++;const q=[start],members=[];while(q.length){const u=q.shift();if(seen.has(u))continue;seen.add(u);members.push(u);for(const v of adjUndirected.get(u)||[])if(!seen.has(v))q.push(v);}steps.push(makeStep(sid++,"component","找到一个连通分量",`第 ${component} 个分量：${members.join("、")}`,view("graph",graphRows(g,{operation:op,component,members,visited:[...seen]})),[action("mark","标记连通分量",{value:component})]));}return makeTrace(req,"求图的连通分量","图",`components=${component}`,steps);}

function bstInsert(root,key){if(!root)return {key,left:null,right:null};if(key<root.key)root.left=bstInsert(root.left,key);else if(key>root.key)root.right=bstInsert(root.right,key);return root;}
function bstRows(root,extra={}){const nodes=[],edges=[];let id=0;function walk(n,parent=null,side=""){if(!n)return;const my=id++;nodes.push({id:my,label:String(n.key),key:n.key});if(parent!==null)edges.push([parent,my,side]);walk(n.left,my,"L");walk(n.right,my,"R");}walk(root);return[row("tree",[],{nodes,edges}),row("meta",[],extra)];}
function simulateBSTAux(req,api){const {makeStep,makeTrace,action}=helpers(api);const op=req.operation;const vals=numArray(req.params.values??req.initial_state.data,[45,24,53,12,28,90]);if(req.params.key!==undefined&&!Number.isFinite(Number(req.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(req.params.key)} 必须是数字`,"key");let root=null,sid=1;for(const v of vals)root=bstInsert(root,v);if(op==="create"){if(!vals.length)throw new SimulationInputError("EMPTY_INPUT","关键字列表为空：无法创建二叉排序树","values");root=null;const steps=[makeStep(sid++,"init","空二叉排序树","依次插入输入关键字。",view("tree",bstRows(root,{operation:op})))];for(const v of vals){root=bstInsert(root,v);steps.push(makeStep(sid++,"insert","按大小关系插入",`${v} 沿比较路径插入空孩子位置。`,view("tree",bstRows(root,{operation:op,current:v})),[action("insert","BST 插入",{value:v})]));}return makeTrace(req,"创建二叉排序树",`keys=${vals.join(",")}`,"创建完成",steps);}const key=Number(req.params.key??28),steps=[makeStep(sid++,"init","二叉排序树","从根开始利用有序性查找。",view("tree",bstRows(root,{operation:op,key})))];let cur=root,depth=0;while(cur){depth++;steps.push(makeStep(sid++,op==="search_recursive"?"call":"compare",op==="search_recursive"?"递归进入当前子树":"循环比较当前结点",`比较 ${key} 与 ${cur.key}。`,view("tree",bstRows(root,{operation:op,key,current:cur.key,depth})),[action("compare","比较关键字",{value:key,target:cur.key})]));if(key===cur.key)return makeTrace(req,op==="search_recursive"?"递归查找二叉排序树":"非递归查找二叉排序树",`key=${key}`,`found=${key}`,steps);cur=key<cur.key?cur.left:cur.right;}steps.push(makeStep(sid,"miss","到达空子树","当前指针为空，查找失败。",view("tree",bstRows(root,{operation:op,key,current:null,depth}))));return makeTrace(req,op==="search_recursive"?"递归查找二叉排序树":"非递归查找二叉排序树",`key=${key}`,"not found",steps);}

function simulateAVLRotation(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const presets={rotate_ll:[30,20,10],rotate_rr:[10,20,30],rotate_lr:[30,10,20],rotate_rl:[10,30,20]};/* 旋转演示的 required 为空，resolver 不回填 demo 参数：空 data 视为未提供，回落到教材标准失衡序列 */const before=Array.isArray(req.initial_state.data)&&req.initial_state.data.length?numArray(req.initial_state.data):presets[op].slice();if(before.length<3)throw new SimulationInputError("INVALID_ROTATION_INPUT",`AVL 旋转演示需要 3 个关键字的失衡局部（如 ${JSON.stringify(presets[op])}），当前只有 ${before.length} 个`,"initialData");const root=before[before.length-1];const after=[...before].sort((a,b)=>a-b);const title={rotate_ll:"LL 型右旋",rotate_rr:"RR 型左旋",rotate_lr:"LR 型先左后右旋",rotate_rl:"RL 型先右后左旋"}[op];const steps=[makeStep(1,"init","失衡局部",`插入序列 ${before.join("→")} 造成 ${title.split(" 型")[0]} 型失衡。`,view("tree",[row("sequence",before),row("meta",[],{operation:op})])),makeStep(2,"rotate",title,`围绕失衡结点执行 ${title}，中间关键字 ${after[1]} 成为局部根。`,view("tree",[row("sequence",after),row("meta",[],{operation:op,root:after[1]})]),[action("rotate","恢复平衡",{value:title})])];return makeTrace(req,`AVL ${title}`,before.join(","),`局部根=${after[1]}`,steps);}

function simulateBTreeAux(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const keys=numArray(req.initial_state.data,[10,20,30,40]);if(!keys.length)throw new SimulationInputError("EMPTY_TREE","B 树结点为空：请在 initialData 中提供结点关键字","initialData");if(req.params.key!==undefined&&!Number.isFinite(Number(req.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(req.params.key)} 必须是数字`,"key");const key=Number(req.params.key??25),order=int(req.params.order,4,3,8,"order");let sid=1;const steps=[makeStep(sid++,"init","B 树结点关键字",`当前结点：[${keys.join(", ")}]，m=${order}。`,view("btree",[row("node",keys),row("meta",[],{operation:op,key,order})]))];if(op==="locate_position"){let pos=0;while(pos<keys.length&&keys[pos]<=key){steps.push(makeStep(sid++,"compare","寻找不大于 k 的最大序号",`比较 key[${pos}]=${keys[pos]} 与 k=${key}。`,view("btree",[row("node",keys),row("meta",[],{operation:op,key,pos})]),[action("compare","结点内比较",{target:pos,value:key})]));pos++;}return makeTrace(req,"B 树结点内定位关键字序号",`k=${key}`,`ipos=${Math.max(0,pos-1)}`,steps);}if(op==="node_insert"){const out=[...keys,key].sort((a,b)=>a-b);steps.push(makeStep(sid,"insert","在结点内插入关键字","右侧关键字和孩子指针先后移，再写入新关键字。",view("btree",[row("node",out),row("meta",[],{operation:op,key})]),[action("insert","结点内插入关键字",{value:key})]));return makeTrace(req,"B 树结点内插入",`[${keys.join(",")}]`,`[${out.join(",")}]`,steps);}const full=[...keys,key].sort((a,b)=>a-b),mid=Math.floor(full.length/2),promote=full[mid],left=full.slice(0,mid),right=full.slice(mid+1);steps.push(makeStep(sid,"split","分裂满结点",`中间关键字 ${promote} 上移，左右分裂为 [${left}] 和 [${right}]。`,view("btree",[row("left",left),row("promote",[promote]),row("right",right),row("meta",[],{operation:op,order})]),[action("split","分裂并提升中间关键字",{value:promote})]));return makeTrace(req,"B 树结点分裂",`[${full.join(",")}]`,`promote=${promote}`,steps);}


function simulateSentinelSearch(req,api){const {makeStep,makeTrace,action}=helpers(api);const data=numArray(req.initial_state.data,[12,25,37,48,59]),key=Number(req.params.key??37);const r=[key,...data],steps=[makeStep(1,"init","设置监视哨","把待查关键字写入 r[0]，从表尾向前查找。",view("search",[row("R",r),row("meta",[],{operation:req.operation,key,index:data.length})]),[action("assign","设置 r[0]=key",{target:0,value:key})])];let i=data.length,sid=2;while(r[i]!==key){steps.push(makeStep(sid++,"compare","从后向前比较",`r[${i}]=${r[i]} ≠ ${key}，i--。`,view("search",[row("R",r),row("meta",[],{operation:req.operation,key,index:i})]),[action("compare","比较关键字",{target:i,value:key})]));i--;}steps.push(makeStep(sid,"found",i===0?"落到监视哨":"找到记录",i===0?"只匹配到 r[0]，原表中不存在该关键字。":`在第 ${i} 个位置找到 ${key}。`,view("search",[row("R",r),row("meta",[],{operation:req.operation,key,index:i,found:i>0})]),[action("mark",i===0?"查找失败":"查找成功",{target:i})]));return makeTrace(req,"带监视哨的顺序查找",`key=${key}`,i===0?"not found":`position=${i}`,steps);}

function decimalKeyString(value,fallback="12345678"){if(value===undefined||value===null||value==="")return fallback;const raw=String(value).replace(/\D/g,"");if(!raw)throw new SimulationInputError("INVALID_KEY",`关键字 ${JSON.stringify(value)} 中不含数字：哈希函数演示需要数字关键字`,"key");return raw;}
function digitsOf(value,width=0){const raw=decimalKeyString(value).padStart(width,"0");return raw.split("").map(Number);}
function simulateHashFunction(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  const keyText=decimalKeyString(req.params.key,"12345678");
  let sid=1,steps=[];
  const st=(rows,extra={})=>view("hash_function",[...rows,row("meta",[],{operation:op,key:keyText,...extra})]);
  if(op==="digit_analysis"){
    const ds=digitsOf(keyText),positions=Array.isArray(req.params.positions)&&req.params.positions.length?req.params.positions.map(Number):[4,7];
    steps.push(makeStep(sid++,"init","拆分关键字各位数字",`${keyText} → ${ds.join(" ")}`,st([row("digits",ds)])));
    const picked=positions.map(p=>ds[Math.max(0,Math.min(ds.length-1,p-1))]);
    steps.push(makeStep(sid,"select","选取分布较均匀的若干位",`取第 ${positions.join("、")} 位，得到地址 ${picked.join("")}。`,st([row("digits",ds),row("selected",picked)],{positions}),[action("select","选取关键字段",{value:picked})]));
    return makeTrace(req,"数字分析法构造哈希地址",`key=${keyText}`,`address=${picked.join("")}`,steps);
  }
  if(op==="mid_square"){
    const square=(BigInt(keyText)*BigInt(keyText)).toString(),width=int(req.params.width,3,1,6,"width"),start=Math.max(0,Math.floor((square.length-width)/2)),mid=square.slice(start,start+width);
    steps=[makeStep(sid++,"compute","关键字平方",`${keyText}² = ${square}`,st([row("square_digits",square.split("").map(Number))])),makeStep(sid,"select","取平方值中间若干位",`取中间 ${width} 位得到 ${mid}。`,st([row("square_digits",square.split("").map(Number)),row("selected",mid.split("").map(Number))],{width,start:start+1}),[action("select","取中间位",{value:mid})])];
    return makeTrace(req,"平方取中法构造哈希地址",`key=${keyText}`,`address=${mid}`,steps);
  }
  if(op==="division_remainder"){
    const p=int(req.params.modulus,13,2,997,"modulus"),addr=Number(BigInt(keyText)%BigInt(p));
    steps=[makeStep(1,"compute","除留余数",`${keyText} mod ${p} = ${addr}`,st([row("formula",[keyText,p,addr])],{modulus:p,address:addr}),[action("compute","计算余数",{value:addr})])];
    return makeTrace(req,"除留余数法构造哈希地址",`key=${keyText}, p=${p}`,`address=${addr}`,steps);
  }
  if(op==="pseudo_random"){
    const randomValue=Number(req.params.randomValue);
    if(!Number.isInteger(randomValue)||randomValue<0)throw new SimulationInputError("INVALID_PARAM",`参数 randomValue=${JSON.stringify(req.params.randomValue)} 必须是非负整数（即给定的 random(key) 结果）`,"randomValue");
    steps=[makeStep(1,"map","调用给定伪随机函数","教材只规定 H(key)=random(key)；本演示使用输入中给定的 random(key) 结果，不擅自规定随机函数公式。",st([row("mapping",[{key:keyText,randomValue}])],{address:randomValue}),[action("compute","读取给定 random(key) 映射",{value:randomValue})])];
    return makeTrace(req,"伪随机数法构造哈希地址",`key=${keyText}`,`address=${String(randomValue)}`,steps);
  }
  const blockSize=int(req.params.blockSize,3,1,6,"blockSize"),discard=int(req.params.discardTailDigits,0,0,Math.max(0,keyText.length-1),"discardTailDigits");
  const usable=discard?keyText.slice(0,-discard):keyText,blockTexts=[];
  for(let i=0;i<usable.length;i+=blockSize)blockTexts.push(usable.slice(i,i+blockSize));
  const blocks=blockTexts.map(Number);
  steps.push(makeStep(sid++,"split","按哈希地址位数把关键字分段",`${keyText}${discard?`（舍去最低 ${discard} 位后为 ${usable}）`:""} → ${blockTexts.join(" | ")}`,st([row("blocks",blockTexts)],{blockSize,discardTailDigits:discard})));
  let normalizedTexts=[...blockTexts];
  if(op==="folding_boundary")normalizedTexts=blockTexts.map((v,i)=>i%2===1?v.split("").reverse().join(""):v);
  const normalized=normalizedTexts.map(Number);let sum=0;
  for(let i=0;i<normalized.length;i++){sum+=normalized[i];steps.push(makeStep(sid++,"accumulate",op==="folding_boundary"?"折叠后叠加":"低位对齐移位叠加",`累计第 ${i+1} 段 ${normalizedTexts[i]}，sum=${sum}。`,st([row("blocks",blockTexts),row("aligned",normalizedTexts),row("sum",[sum])],{index:i}),[action("compute","分段叠加",{value:sum})]));}
  const modulus=int(req.params.modulus,10**Math.min(4,blockSize),2,10000,"modulus"),addr=sum%modulus;
  steps.push(makeStep(sid,"result","舍弃最高进位",`${sum} mod ${modulus} = ${addr}`,st([row("blocks",blockTexts),row("aligned",normalizedTexts),row("address",[addr])],{address:addr}),[action("compute","保留地址位数",{value:addr})]));
  return makeTrace(req,op==="folding_boundary"?"折叠叠加法构造哈希地址":"移位叠加法构造哈希地址",`key=${keyText}`,`address=${addr}`,steps);
}

function simulateLinkedRadix(req,api){const {makeStep,makeTrace,action}=helpers(api);let a=numArray(req.initial_state.data,[329,457,657,839,436,720,355]),sid=1;const maxDigits=Math.max(...a.map(x=>String(Math.abs(Math.trunc(x))).length));const steps=[makeStep(sid++,"init","静态链表保存待排记录","使用 next 指针把记录组织成当前链表顺序。",view("sort",[row("records",a),row("next",a.map((_,i)=>i+1<a.length?i+1:-1)),row("meta",[],{operation:req.operation})]))];let exp=1;for(let pass=1;pass<=maxDigits;pass++,exp*=10){const buckets=Array.from({length:10},()=>[]);for(const v of a){const d=Math.floor(Math.abs(v)/exp)%10;buckets[d].push(v);steps.push(makeStep(sid++,"distribute","按当前位分配到链式队列",`${v} 的第 ${pass} 位是 ${d}，接到 ${d} 号桶尾。`,view("sort",[row("records",a),row("buckets",buckets.map((b,i)=>({bucket:i,values:b}))),row("meta",[],{pass,current:v,digit:d})]),[action("link","记录接入桶尾",{target:d,value:v})]));}a=buckets.flat();steps.push(makeStep(sid++,"collect","按桶号重新链接记录","依次连接 0～9 号桶的首尾指针，得到新的静态链表顺序。",view("sort",[row("records",a),row("next",a.map((_,i)=>i+1<a.length?i+1:-1)),row("meta",[],{pass})]),[action("link","按桶序收集",{value:a})]));}return makeTrace(req,"链式基数排序",`[${numArray(req.initial_state.data,[329,457,657,839,436,720,355]).join(",")}]`,`[${a.join(",")}]`,steps);}

function simulateDiskBufferMerge(req,api){const {makeStep,makeTrace,action}=helpers(api);const raw=Array.isArray(req.params.runs)?req.params.runs:req.initial_state.data;const runs=(Array.isArray(raw)?raw:[[1,5,9],[2,6,8]]).map(r=>numArray(r,[])).filter(r=>r.length);const left=runs[0]||[1,5,9],right=runs[1]||[2,6,8],pageSize=int(req.params.pageSize,2,1,8),out=[];let i=0,j=0,sid=1;const steps=[makeStep(sid++,"load","装入两个输入缓冲区","分别从两个有序归并段读入一页记录。",view("external_sort",[row("inputA",left.slice(0,pageSize)),row("inputB",right.slice(0,pageSize)),row("outputBuffer",[]),row("meta",[],{operation:req.operation,pageSize,i,j})]),[action("read","磁盘块读入输入缓冲",{value:pageSize})])];while(i<left.length||j<right.length){const takeLeft=j>=right.length||(i<left.length&&left[i]<=right[j]);const v=takeLeft?left[i++]:right[j++];out.push(v);const outputPage=out.slice(Math.max(0,out.length-pageSize));steps.push(makeStep(sid++,"merge","比较输入缓冲当前记录",`把 ${v} 写入输出缓冲区。`,view("external_sort",[row("inputA",left.slice(i,i+pageSize)),row("inputB",right.slice(j,j+pageSize)),row("outputBuffer",outputPage),row("written",out.slice(0,Math.max(0,out.length-pageSize))),row("meta",[],{operation:req.operation,pageSize,i,j})]),[action("write","写入输出缓冲",{value:v})]));if(out.length%pageSize===0)steps.push(makeStep(sid++,"flush","输出缓冲区写满","把当前输出页写回外存，并继续归并。",view("external_sort",[row("written",out),row("outputBuffer",[]),row("meta",[],{operation:req.operation,pageSize,i,j})]),[action("write","输出页写回磁盘",{value:out.slice(-pageSize)})]));}return makeTrace(req,"外部排序输入/输出缓冲二路归并","两个有序归并段",`[${out.join(",")}]`,steps);}

function simulateSortAux(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const a=numArray(req.initial_state.data,[49,38,65,97,76,13,27]);if(op==="dutch_flag"&&a.some(x=>![0,1,2].includes(x)))throw new SimulationInputError("INVALID_ELEMENT","荷兰国旗三色分类只接受 0/1/2 作为元素","initialData");if(!a.length)throw new SimulationInputError("EMPTY_INPUT","待处理序列为空：请提供至少一个元素","initialData");let sid=1;const st=(extra={})=>view("sort",[row("array",a),row("meta",[],{operation:op,...extra})]);const steps=[makeStep(sid++,"init","待处理序列",`[${a.join(", ")}]`,st())];if(op==="linked_radix")return simulateLinkedRadix(req,api);if(op==="quick_partition"){let i=0,j=a.length-1,pivot=a[0];while(i<j){while(i<j&&a[j]>=pivot)j--;if(i<j){a[i]=a[j];steps.push(makeStep(sid++,"move","右侧小记录填左坑",`${a[i]} 移到位置 ${i}。`,st({i,j,pivot}),[action("move","记录搬移",{from:j,to:i})]));i++;}while(i<j&&a[i]<=pivot)i++;if(i<j){a[j]=a[i];steps.push(makeStep(sid++,"move","左侧大记录填右坑",`${a[j]} 移到位置 ${j}。`,st({i,j,pivot}),[action("move","记录搬移",{from:i,to:j})]));j--;}}a[i]=pivot;steps.push(makeStep(sid,"pivot","枢轴归位",`${pivot} 放入位置 ${i}。`,st({pivotIndex:i,pivot}),[action("insert","枢轴归位",{target:i,value:pivot})]));return makeTrace(req,"一趟快速排序划分","待划分区间",`pivotIndex=${i}`,steps);}function down(n,i){const start=i,temp=a[i];while(2*i+1<n){let child=2*i+1;if(child+1<n&&a[child+1]>a[child])child++;if(a[child]<=temp)break;a[i]=a[child];steps.push(makeStep(sid++,"adjust","较大孩子上移",`${a[i]} 上移到位置 ${i}。`,st({heapSize:n,current:i,child}),[action("move","孩子上移",{from:child,to:i})]));i=child;}a[i]=temp;steps.push(makeStep(sid++,"adjust","待调整记录落位",`${temp} 放到位置 ${i}。`,st({heapSize:n,current:i}),[action("insert","待调整记录落位",{target:i,value:temp})]));}
  if(op==="heap_adjust"){down(a.length,int(req.params.root,0,0,a.length-1,"root"));return makeTrace(req,"重建堆过程","局部堆",`[${a.join(",")}]`,steps);}if(op==="heap_build"){for(let i=Math.floor(a.length/2)-1;i>=0;i--)down(a.length,i);return makeTrace(req,"建立初始堆","任意序列",`heap=[${a.join(",")}]`,steps);}if(op==="merge_two"){const mid=int(req.params.mid,Math.floor(a.length/2),1,a.length-1,"mid"),left=a.slice(0,mid).sort((x,y)=>x-y),right=a.slice(mid).sort((x,y)=>x-y),out=[];let i=0,j=0;while(i<left.length||j<right.length){if(j>=right.length||(i<left.length&&left[i]<=right[j]))out.push(left[i++]);else out.push(right[j++]);steps.push(makeStep(sid++,"merge","比较两个有序段当前记录",`输出 ${out[out.length-1]}。`,view("sort",[row("left",left),row("right",right),row("output",out),row("meta",[],{i,j,operation:op})]),[action("merge","较小记录写入结果",{value:out[out.length-1]})]));}return makeTrace(req,"相邻两个有序子序列合并","两个有序段",`[${out.join(",")}]`,steps);}let low=0,mid=0,high=a.length-1;while(mid<=high){if(a[mid]===0){[a[low],a[mid]]=[a[mid],a[low]];steps.push(makeStep(sid++,"swap","0 放到左区",`交换 low=${low}, mid=${mid}。`,st({low,mid,high}),[action("swap","交换记录",{from:mid,to:low})]));low++;mid++;}else if(a[mid]===1){mid++;}else{[a[mid],a[high]]=[a[high],a[mid]];steps.push(makeStep(sid++,"swap","2 放到右区",`交换 mid=${mid}, high=${high}。`,st({low,mid,high}),[action("swap","交换记录",{from:mid,to:high})]));high--;}}return makeTrace(req,"荷兰国旗三色分类","0/1/2 序列",`[${a.join(",")}]`,steps);}

function simulateAuxiliaryOperation(req,api){
  if(req.structure==="linked_list")return simulateLinkedListAux(req,api);
  if(req.structure==="circular_linked_list")return simulateCircularList(req,api);
  if(req.structure==="static_linked_list")return simulateStaticList(req,api);
  if(req.structure==="polynomial")return simulatePolynomialBuild(req,api);
  if(req.structure==="stack")return simulateStackAux(req,api);
  if(req.structure==="double_stack")return simulateDoubleStack(req,api);
  if(req.structure==="linked_stack")return simulateLinkedStack(req,api);
  if(req.structure==="recursion")return simulateRecursionAux(req,api);
  if(req.structure==="linked_queue")return simulateLinkedQueue(req,api);
  if(req.structure==="circular_queue")return simulateCircularQueueInit(req,api);
  if(req.structure==="queue_app")return simulateYanghui(req,api);
  if(req.structure==="circular_buffer")return simulateCircularBuffer(req,api);
  if(req.structure==="string")return simulateStringCompare(req,api);
  if(req.structure==="heap_string")return simulateHeapString(req,api);
  if(req.structure==="special_matrix")return simulateSpecialMatrix(req,api);
  if(req.structure==="generalized_list")return simulateGeneralizedHead(req,api);
  if(req.structure==="tree")return simulateTreeAux(req,api);
  if(req.structure==="forest")return simulateForest(req,api);
  if(req.structure==="union_find")return simulateUnionFindAux(req,api);
  if(req.structure==="graph")return simulateGraphAux(req,api);
  if(req.structure==="search")return simulateSentinelSearch(req,api);
  if(req.structure==="bst")return simulateBSTAux(req,api);
  if(req.structure==="avl")return simulateAVLRotation(req,api);
  if(req.structure==="btree")return simulateBTreeAux(req,api);
  if(req.structure==="hash_function")return simulateHashFunction(req,api);
  if(req.structure==="sort")return simulateSortAux(req,api);
  if(req.structure==="external_sort")return simulateDiskBufferMerge(req,api);
  throw new Error(`未实现教材辅助动画 ${req.structure}/${req.operation}`);
}

module.exports={AUX_SUPPORTED_PAIRS,simulateAuxiliaryOperation};
