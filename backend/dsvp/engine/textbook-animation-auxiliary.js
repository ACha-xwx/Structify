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
  /* 代码级：一帧 = 一行赋值，帧的标题就是那行代码；槽位里写**目标结点的序号**（`next: [2,3,null]`，
     写值遇到重复数据就分不清）；逐行帧标 `phase: "line"`（精简版把连续 line 压成最后一帧）。
     这里用**带头结点**的模型（`head->next` 指向首数据结点）——头结点是第 1 个结点，
     所以数据结点的序号从 2 开始，和教材的图画法一致。 */
  const ring=(values)=>({labels:["head",...values.map(String)],next:["head",...values.map(String)].map((_,i)=>i+1<values.length+1?i+2:null)});
  const pan=(c,extra={})=>row("L",c.labels,{next:[...c.next],...extra});
  const plain=(values)=>({labels:values.map(String),next:values.map((_,i)=>i+1<values.length?i+2:null)});
  const steps=[];
  const snap=(code,rows,meta,act,line)=>steps.push(makeStep(sid++,line?"line":"assign",code,code,view("linked_list",[...rows,row("meta",[],meta)]),act?[act]:[]));

  if(op==="initialize"){
    snap("L = malloc()",[pan(ring([]),{pointers:{L:0}})],{operation:op},action("allocate","申请头结点",{value:"head"}),true);
    snap("L->next = NULL",[pan(ring([]),{pointers:{L:0},write:0})],{operation:op},action("link","头结点的 next 置空",{to:"NULL"}));
    return makeTrace(req,"初始化单链表","未初始化","空表 L",steps);
  }
  if(op==="build_head"||op==="build_tail"){
    const input=scalarArray(req.params.values??data,[1,2,3,4]);
    const c=ring([]);let rear=0;
    snap("L = malloc(); L->next = NULL",[pan(c,{pointers:{L:0}})],{operation:op},action("allocate","建立头结点",{}),true);
    for(const x of input){
      const idx=c.labels.length;
      c.labels.push("");c.next.push(null);
      snap("s = malloc()",[pan(c,{pointers:{L:0,s:idx}})],{operation:op,input:x},null,true);
      c.labels[idx]=String(x);
      snap(`s->data = ${x}`,[pan(c,{pointers:{L:0,s:idx}})],{operation:op,input:x},null,true);
      if(op==="build_head"){
        c.next[idx]=c.next[0];
        snap("s->next = L->next",[pan(c,{pointers:{L:0,s:idx},write:idx})],{operation:op,input:x},action("link","新结点先接住原来的首结点",{to:2}));
        c.next[0]=idx+1;
        snap("L->next = s",[pan(c,{pointers:{L:0,s:idx},write:0})],{operation:op,input:x},action("link","头结点指向新结点",{to:idx+1}));
      } else {
        if(idx===1){c.next[0]=2;rear=2;}
        else {c.next[rear-1]=idx+1;rear=idx+1;}
        snap(idx===1?"L->next = s":"rear->next = s",[pan(c,{pointers:{L:0,rear},write:idx===1?0:rear-1})],{operation:op,input:x},action("link",idx===1?"头结点接上新结点":"尾结点指向新结点",{to:idx+1}));
        snap("rear = s",[pan(c,{pointers:{L:0,rear:idx+1}})],{operation:op,input:x},null,true);
      }
    }
    const out=input.slice();
    return makeTrace(req,op==="build_head"?"头插法建立单链表":"尾插法建立单链表",`输入=${input.join(",")}`,`L=head→${out.join("→")}`,steps);
  }
  if(op==="search_position"){
    const position=int(req.params.position,2,1,Math.max(1,data.length));
    const c=plain(data);
    snap("p = L",[pan(c,{pointers:{p:0}})],{operation:op,position},null,true);
    for(let i=0;i<data.length&&i<position-1;i++){
      snap("p = p->next",[pan(c,{focusIndex:i+1,pointers:{p:i+1}})],{operation:op,position},null,true);
    }
    return makeTrace(req,"单链表按位置查找",`i=${position}`,position<=data.length?`找到 ${data[position-1]}`:"不存在",steps);
  }
  if(op==="search_value"){
    const key=req.params.key??data[Math.min(2,data.length-1)]; const c=plain(data);
    snap("p = L",[pan(c,{pointers:{p:0}})],{operation:op,key},null,true);
    let found=-1;
    for(let i=0;i<data.length;i++){
      const hit=data[i]===key;
      snap(`if (p->data == ${String(key)}) → ${hit?"true":"false"}`,[pan(c,{focusIndex:i,pointers:{p:i}})],{operation:op,key},action("compare","比较 data 与 key",{value:key}));
      if(hit){found=i;break;}
      if(i+1<data.length)snap("p = p->next",[pan(c,{focusIndex:i+1,pointers:{p:i+1}})],{operation:op,key},null,true);
    }
    return makeTrace(req,"单链表按值查找",`key=${String(key)}`,found>=0?`第 ${found+1} 个结点`:`未找到`,steps);
  }
  if(op==="length"){
    const c=plain(data);
    snap("n = 0; p = L",[pan(c,{pointers:{p:0}})],{operation:op,count:0},null,true);
    for(let i=0;i<data.length;i++){
      if(i>0)snap("p = p->next",[pan(c,{focusIndex:i,pointers:{p:i}})],{operation:op,count:i},null,true);
      snap("n = n + 1",[pan(c,{focusIndex:i,pointers:{p:i}})],{operation:op,count:i+1},action("count","结点计数加 1",{value:i+1}));
    }
    return makeTrace(req,"求单链表长度","从头结点后开始计数",`length=${data.length}`,steps);
  }
  /* 逆置（算法 2.8）：把每个结点的 next 依次反转过来，四个指针 r、p、q 一路往前推。
     每遍历一个结点报四帧：`q = p->next` → `p->next = r` → `r = p` → `p = q`——
     断和接的顺序在画面上就是"这根旧箭头消失、那根新箭头出现"。 */
  {
    const c=plain(data);
    snap("r = NULL; p = L",[pan(c,{pointers:{p:0}})],{operation:op},null,true);
    let p=0,r=null;
    while(p<data.length){
      const q=p+1<data.length?p+1:null;
      snap("q = p->next",[pan(c,{focusIndex:p,pointers:{p,r:r===null?undefined:r}})],{operation:op},null,true);
      const before=c.next[p];
      c.next[p]=r===null?null:r+1;
      snap("p->next = r",[pan(c,{focusIndex:p,pointers:{p,q:q===null?undefined:q},write:p})],{operation:op},action("link","next 改指前驱",{from:before,to:c.next[p]}),true);
      r=p;p=q===null?data.length:q;
      snap("r = p; p = q",[pan(c,{pointers:{r:r===null?undefined:r,p:p}})],{operation:op});
    }
    const out=[...data].reverse();
    snap("L = r",[pan(plain(out),{pointers:{L:0}})],{operation:op,done:true},action("link","头指针改指向原尾结点",{}));
    return makeTrace(req,"单链表逆置",`L=${data.join("→")}`,`L=${out.join("→")}`,steps);
  }
}

function simulateCircularList(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation; const raw=Array.isArray(req.initial_state.data)?req.initial_state.data:[];
  const left=scalarArray(req.params.left??raw[0],[1,3,5]),right=scalarArray(req.params.right??raw[1],[2,4,6]); let sid=1;

  /* 循环单链表**带头结点**（教材 2.4）。整条链的真相就是一个 `next` 数组：第 i 个结点的 next 指向第几个结点。
     第 0 个结点是头结点（标签 head），尾结点的 next 指回 0 —— "循环"这件事就落在这一张数组上。
     这个操作按**代码级别**报帧：**一帧 = 一行赋值**，帧的标题就是那行代码；面板里：
       · `next[i]` 写明第 i 个结点此刻 next 栏该显示什么（指向谁一目了然，不需要猜）；
       · `write` 标出这一帧正在改哪个结点的指针（渲染器把那一格点亮）；
       · `pointers` 是**本面板自己的**具名指针（p / q / r / A / B），不是全帧共用的——
         两个表各自有 p、q 时，共用一份会互相串（LB 的 q 会标到 LA 上）。
     这样"怎么断的、怎么连的"就是画面本身，不需要旁白。 */
  const chain=(values,headLabel="head")=>{
    const labels=[headLabel,...values.map(String)];
    /* `next` 里放的是**目标结点的序号**（第几个结点，从 1 数起），**不是它的值**——
       值会重复（[20,20] 两个 20 都写"20"就分不清指向谁），只有序号唯一。
       第 1 个结点是头结点；尾结点指回 1（循环）。跨表的用 `"B#2"` 这种带表面板前缀的写法。 */
    const next=labels.map((_,i)=>(i+1<labels.length?i+2:1));
    return {labels,next};
  };
  const pan=(role,c,extra={})=>row(role,c.labels,{next:[...c.next],...extra});

  if(op==="initialize"){
    const c=chain([]);
    return makeTrace(req,"初始化循环单链表","未初始化","head->next = head",[
      makeStep(sid++,"init","L = 空表","建立头结点之前，表是空的",view("circular_linked_list",[row("LA",[],{next:[]}),row("meta",[],{operation:op,circular:true})])),
      makeStep(sid++,"link","head->next = head","head->next = head",
        view("circular_linked_list",[pan("LA",c,{pointers:{head:0},write:0}),row("meta",[],{operation:op,circular:true})]),
        [action("link","head.next=head",{target:"head",to:"head"})])
    ]);
  }

  if(op==="build"){
    const vals=scalarArray(req.params.values??raw,[1,2,3,4]);
    const c=chain([]);let tail=0;
    const steps=[makeStep(sid++,"init","r = head","空循环链表，尾指针 r 先指向头结点",
      view("circular_linked_list",[pan("LA",c,{pointers:{head:0,r:tail}}),row("meta",[],{operation:op,circular:true,tail})]))];
    for(const x of vals){
      const idx=c.labels.length;
      c.labels.push(String(x));c.next.push(1);                       /* s = new; s->data = x; s->next = head; */
      steps.push(makeStep(sid++,"link","s->next = head","s->next = head",
        view("circular_linked_list",[pan("LA",c,{focusIndex:idx,pointers:{head:0,r:tail},write:idx}),row("meta",[],{operation:op,circular:true,tail,value:x})]),
        [action("link","新结点先接回头结点",{target:idx,to:"head",value:x})]));
      c.next[tail]=idx+1;                                             /* r->next = s;  r = s; */
      steps.push(makeStep(sid++,"link","r->next = s","r->next = s",
        view("circular_linked_list",[pan("LA",c,{focusIndex:idx,pointers:{head:0,r:tail},write:tail}),row("meta",[],{operation:op,circular:true,tail:idx,value:x})]),
        [action("link","原尾结点接到新结点",{target:tail,to:x,value:x})]));
      tail=idx;
    }
    return makeTrace(req,"建立循环单链表",`输入=${vals.join(",")}`,`循环表=head→${vals.join("→")}→head`,steps);
  }
  /* 两个表并排画时，两个头结点**必须各有名字**：`q->next = A` 之后 B 的尾结点指向的是 **A 的头结点**，
     如果两个都叫 head，这一帧和"指向自己"长得一模一样——学生看不出接没接上（契约测试会直接判它同画面）。 */
  const A=chain(left,"headA"),B=chain(right,"headB");
  const merged=[...left,...right],M=chain(merged,"headA");
  const headPointer=op==="merge_head_pointer";
  const steps=[makeStep(sid++,"init",headPointer?"A / B":"rA / rB",headPointer?"A、B 分别是两个循环表的头指针":"rA、rB 分别是两个循环表的尾指针",
    view("circular_linked_list",[
      pan("LA",A,headPointer?{pointers:{A:0}}:{pointers:{rA:A.labels.length-1}}),
      pan("LB",B,headPointer?{pointers:{B:0}}:{pointers:{rB:B.labels.length-1}}),
      row("meta",[],{operation:op,circular:true})
    ]))];

  if(headPointer){
    /* 算法 2.14：先沿 next 走到两个表的尾结点，再把 A 尾接到 B 的首数据结点、B 尾接回 A。每行一帧。 */
    steps.push(makeStep(sid++,"line","p = A","p = A",
      view("circular_linked_list",[pan("LA",A,{pointers:{p:0,A:0}}),pan("LB",B,{pointers:{B:0}}),row("meta",[],{operation:op,circular:true})]),[action("move","p 指向 A 的头结点",{target:"p"})]));
    for(let i=0;i<A.labels.length-1;i++){
      steps.push(makeStep(sid++,"line","p = p->next","p = p->next",
        view("circular_linked_list",[pan("LA",A,{focusIndex:i+1,pointers:{p:i+1,A:0}}),pan("LB",B,{pointers:{B:0}}),row("meta",[],{operation:op,circular:true})]),[action("move","p 后移",{target:"p",value:i+1})]));
    }
    A.next[A.labels.length-1]="B#2";
    steps.push(makeStep(sid++,"assign","p->next = B->next","p->next = B->next",
      view("circular_linked_list",[pan("LA",A,{focusIndex:A.labels.length-1,pointers:{p:A.labels.length-1,A:0},write:A.labels.length-1}),pan("LB",B,{pointers:{B:0}}),row("meta",[],{operation:op,circular:true})]),[action("link","改写 A 尾结点的 next",{target:"p->next",from:"head",to:"2"})]));
    steps.push(makeStep(sid++,"line","q = B","q = B",
      view("circular_linked_list",[pan("LA",A,{pointers:{p:A.labels.length-1,A:0}}),pan("LB",B,{pointers:{q:0,B:0}}),row("meta",[],{operation:op,circular:true})]),[action("move","q 指向 B 的头结点",{target:"q"})]));
    for(let i=0;i<B.labels.length-1;i++){
      steps.push(makeStep(sid++,"line","q = q->next","q = q->next",
        view("circular_linked_list",[pan("LA",A,{pointers:{p:A.labels.length-1,A:0}}),pan("LB",B,{focusIndex:i+1,pointers:{q:i+1,B:0}}),row("meta",[],{operation:op,circular:true})]),[action("move","q 后移",{target:"q",value:i+1})]));
    }
    B.next[B.labels.length-1]="A#1";
    steps.push(makeStep(sid++,"assign","q->next = A","q->next = A",
      view("circular_linked_list",[pan("LA",A,{pointers:{p:A.labels.length-1,A:0}}),pan("LB",B,{focusIndex:B.labels.length-1,pointers:{q:B.labels.length-1,B:0},write:B.labels.length-1}),row("meta",[],{operation:op,circular:true})]),[action("link","改写 B 尾结点的 next",{target:"q->next",from:"head",to:"A"})]));
  } else {
    /* 算法 2.15：有尾指针就不用找尾，三行赋值接完。 */
    steps.push(makeStep(sid++,"line","p = rA->next","p = rA->next",
      view("circular_linked_list",[pan("LA",A,{pointers:{p:0,rA:A.labels.length-1}}),pan("LB",B,{pointers:{rB:B.labels.length-1}}),row("meta",[],{operation:op,circular:true})]),[action("move","p = rA->next",{target:"p",value:0})]));
    A.next[A.labels.length-1]="B#2";
    steps.push(makeStep(sid++,"assign","rA->next = rB->next","rA->next = rB->next",
      view("circular_linked_list",[pan("LA",A,{focusIndex:A.labels.length-1,pointers:{p:0,rA:A.labels.length-1},write:A.labels.length-1}),pan("LB",B,{pointers:{rB:B.labels.length-1}}),row("meta",[],{operation:op,circular:true})]),[action("link","改写 A 尾结点的 next",{target:"rA->next",from:"head",to:"2"})]));
    B.next[B.labels.length-1]="A#1";
    steps.push(makeStep(sid++,"assign","rB->next = p","rB->next = p",
      view("circular_linked_list",[pan("LA",A,{pointers:{p:0,rA:A.labels.length-1}}),pan("LB",B,{focusIndex:B.labels.length-1,pointers:{rB:B.labels.length-1},write:B.labels.length-1}),row("meta",[],{operation:op,circular:true})]),[action("link","改写 B 尾结点的 next",{target:"rB->next",to:"A"})]));
  }
  steps.push(makeStep(sid++,"free","free(B)","free(B)",
    view("circular_linked_list",[pan("LA",M,{pointers:{head:0}}),row("LB",[],{next:[]}),row("meta",[],{operation:op,circular:true,merged:true})]),[action("free","释放 B 的头结点",{value:"B"})]));
  return makeTrace(req,headPointer?"循环单链表合并（头指针）":"循环单链表合并（尾指针）","两个循环表",`L=head→${merged.join("→")}→head`,steps);
}

function simulateStaticList(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation; const size=int(req.params.size,8,4,30,"size");let sid=1;
  /* 静态链表的"指针"就是数组下标（`cursor` 栏），所以整个操作就是**逐格改写 cursor**。
     代码级：一帧 = 一行（`space[i].cur = i+1` / `av = space[p].cur` …），
     已用/空闲靠 free 那栏区分，备用链的走向直接从 cursor 栏读出来。 */
  let nodes=Array.from({length:size},(_,i)=>({index:i,data:null,cursor:null,free:true}));
  const steps=[];
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"assign",code,code,view("static_linked_list",[row("nodes",nodes),row("meta",[],{operation:op,...extra})]),act?[act]:[]));
  if(op==="initialize"){
    for(let i=0;i<size-1;i++){nodes[i].cursor=i+1;snap(`space[${i}].cur = ${i+1}`,{available:0},null,true);}
    nodes[size-1].cursor=0;
    snap(`space[${size-1}].cur = 0`,{available:0},action("link","备用链收尾：最后一个结点指回 0",{target:"cur"}));
    return makeTrace(req,"静态单链表初始化",`空间=${size}`,"备用链建立",steps);
  }
  const used=Array.isArray(req.params.usedIndices)?req.params.usedIndices.map((v,i)=>{const n=Number(v);if(!Number.isInteger(n)||n<0||n>=size)throw new SimulationInputError("INVALID_INDEX",`usedIndices 第 ${i+1} 项 ${JSON.stringify(v)} 必须是 0~${size-1} 的下标`,`usedIndices[${i}]`);return n;}):[1,2,3];
  for(const i of used){nodes[i].free=false;nodes[i].data=`D${i}`;}
  snap("av = 0",{used},action("assign","av 是备用链头",{}),true);
  const freeIdx=nodes.findIndex((n,i)=>i>0&&n.free);
  if(op==="allocate"){
    if(freeIdx<0)return makeTrace(req,"静态链表申请结点","备用链","无空闲结点",steps);
    snap("p = av",{used,current:freeIdx,av:0},null,true);
    snap("av = space[p].cur",{used,current:freeIdx,av:nodes[freeIdx].cursor},null,true);
    nodes[freeIdx].free=false;nodes[freeIdx].data=req.params.value??"NEW";
    snap("space[p].data = x",{used,allocated:freeIdx},action("allocate","把数据写进这个结点",{target:freeIdx,value:nodes[freeIdx].data}));
    return makeTrace(req,"静态链表申请空间","备用链",`index=${freeIdx}`,steps);
  }
  const target=int(req.params.index,used[0]??1,1,size-1,"index");if(nodes[target].free)throw new SimulationInputError("INVALID_INDEX",`下标 ${target} 的结点不在已用状态，无法释放（已用下标：${used.join("、")}）`,"index");
  snap("p = 待释放结点下标",{used,current:target},null,true);
  nodes[target].cursor=0;
  snap("space[p].cur = av",{used,current:target},action("link","把释放结点的 cursor 接到备用链头",{target:"cur"}));
  nodes[target].free=true;nodes[target].data=null;
  snap("av = p",{used,freed:target},action("assign","备用链头回到这个结点",{}));
  return makeTrace(req,"静态链表释放空间",`index=${target}`,"已归还备用链",steps);
}

function normalizeTerms(v,f=[{coef:3,exp:3},{coef:2,exp:1},{coef:1,exp:0}]){const a=Array.isArray(v)?v:f;return a.map((t,i)=>{if(!t||typeof t!=="object"||Array.isArray(t))throw new SimulationInputError("INVALID_TERM",`第 ${i+1} 项 ${JSON.stringify(t)} 格式不正确：多项式项应为 { "coef": 数字, "exp": 数字 }（如 { "coef": 3, "exp": 4 }）`,`terms[${i}]`);const coef=Number(t.coef??t.coefficient),exp=Number(t.exp??t.exponent);if(!Number.isFinite(coef))throw new SimulationInputError("INVALID_TERM",`第 ${i+1} 项的系数必须是有限数字（当前 ${JSON.stringify(t.coef??t.coefficient)}）`,`terms[${i}]`);if(!Number.isInteger(exp)||exp<0)throw new SimulationInputError("INVALID_TERM",`第 ${i+1} 项的指数必须是非负整数（当前 ${JSON.stringify(t.exp??t.exponent)}）`,`terms[${i}]`);return {coef,exp};});}
function simulatePolynomialBuild(req,api){const {makeStep,makeTrace,action}=helpers(api);const terms=normalizeTerms(req.params.terms??req.initial_state.data);const list=[];let sid=1;const steps=[makeStep(sid++,"init","空多项式链表","按指数有序插入各项。",view("polynomial",[row("P",list),row("meta",[],{operation:req.operation})]))];for(const t of terms){let pos=list.findIndex(x=>x.exp<t.exp);if(pos<0)pos=list.length;list.splice(pos,0,t);/* 高亮刚插入的那一项，否则整条建立过程一行高亮都没有 */steps.push(makeStep(sid++,"insert","插入多项式项",`${t.coef}x^${t.exp} 插入指数有序位置。`,view("polynomial",[row("P",list,{focusIndex:pos}),row("meta",[],{current:t})]),[action("insert","插入项结点",{target:pos,value:`${t.coef}x^${t.exp}`})]));}return makeTrace(req,"建立一元多项式链表","输入项",`P=${list.map(t=>`${t.coef}x^${t.exp}`).join("+")}`,steps);}

function simulateStackAux(req,api){const {makeStep,makeTrace,action}=helpers(api);const data=scalarArray(req.initial_state.data,[2,5,7]);const op=req.operation;if(op==="initialize"){const capacity=int(req.params.capacity,10,1,100);return makeTrace(req,"顺序栈初始化",`capacity=${capacity}`,"top=-1",[makeStep(1,"init","建立空顺序栈","令 top=-1，表示栈中还没有元素。",view("stack",[row("stack",[]),row("meta",[],{top:-1,capacity,operation:op})]),[action("assign","设置栈顶指针",{target:"top",value:-1})])]);}const top=data.length-1;const steps=[makeStep(1,"init","顺序栈状态",`top=${top}，栈内元素自栈底到栈顶为 ${data.join(", ")||"（空）"}。`,view("stack",[row("stack",data),row("meta",[],{top,operation:"peek"})]))];if(data.length)steps.push(makeStep(2,"locate","top 指向栈顶","读栈顶只看 top 所指单元，不移动指针、不弹元素。",view("stack",[row("stack",data),row("meta",[],{top,operation:"peek"})]),[action("read","定位栈顶",{target:top})]));steps.push(makeStep(steps.length+1,"read","读取栈顶但不修改 top",data.length?`读取 ${data[top]}，top 仍为 ${top}。`:"空栈不能读取栈顶。",view("stack",[row("stack",data),row("meta",[],{top,peek:data[top]??null,operation:"peek"})]),[action("read","读取栈顶",{target:top,value:data[top]??null})]));return makeTrace(req,"读取栈顶元素",`top=${top}`,data.length?String(data[top]):"空栈",steps);}

function simulateDoubleStack(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const capacity=int(req.params.capacity,8,4,30,"capacity");const raw=req.initial_state.data;const left=scalarArray(req.params.left??raw?.[0],[1,2]),right=scalarArray(req.params.right??raw?.[1],[9,8]);let sid=1;const st=(extra={},focusSide=null,focusIndex=null)=>view("double_stack",[row("left",left,focusSide==="left"&&focusIndex!==null?{focusIndex}:{}),row("right",right,focusSide==="right"&&focusIndex!==null?{focusIndex}:{}),row("meta",[],{capacity,topLeft:left.length-1,topRight:capacity-right.length,free:capacity-left.length-right.length,...extra})]);const steps=[makeStep(sid++,"init","双端顺序栈",`两个栈从数组两端向中间增长。`,st({operation:op}))];if(op==="initialize")return makeTrace(req,"双端顺序栈初始化","共享数组","两端栈顶就位",steps);if(op.startsWith("push")){if(left.length+right.length>=capacity)return api.makeRuntimeError(req,"双端顺序栈进栈","STACK_OVERFLOW",`共享数组已满（capacity=${capacity}，两栈共占 ${left.length+right.length} 个单元），不能再进栈。`,st({operation:op}),"栈满");const side=op.endsWith("left")?left:right;const val=req.params.value??(side===left?3:7);steps.push(makeStep(sid++,"check","进栈前检查共享空间",`两栈共占 ${left.length+right.length}/${capacity} 个单元，${side===left?"左":"右"}栈顶将从 ${side.length-1} 移到 ${side.length}。`,st({operation:op,value:val,phase:"check"},op.endsWith("left")?"left":"right",side.length-1),[action("check","检查两栈顶是否相遇",{value:val})]));side.push(val);steps.push(makeStep(sid,"push",side===left?"左栈进栈":"右栈进栈",`${val} 从${side===left?"左":"右"}端进入共享数组。`,st({operation:op},op.endsWith("left")?"left":"right",side.length-1),[action("push","双端栈进栈",{value:val})]));}else{const side=op.endsWith("left")?left:right;if(!side.length)return api.makeRuntimeError(req,"双端顺序栈出栈","STACK_UNDERFLOW",`${op.endsWith("left")?"左":"右"}栈为空，不能出栈。`,st({operation:op}),"空栈");steps.push(makeStep(sid++,"check","栈顶元素就位",`${side===left?"左":"右"}栈顶是 ${side[side.length-1]}，出栈后栈顶指针向端点退一格。`,view("double_stack",[row(side===left?"left":"right",side,{focusIndex:side.length-1}),row(side===left?"right":"left",side===left?right:left),row("meta",[],{capacity,topLeft:left.length-1,topRight:capacity-right.length,free:capacity-left.length-right.length,operation:op,phase:"check"})]),[action("read","定位栈顶",{target:side.length-1})]));const val=side.pop();steps.push(makeStep(sid,"pop",side===left?"左栈出栈":"右栈出栈",`${String(val)} 从${side===left?"左":"右"}端退出。`,st({operation:op}),[action("pop","双端栈出栈",{value:val})]));}return makeTrace(req,"双端顺序栈操作","共享空间","操作完成",steps);}

function simulateLinkedStack(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const data=scalarArray(req.initial_state.data,[2,5,7]);let sid=1;
  /* 代码级：一帧 = 一行赋值（`s->next = top` / `top = s`），槽位写**目标结点的序号**，
     `pointers` 是"栈顶指针 top 指着第几个结点"。逐行帧标 `phase: "line"`。 */
  const c={labels:data.map(String),next:data.map((_,i)=>i+1<data.length?i+2:null)};
  const pan=(extra={})=>row("stack",c.labels,{next:[...c.next],...extra});
  const steps=[];
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"assign",code,code,view("linked_stack",[pan(extra),row("meta",[],{top:c.labels.length?0:null,operation:op})]),act?[act]:[]));
  snap("top = head",{pointers:{top:0}},action("assign","栈顶指针指向链首",{}),true);
  if(op==="push"){
    const v=req.params.value??9;
    c.labels.unshift("");
    // unshift 之后**所有**结点序号都变了，next 必须整体重建（新结点留空，等 s->next = top 那一行接上）。
    c.next=c.labels.map((_,i)=>(i===0?null:(i+1<c.labels.length?i+2:null)));
    snap("s = malloc()",{pointers:{top:0,s:0}},null,true);
    c.labels[0]=String(v);
    snap(`s->data = ${v}`,{pointers:{top:0,s:0}},null,true);
    c.next[0]=2;
    snap("s->next = top",{pointers:{top:0,s:0},write:0},action("link","新结点接到链首",{to:2}));
    snap("top = s",{pointers:{top:0,s:0}},action("assign","栈顶改指向新结点",{}));
    return makeTrace(req,"链栈进栈","链栈",`top=${v}`,steps);
  }
  if(!c.labels.length)return api.makeRuntimeError(req,"链栈出栈","STACK_UNDERFLOW","链栈为空，不能出栈。",view("linked_stack",[row("stack",[]),row("meta",[],{top:null,operation:op})]),"空栈");
  snap("q = top",{pointers:{top:0,q:0}},null,true);
  snap("top = top->next",{pointers:{top:c.labels.length>1?1:0,q:0}},action("assign",c.labels.length>1?"top 指向下一个结点":"栈变空",{}));
  const removed=c.labels[0];
  const freed={labels:c.labels.slice(1),next:c.labels.slice(1).map((_,i)=>i+1<c.labels.length-1?i+2:null)};
  steps.push(makeStep(sid++,"assign","free(q)","free(q)",view("linked_stack",[row("stack",freed.labels,{next:[...freed.next]}),row("meta",[],{top:freed.labels.length?0:null,operation:op})]),[action("free","释放原栈顶",{value:removed})]));
  return makeTrace(req,"链栈出栈","链栈","操作完成",steps);
}

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
    /* 高亮用**下标**，不用值：`current` 在前端是"指针 → 下标"，早先这里传的是层数 k（如 12、11…），
       落到 8 层的栈上直接越界、整条动画一格高亮都没有（2026-09-24 真机扫描发现）。 */
    for (let k = n; k > 1; k--) {
      stack.push(k);
      steps.push(makeStep(
        sid++, "call", "递归压栈", `Fact(${k}) 等待 Fact(${k - 1}) 返回。`,
        view("recursion", [row("call_stack", stack, { focusIndex: stack.length - 1 }), row("meta", [], { current: k })]),
        [action("push", "保存递归层", { value: k })]
      ));
    }
    let result = 1;
    while (stack.length) {
      const k = stack.pop();
      result *= k;
      steps.push(makeStep(
        sid++, "return", "递归返回", `乘以 ${k}，当前结果 ${result}。`,
        view("recursion", [row("call_stack", stack, { focusIndex: stack.length - 1 }), row("meta", [], { result, current: k })]),
        [action("pop", "返回一层", { value: k })]
      ));
    }
    return makeTrace(req, "阶乘递归调用过程", `n=${n}`, `${n}!=${result}`, steps);
  }

  if (op === "factorial_iterative") {
    // 循环累乘的每一帧都要看得见东西：累积序列（数组）+ 当前乘数与结果（记录）。
    // 早先这里每帧只发一行 `meta`，而 meta 只变成帧元信息 ⇒ 整个动画在界面上是全空白。
    let result = 1;
    const partials = [1];
    const panels = (extra = {}) => [
      row("result", [...partials]),
      row("records", [
        { label: "n", value: n },
        { label: "这一轮乘", value: extra.k ?? 1 },
        { label: "result", value: result },
      ]),
      row("meta", [], { n, result, ...extra, operation: op }),
    ];
    steps.push(makeStep(
      sid++, "init", "循环求阶乘", `result 从 1 开始，依次乘 2..${n}，每乘一次 result 就变大一步。`,
      view("recursion", panels())
    ));
    for (let k = 2; k <= n; k++) {
      const before = result;
      result *= k;
      partials.push(result);
      steps.push(makeStep(
        sid++, "iterate", "循环累乘", `result = ${before} × ${k} = ${result}。`,
        view("recursion", panels({ k })),
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
        view("recursion", [row("sequence", seq, { focusIndex: seq.length - 1 }), row("meta", [], { k, a, b })]),
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

function simulateLinkedQueue(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const data=scalarArray(req.initial_state.data,[4,7,9]);let sid=1;
  /* **带头结点**的链队列（教材：front 指向头结点、rear 指向队尾结点）——
     initialize 那一课把"只剩一个头结点"画出来，后面的操作也就都以它为第 1 个结点。
     一帧 = 一行：`s->next = NULL` / `rear->next = s` / `rear = s`；槽位写目标结点的序号。 */
  const ring=(values)=>{const labels=["head",...values.map(String)];return {labels,next:labels.map((_,i)=>i+1<labels.length?i+2:null)};};
  const c=ring(data);
  const pan=(extra={})=>row("queue",c.labels,{next:[...c.next],...extra});
  const meta=()=>({front:c.labels.length?0:null,rear:c.labels.length?c.labels.length-1:null,operation:op});
  const steps=[];
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"assign",code,code,view("linked_queue",[pan(extra),row("meta",[],meta())]),act?[act]:[]));
  if(op==="initialize"){
    snap("front = rear = head",{pointers:{front:0,rear:0}},action("assign","front 与 rear 都指向头结点",{}));
    snap("head->next = NULL",{pointers:{front:0,rear:0},write:0},action("link","头结点 next 置空：队列里没有数据结点",{to:"NULL"}));
    return makeTrace(req,"链队列初始化","未初始化","front=rear=head，head->next=NULL",steps);
  }
  snap("front = head",{pointers:{front:0,rear:c.labels.length-1}},null,true);
  if(op==="enqueue"){
    const v=req.params.value??12;const idx=c.labels.length;
    c.labels.push("");c.next.push(null);
    snap("s = malloc()",{pointers:{front:0,rear:idx-1,s:idx}},null,true);
    c.labels[idx]=String(v);
    snap(`s->data = ${v}`,{pointers:{front:0,rear:idx-1,s:idx}},null,true);
    snap("s->next = NULL",{pointers:{front:0,rear:idx-1,s:idx},write:idx},action("link","新结点是队尾，next 置空",{to:"NULL"}));
    c.next[idx-1]=idx+1;
    snap("rear->next = s",{pointers:{front:0,rear:idx-1,s:idx},write:idx-1},action("link","原队尾指向新结点",{to:idx+1}));
    snap("rear = s",{pointers:{front:0,rear:idx}},action("assign","队尾指针后移到新结点",{}));
    return makeTrace(req,"链队列入队","链队列",`rear=${v}`,steps);
  }
  if(c.labels.length<=1)return api.makeRuntimeError(req,"链队列出队","QUEUE_UNDERFLOW","队列为空（只有头结点），不能出队。",view("linked_queue",[row("queue",c.labels,{next:[...c.next]}),row("meta",[],{front:0,rear:0,operation:op})]),"空队列");
  snap("q = front->next",{pointers:{front:0,q:1}},null,true);
  c.next[0]=c.next[1];
  snap("front->next = q->next",{pointers:{front:0,q:1},write:0},action("link","头结点越过队首数据结点",{to:c.next[0]}));
  const removed=c.labels[1];
  const rest=c.labels.slice(2);
  const after=ring(rest);
  steps.push(makeStep(sid++,"assign","free(q)","free(q)",view("linked_queue",[row("queue",after.labels,{next:[...after.next]}),row("meta",[],{front:0,rear:after.labels.length-1,operation:op})]),[action("free","释放队首数据结点",{value:removed})]));
  return makeTrace(req,"链队列出队","链队列","操作完成",steps);
}

function simulateCircularQueueInit(req,api){const {makeStep,makeTrace}=helpers(api);const cap=int(req.params.capacity,6,2,30);return makeTrace(req,"循环队列初始化",`capacity=${cap}`,"front=rear=0",[makeStep(1,"init","设置队首队尾指针","令 front=rear=0，队列为空。",view("circular_queue",[row("buffer",Array(cap).fill(null)),row("meta",[],{capacity:cap,front:0,rear:0,count:0,operation:"initialize"})]))]);}

function simulateYanghui(req,api){const {makeStep,makeTrace,action}=helpers(api);const n=int(req.params.rows,6,2,12);let prev=[1],sid=1;const all=[prev];const steps=[makeStep(sid++,"init","杨辉三角第 1 行","队列保存当前行数据。",view("queue_app",[row("current",prev),row("rows",all),row("meta",[],{row:1,operation:req.operation})]))];for(let r=2;r<=n;r++){const next=[1];for(let i=0;i<prev.length-1;i++)next.push(prev[i]+prev[i+1]);next.push(1);all.push(next);steps.push(makeStep(sid++,"queue","由上一行生成下一行",`第 ${r} 行：${next.join(" ")}`,view("queue_app",[row("current",next,{focusIndex:next.length-1}),row("rows",all),row("meta",[],{row:r})]),[action("compute","相邻两个队列元素相加",{value:r})]));prev=next;}return makeTrace(req,"利用队列生成杨辉三角",`n=${n}`,`生成 ${n} 行`,steps);}

function simulateCircularBuffer(req,api){const {makeStep,makeTrace,action}=helpers(api);const cap=int(req.params.capacity,6,3,20,"capacity");const rawInput=String(req.params.input??"ABCDEFGH");if(rawInput.length>30)throw new SimulationInputError("INPUT_TOO_LONG",`输入长度 ${rawInput.length} 超过 30 字符上限`,"input");const input=[...rawInput];const buf=Array(cap).fill(null);let front=0,rear=0,count=0,sid=1;const steps=[makeStep(sid++,"init","循环输入缓冲区","front=rear=0。",view("circular_buffer",[row("buffer",buf),row("meta",[],{front,rear,count,capacity:cap})]))];for(const ch of input){if(count===cap){const slot=front;const removed=buf[front];buf[front]=null;front=(front+1)%cap;count--;/* 高亮刚被消费的那一格 */steps.push(makeStep(sid++,"consume","缓冲区满，先消费最早字符",`读出 ${removed}，front 循环后移。`,view("circular_buffer",[row("buffer",buf,{focusIndex:slot}),row("meta",[],{front,rear,count,current:removed})]),[action("dequeue","读出字符",{value:removed})]));}const slot=rear;buf[rear]=ch;rear=(rear+1)%cap;count++;steps.push(makeStep(sid++,"input","写入一个键盘字符",`${ch} 写入 rear 原位置，rear 取模后移。`,view("circular_buffer",[row("buffer",buf,{focusIndex:slot}),row("meta",[],{front,rear,count,current:ch})]),[action("enqueue","写入缓冲区",{value:ch})]));}return makeTrace(req,"键盘输入循环缓冲区",`input=${input.join("")}`,`缓冲区保留 ${count} 个字符`,steps);}

function simulateStringCompare(req,api){const {makeStep,makeTrace,action}=helpers(api);const a=[...String(req.params.left??"DATA")],b=[...String(req.params.right??"DATE")];let sid=1;const steps=[makeStep(sid++,"init","两个字符串","从第一个字符开始比较。",view("string_match",[row("left",a),row("right",b),row("meta",[],{i:0,operation:req.operation})]))];let result=0,i=0;for(;i<Math.min(a.length,b.length);i++){steps.push(makeStep(sid++,"compare","逐字符比较",`${a[i]} ${a[i]===b[i]?"=":a[i]<b[i]?"<":">"} ${b[i]}`,view("string_match",[row("left",a),row("right",b),row("meta",[],{i})]),[action("compare","比较对应字符",{target:i})]));if(a[i]!==b[i]){result=a[i]<b[i]?-1:1;break;}}if(result===0)result=a.length===b.length?0:a.length<b.length?-1:1;return makeTrace(req,"串比较",a.join(""),result===0?"相等":result<0?"左串较小":"左串较大",steps);}

function simulateHeapString(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;const text=String(req.params.text??req.initial_state.data??"DATA"),value=String(req.params.value??"STRUCT"),pos=int(req.params.position,Math.min(3,text.length+1),1,text.length+1);let sid=1;const blocks=[...text];const steps=[makeStep(sid++,"init","堆串当前值",text,view("heap_string",[row("chars",blocks),row("meta",[],{length:blocks.length,operation:op})]))];if(op==="assign"){
  // 「空空的啥也没有」：原来释放帧的 chars 是空数组，申请帧一次把整串塞进去且没有指针，两帧都看不出在干什么。
  // 现在释放帧把旧串值连同区间一起摆出来（整段高亮 = 这块空间要被回收），新空间先摆出 n 个空槽再逐字符填充。
  steps.push(makeStep(sid++,"free","释放原串值空间",`旧串值空间（${blocks.length} 个字符：${text}）先归还堆。`,view("heap_string",[row("chars",blocks),row("meta",[],{length:blocks.length,operation:op,phase:"free",low:0,high:Math.max(0,blocks.length-1)})]),[action("free","释放旧空间",{value:blocks.length})]));
  const next=[...value],slots=new Array(next.length).fill(null);
  steps.push(makeStep(sid++,"allocate","申请新串值空间",`申请 ${next.length} 个字符空间，准备把 ${value} 逐个复制进去。`,view("heap_string",[row("chars",slots),row("meta",[],{length:next.length,operation:op,written:0})]),[action("allocate","动态申请串值空间",{value:next.length})]));
  for(let i=0;i<next.length;i++){slots[i]=next[i];steps.push(makeStep(sid++,"copy","逐字符复制",`第 ${i+1} 个字符 ${next[i]} 写入新空间：${next.slice(0,i+1).join("")}。`,view("heap_string",[row("chars",[...slots],{focusIndex:i}),row("meta",[],{length:next.length,operation:op,written:i+1})]),[action("copy","复制字符",{target:i,value:next[i]})]));}
  return makeTrace(req,"堆串赋值",text,value,steps);
}const next=[...text];next.splice(pos-1,0,...value);steps.push(makeStep(sid++,"allocate","申请扩大后的串值空间",`新长度 ${next.length}。`,view("heap_string",[row("chars",next),row("meta",[],{position:pos,length:next.length,operation:op})]),[action("allocate","申请新空间",{value:next.length})]));steps.push(makeStep(sid,"copy","复制前段、插入串和后段",`在第 ${pos} 个位置插入 ${value}。`,view("heap_string",[row("chars",next),row("meta",[],{position:pos,length:next.length,operation:op})]),[action("insert","复制并插入",{target:pos-1,value})]));return makeTrace(req,"堆串插入",text,next.join(""),steps);}

const SPECIAL_MATRIX_KINDS = Object.freeze(["lower_triangular", "upper_triangular", "symmetric", "tridiagonal"]);
const SPECIAL_MATRIX_LABELS = Object.freeze({
  lower_triangular: "下三角矩阵",
  upper_triangular: "上三角矩阵",
  symmetric: "对称矩阵",
  tridiagonal: "三对角矩阵",
});

/** `kind` 在实验室里是手输的自由文本：大小写、连字符都放过，但值必须是教材里那四种之一。 */
function specialMatrixKind(raw) {
  const text = String(raw === undefined || raw === null || raw === "" ? "lower_triangular" : raw)
    .trim().toLowerCase().replace(/[-\s]+/g, "_");
  const alias = { lower: "lower_triangular", upper: "upper_triangular", lower_triangular: "lower_triangular", upper_triangular: "upper_triangular", symmetric: "symmetric", tridiagonal: "tridiagonal" };
  if (!ownOf(alias, text)) {
    throw new SimulationInputError("INVALID_PARAM", `kind=${JSON.stringify(raw)} 不是支持的矩阵类型：只支持 ${SPECIAL_MATRIX_KINDS.join("、")}`, "kind");
  }
  return alias[text];
}

/** 存储次序：按行扫描，把落在存储区的元素依次放进压缩数组。 */
function specialMatrixSlots(n, kind) {
  const slots = [];
  for (let r = 1; r <= n; r++) {
    if (kind === "upper_triangular") { for (let c = r; c <= n; c++) slots.push([r, c]); }
    else if (kind === "tridiagonal") { for (let c = Math.max(1, r - 1); c <= Math.min(n, r + 1); c++) slots.push([r, c]); }
    else { for (let c = 1; c <= r; c++) slots.push([r, c]); }
  }
  return slots;
}

/** A[i,j] 是否在存储区（对称矩阵每一对元素都存一个）。 */
function specialMatrixStored(kind, i, j) {
  if (kind === "lower_triangular") return i >= j;
  if (kind === "upper_triangular") return i <= j;
  if (kind === "tridiagonal") return Math.abs(i - j) <= 1;
  return true;
}

/** 教材 5.3 的下标公式拆成「前面整行 + 本行内偏移」两段，界面要把这两段分开讲。 */
function specialMatrixOffset(n, kind, i, j) {
  if (kind === "symmetric") {
    const r = Math.max(i, j), c = Math.min(i, j);
    return { row: r, column: c, before: (r - 1) * r / 2, offset: c - 1, formula: "k = (r-1)*r/2 + (c-1)，r=max(i,j)，c=min(i,j)" };
  }
  if (kind === "upper_triangular") return { row: i, column: j, before: (i - 1) * (2 * n - i + 2) / 2, offset: j - i, formula: "k = (i-1)*(2n-i+2)/2 + (j-i)" };
  // 第 1 行只有 a11、a12 两个存储元素（左上方没有元），所以行内偏移从 0 起算；
  // 其余各行都是「左上、对角、右下」三个，行内偏移为 j-i+1。
  if (kind === "tridiagonal") return { row: i, column: j, before: i === 1 ? 0 : 3 * i - 4, offset: i === 1 ? j - 1 : j - i + 1, formula: "k = 2i+j-3" };
  return { row: i, column: j, before: (i - 1) * i / 2, offset: j - 1, formula: "k = (i-1)*i/2 + (j-1)" };
}

/** 存储区的说法，用来解释这个下标为什么在/不在压缩数组里。 */
function specialMatrixRegion(kind, i, j) {
  if (kind === "lower_triangular") return i >= j ? `i=${i} ≥ j=${j}，落在下三角` : `i=${i} < j=${j}，落在下三角之外的常量区`;
  if (kind === "upper_triangular") return i <= j ? `i=${i} ≤ j=${j}，落在上三角` : `i=${i} > j=${j}，落在上三角之外的常量区`;
  if (kind === "tridiagonal") return Math.abs(i - j) <= 1 ? `|i-j| = ${Math.abs(i - j)} ≤ 1，落在主对角线带上` : `|i-j| = ${Math.abs(i - j)} > 1，落在带外常量区`;
  return `对称矩阵的 A[${i},${j}] 与 A[${j},${i}] 共用同一个位置`;
}

/**
 * 特殊矩阵的压缩存储：把二维下标映射到一维压缩数组的下标。
 *
 * 画面上矩阵里的数字就是压缩数组里的数字 —— 存储区按存储次序填 1..m，常量区填 0，所以
 * 「A[i,j] 的值 = B[k] 的值」这件事是看得见的，而不是只给一个 k。
 */
function simulateSpecialMatrix(req, api) {
  const { makeStep, makeTrace, action } = helpers(api);
  const n = int(req.params.n, 5, 2, 20, "n");
  const kind = specialMatrixKind(req.params.kind);
  const i = int(req.params.i, Math.min(4, n), 1, n, "i");
  const j = int(req.params.j, Math.min(2, n), 1, n, "j");
  const label = SPECIAL_MATRIX_LABELS[kind];
  const slots = specialMatrixSlots(n, kind);
  const stored = specialMatrixStored(kind, i, j);

  const grid = Array.from({ length: n }, () => new Array(n).fill(0));
  slots.forEach(([r, c], index) => {
    grid[r - 1][c - 1] = index + 1;
    if (kind === "symmetric") grid[c - 1][r - 1] = index + 1;   // 对称位置放同一个值
  });
  const packed = slots.map((_slot, index) => index + 1);
  // 下三角/上三角的常量区只占一个位置，教材把它放在 B[n(n+1)/2]；三对角矩阵的带外元素根本不存。
  const array = !stored && kind !== "tridiagonal" ? [...packed, 0] : packed;
  const offset = specialMatrixOffset(n, kind, i, j);
  const k = stored ? offset.before + offset.offset : (kind === "tridiagonal" ? -1 : packed.length);
  const cellValue = grid[i - 1][j - 1];

  const matrixPanel = (extra = {}) => row("matrix", grid, { focusCell: [i - 1, j - 1], ...extra });
  // 面板上的小胶囊：这一帧用的是哪一阶、哪种矩阵、哪个下标；公式和 k 只在算出来之后才带上。
  const metaPanel = (extra = {}) => row("meta", [], { n, kind, i, j, ...extra });
  const build = kind === "lower_triangular" ? "下三角" : kind === "upper_triangular" ? "上三角" : kind === "symmetric" ? "下三角（对称元素共用）" : "主对角线带";
  const derivation = [
    { label: "压缩数组", value: `B[0..${array.length - 1}]，按${build}的存储次序，共 ${array.length} 个分量` },
    { label: "前面整行", value: offset.row === 1 ? `第 ${offset.row} 行是首行，前面没有整行` : `第 1..${offset.row - 1} 行共 ${offset.before} 个存储元素` },
    { label: "本行内偏移", value: `A[${i},${j}] 在它这一行里排第 ${offset.offset + 1} 个（前面还有 ${offset.offset} 个）` },
    { label: "下标公式", value: offset.formula },
    { label: "代入结果", value: `k = ${offset.before} + ${offset.offset} = ${k}` },
  ];

  const steps = [
    makeStep(1, "locate", "定位矩阵元素",
      `${n} 阶${label}：先找到第 ${i} 行第 ${j} 列的 A[${i},${j}]。矩阵里的数字就是压缩数组里存的数字，0 表示这一片不单独存储。`,
      view("special_matrix", [matrixPanel(), metaPanel()])),
    makeStep(2, "classify", stored ? `A[${i},${j}] 落在存储区` : `A[${i},${j}] 落在常量区`,
      `${specialMatrixRegion(kind, i, j)}。${stored ? `存储区的元素按存储次序写进压缩数组，A[${i},${j}] 的存储位置就是它的压缩下标 k。` : kind === "tridiagonal" ? `带外元素全为 0，不进入压缩数组，所以它没有压缩下标。` : `这一片的值全为同一个常量，压缩数组里只留一个位置放它。`}`,
      view("special_matrix", [matrixPanel(), metaPanel()])),
  ];

  if (stored) {
    steps.push(makeStep(3, "derive", "展开下标公式",
      `把 k 拆成「前面整行」和「本行内偏移」两段：${offset.before} + ${offset.offset} = ${k}。`,
      view("special_matrix", [matrixPanel(), row("records", derivation), metaPanel({ formula: offset.formula, k })])),
      makeStep(4, "map", "写入压缩数组",
        kind === "symmetric"
          ? `A[${i},${j}] = A[${j},${i}] = ${cellValue}，两个对称元素共用 B[${k}]。`
          : `A[${i},${j}] = ${cellValue} 写进 B[${k}]，两者是矩阵里的同一个元素。`,
        view("special_matrix", [matrixPanel(), row("compressed", array, { focusIndex: k }), metaPanel({ formula: offset.formula, k })]),
        [action("map", "二维下标映射到一维下标", { from: `${i},${j}`, to: k })]));
  } else if (kind !== "tridiagonal") {
    steps.push(makeStep(3, "map", "常量区只占一个位置",
      `整个常量区共用 B[${k}]，里面的常量是 0：A[${i},${j}] = 0，所以它不需要单独占一个压缩位置。`,
      view("special_matrix", [matrixPanel(), row("compressed", array, { focusIndex: k }), metaPanel({ k })]),
      [action("map", "常量元素指向公共常量位置", { from: `${i},${j}`, to: k })]));
  } else {
    steps.push(makeStep(3, "map", "带外元素不进压缩数组",
      `A[${i},${j}] = 0 且不带内存储：压缩数组只保存主对角线带上的 ${array.length} 个元素。`,
      view("special_matrix", [matrixPanel(), row("compressed", array, { focusIndex: null }), metaPanel()])));
  }

  const result = stored ? `B[${k}]=${cellValue}` : (kind === "tridiagonal" ? "不存储（带外为 0）" : `常量 B[${k}]`);
  return makeTrace(req, "特殊矩阵压缩存储映射", `A[${i},${j}]`, result, steps);
}

function simulateGeneralizedHead(req,api){const {makeStep,makeTrace,action}=helpers(api);const data=Array.isArray(req.initial_state.data)?req.initial_state.data:["a",["b","c"]];if(!data.length)return api.makeRuntimeError(req,"求广义表表头","EMPTY_LIST","空表没有表头：广义表至少要有一个元素。",view("generalized_list",[row("list",[])]),"空广义表");const head=data[0];return makeTrace(req,"求广义表表头","广义表",`head=${JSON.stringify(head)}`,[makeStep(1,"init","广义表结构","观察最外层第一个表元素。",view("generalized_list",[row("list",data)])),makeStep(2,"select","指向第一个表元素","表头就是最外层的第一个表元素，不管它是原子还是子表。",view("generalized_list",[row("list",data),row("meta",[],{current:0})]),[action("locate","指向第一个表元素",{target:0})]),makeStep(3,"select","取表头",`最外层第一个元素 ${JSON.stringify(head)} 即表头。`,view("generalized_list",[row("list",data),row("head",head)]),[action("select","选择第一个表元素",{target:0})])]);}

function treeArray(v){if(!Array.isArray(v)||v.length===0)throw new SimulationInputError("EMPTY_TREE","二叉树为空：initialData 需要至少一个结点（空子树用 null 表示）","initialData");if(v.length>31)throw new SimulationInputError("INPUT_TOO_LARGE",`二叉树动画最多演示 31 个结点（5 层完全二叉树），当前 ${v.length} 个`,"initialData");return v.map(x=>x===undefined?null:x);}
function child(arr,i){const l=2*i+1,r=2*i+2;return [l<arr.length&&arr[l]!==null?l:-1,r<arr.length&&arr[r]!==null?r:-1];}
function treeRows(arr,extra={}){const nodes=arr.map((x,i)=>x===null?null:{id:i,label:String(x),index:i}).filter(Boolean),edges=[];for(const n of nodes){const[l,r]=child(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);}return [row("tree",[],{nodes,edges}),row("meta",[],extra)];}
function treeHeight(arr,i=0){if(i<0||i>=arr.length||arr[i]===null)return 0;const[l,r]=child(arr,i);return 1+Math.max(treeHeight(arr,l),treeHeight(arr,r));}
function preorder(arr){const out=[];function f(i){if(i<0||i>=arr.length||arr[i]===null)return;out.push(i);const[l,r]=child(arr,i);f(l);f(r);}f(0);return out;}
function inorder(arr){const out=[];function f(i){if(i<0||i>=arr.length||arr[i]===null)return;const[l,r]=child(arr,i);f(l);out.push(i);f(r);}f(0);return out;}
/* 线索二叉树：线索画成虚线（树面板的 `threads`），`ltag`/`rtag` 落在结点上——
   空孩子指针改指前驱/后继，正是线索树区别于普通二叉树的那两个特征位。 */
function threadAll(arr){const threads=[],tags={};let pre=-1;for(const idx of inorder(arr)){if(child(arr,idx)[0]<0){if(pre>=0)threads.push([idx,pre,"L"]);tags[idx]={...(tags[idx]||{}),ltag:1};}if(pre>=0&&child(arr,pre)[1]<0){threads.push([pre,idx,"R"]);tags[pre]={...(tags[pre]||{}),rtag:1};}pre=idx;}return {threads,tags};}
function threadView(arr,visited,current,threads,tags,extra={}){
  const nodes=arr.map((v,i)=>v===null?null:{id:i,label:String(v),index:i,...(tags[i]||{})}).filter(Boolean);
  const edges=[];
  for(const n of nodes){const[l,r]=child(arr,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);}
  const treeRow=row("tree",[],{nodes,edges});
  treeRow.threads=threads.map((t)=>[t[0],t[1],t[2]]);
  const rows=[treeRow];
  if(visited.length)rows.push(row("visited",visited.map((i)=>arr[i])));
  rows.push(row("meta",[],{currentValue:current>=0?arr[current]:null,currentIndex:current,...extra}));
  return view("tree",rows);
}

function simulateTreeAux(req,api){const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  if(op==="build_extended_preorder"){
    /* 递归建树：`ch = seq[k++]` 一行、读到 `#` 就返回 NULL、否则申请结点再递归建左右子树。 */
    const rawSeq=String(req.params.sequence??"AB##CD###");
    if(rawSeq.length>63)throw new SimulationInputError("INPUT_TOO_LONG",`扩展先序序列长度 ${rawSeq.length} 超过 63 字符上限（对应最多 31 个结点）`,"sequence");
    if(!rawSeq.length)throw new SimulationInputError("EMPTY_SEQUENCE","扩展先序序列不能为空","sequence");
    const seq=rawSeq;const built=[];let k=0,sid=1;
    const treeRowOf=()=>{const nodes=built.map((v,i)=>v===null||v===undefined?null:{id:i,label:String(v),index:i}).filter(Boolean);const edges=[];for(const n of nodes){const[l,r]=child(built,n.index);if(l>=0)edges.push([n.index,l,"L"]);if(r>=0)edges.push([n.index,r,"R"]);}return row("tree",[],{nodes,edges});};
    const at=(current,extra={})=>view("tree",[treeRowOf(),row("meta",[],{operation:op,...(current>=0?{currentValue:built[current],currentIndex:current}:{}),...extra})]);
    const steps=[makeStep(sid++,"init","空二叉树",`按扩展先序序列 ${seq} 递归建树：# 表示空子树。`,at(-1))];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"build",code,code,at(current,extra),act?[act]:[]));
    function rec(index){
      if(k>=seq.length)return;
      const ch=seq[k++];
      step(`ch = seq[k++] → '${ch}'`,-1,{ch,k},null,true);
      if(ch==="#"){step("# → return NULL",-1,{ch:"#",ret:"NULL"},null,true);return;}
      while(built.length<=index)built.push(null);
      built[index]=ch;
      step(`s = malloc(); s->data = '${ch}'`,index,{ch,k},action("insert","递归建立结点",{target:index,value:ch}));
      step("p->lchild = Create()",index,{ch,k,call:"lchild"},null,true);
      rec(2*index+1);
      step("p->rchild = Create()",index,{ch,k,call:"rchild"},null,true);
      rec(2*index+2);
    }
    rec(0);
    if(!built.some((x)=>x!==null))throw new SimulationInputError("EMPTY_TREE","序列中没有任何结点符号（只有 #）：无法建立二叉树","sequence");
    return makeTrace(req,"由扩展先序序列建立二叉树",seq,`结点数=${built.filter((x)=>x!==null).length}`,steps);
  }
  const arr=treeArray(req.initial_state.data);let sid=1;
  const treeRowOf=()=>row("tree",[],{nodes:arr.map((v,i)=>v===null?null:{id:i,label:String(v),index:i}).filter(Boolean),edges:arr.map((v,i)=>v===null?null:i).filter((i)=>i!==null).flatMap((i)=>{const[l,r]=child(arr,i);const out=[];if(l>=0)out.push([i,l,"L"]);if(r>=0)out.push([i,r,"R"]);return out;})});
  /* 后面几个操作（线索求第一个/遍历）共用这一组起点帧与游标。 */
  const steps=[makeStep(sid++,"init","二叉树","观察当前树结构。",view("tree",[treeRowOf(),row("meta",[],{operation:op})]))];
  if(op==="leaf_output"||op==="leaf_count"){
    /* 先序遍历：`if (!p->lchild && !p->rchild)` 一行，是叶子就 `count++` 一行。 */
    const leaves=[];
    const at=(current,extra={})=>view("tree",[treeRowOf(),row("leaves",[...leaves]),row("meta",[],{operation:op,...(current>=0?{currentValue:arr[current],currentIndex:current}:{}),...extra})]);
    const steps=[makeStep(sid++,"init","二叉树","按先序走一遍，看哪些结点没有左右孩子。",at(-1))];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"visit",code,code,at(current,extra),act?[act]:[]));
    let count=0;
    for(const idx of preorder(arr)){
      const [l,r]=child(arr,idx);const isLeaf=l<0&&r<0;
      step(`if (!p->lchild && !p->rchild) → ${isLeaf}`,idx,{leaf:isLeaf?1:0},null,true);
      if(!isLeaf)continue;
      count++;leaves.push(arr[idx]);
      step("count++; 输出 p",idx,{leaf:count},action("visit",`第 ${count} 个叶子：${arr[idx]}`,{value:arr[idx]}));
    }
    step("return count",-1,{leafCount:count});
    return makeTrace(req,op==="leaf_output"?"先序遍历输出叶子结点":"统计二叉树叶子结点数","二叉树",`leafCount=${count}`,steps);
  }
  const heightOf=(i)=>{if(i<0||i>=arr.length||arr[i]===null)return 0;const[l,r]=child(arr,i);return 1+Math.max(heightOf(l),heightOf(r));};
  if(op==="height_postorder"){
    /* 后序：`h = max(h(lchild), h(rchild)) + 1`，自下而上一个一个结点算出来。 */
    const at=(current,extra={})=>view("tree",[treeRowOf(),row("meta",[],{operation:op,...(current>=0?{currentValue:arr[current],currentIndex:current}:{}),...extra})]);
    const steps=[makeStep(sid++,"init","二叉树","自下而上：先算左右子树的高度，再合成本结点的高度。",at(-1))];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"measure",code,code,at(current,extra),act?[act]:[]));
    for(const idx of [...preorder(arr)].reverse()){
      const [l,r]=child(arr,idx);
      const lh=heightOf(l),rh=heightOf(r),h=1+Math.max(lh,rh);
      step(`h(${arr[idx]}) = max(${lh}, ${rh}) + 1 = ${h}`,idx,{lh,rh,h},action("compute","由左右子树高度得出",{value:h}));
    }
    step("return h(root)",0,{height:heightOf(0)});
    return makeTrace(req,"后序遍历求二叉树高度","二叉树",`height=${heightOf(0)}`,steps);
  }
  if(op==="height_preorder"){
    /* 先序：每往下走一层 `depth++`，一路上把最大层数记下来。 */
    const at=(current,extra={})=>view("tree",[treeRowOf(),row("meta",[],{operation:op,...(current>=0?{currentValue:arr[current],currentIndex:current}:{}),...extra})]);
    const steps=[makeStep(sid++,"init","二叉树","从根往下走，每深一层 depth 加一，边走边记最大层数。",at(-1))];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"measure",code,code,at(current,extra),act?[act]:[]));
    let maxDepth=0;
    for(const idx of preorder(arr)){
      const depth=Math.floor(Math.log2(idx+1))+1;
      maxDepth=Math.max(maxDepth,depth);
      step("depth = depth + 1; maxDepth = max(maxDepth, depth)",idx,{depth,maxDepth},action("compute","更新最大层数",{value:maxDepth}));
    }
    step("return maxDepth",0,{height:maxDepth});
    return makeTrace(req,"先序遍历求二叉树高度","二叉树",`height=${maxDepth}`,steps);
  }
  if(op==="thread_first"||op==="thread_traverse"){
    /* 线索树的"一行"就是**沿线索走**：找中序第一个结点 = 一路 `p = p->lchild`；
       找后继 = 有 `rtag` 线索就一步走，否则走到右子树的**最左**。 */
    const {threads,tags}=threadAll(arr);
    const visited=[];
    steps[0]=makeStep(1,"init","线索二叉树","线索已经建好：虚线指向前驱/后继（ltag/rtag 为 1 的那个空指针）。",threadView(arr,visited,-1,threads,tags,{operation:op}));
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"thread",code,code,threadView(arr,visited,current,threads,tags,{operation:op,...extra}),act?[act]:[]));
    if(op==="thread_first"){
      step("p = root",0,null,null,true);
      let p=0;
      for(;;){
        const stop=Number((tags[p]||{}).ltag)===1;
        step(`if (p->ltag == 0) → ${!stop}`,p,{ltag:stop?1:0},null,true);
        if(stop)break;
        const next=child(arr,p)[0];
        step("p = p->lchild",next,{},action("move","沿左孩子往下走",{value:next>=0?arr[next]:null}),true);
        if(next<0)break;
        p=next;
      }
      step("return p",0,{p:arr[p]});
      return makeTrace(req,"中序线索树求第一个结点","线索树",String(arr[p]),steps);
    }
    let p=inorder(arr)[0];
    step(`p = ${arr[p]}`,p,{p:arr[p]},null,true);
    while(p>=0){
      visited.push(p);
      step("visit(p)",p,{p:arr[p]},action("visit","访问结点",{value:arr[p]}));
      const threaded=Number((tags[p]||{}).rtag)===1;
      step(`if (p->rtag == 1) → ${threaded}`,p,{rtag:threaded?1:0},null,true);
      if(threaded){
        const t=threads.find((x)=>x[0]===p&&x[2]==="R");
        const next=t?t[1]:-1;
        step("p = p->rchild",next,{p:next>=0?arr[next]:"NULL"},action("move","沿后继线索走一步",{value:next>=0?arr[next]:null}),true);
        p=next;
        continue;
      }
      let q=child(arr,p)[1];
      step("q = p->rchild",q,{},null,true);
      if(q<0){p=-1;continue;}
      for(;;){
        const stop=Number((tags[q]||{}).ltag)===1;
        step(`if (q->ltag == 0) → ${!stop}`,q,{ltag:stop?1:0},null,true);
        if(stop)break;
        const gone=child(arr,q)[0];
        step("q = q->lchild",gone,{},action("move","沿左链走到最左",{value:gone>=0?arr[gone]:null}),true);
        if(gone<0)break;
        q=gone;
      }
      step("p = q",q,{p:q>=0?arr[q]:"NULL"},action("move","p 移到后继",{value:q>=0?arr[q]:null}),true);
      p=q;
    }
    step("p == NULL",-1,{p:"NULL"});
    return makeTrace(req,"遍历中序线索二叉树","线索树",visited.map((i)=>arr[i]).join(" "),steps);
  }
  if(op==="path_to_node"){
    /* 从目标结点**往上**走：`p = parent(p)` 一行一格，走回根就是路径。 */
    const target=String(req.params.target??arr[arr.length-1]);
    const idx=arr.findIndex((x)=>String(x)===target);
    if(idx<0)return api.makeRuntimeError(req,"根到指定结点路径","TARGET_NOT_FOUND",`目标 ${target} 不在这棵二叉树中（结点：${arr.filter((x)=>x!==null&&x!==undefined).join("、")}），请检查 target 参数。`,view("tree",[treeRowOf(),row("meta",[],{operation:op,target})]),`target=${target}`);
    const path=[];
    const at=(current,extra={})=>view("tree",[treeRowOf(),...(path.length?[row("path",path.map((i)=>arr[i]).join(" → "))]:[]),row("meta",[],{operation:op,target,...(current>=0?{currentValue:arr[current],currentIndex:current}:{}),...extra})]);
    const steps=[makeStep(sid++,"init","二叉树",`求根到 ${target} 的路径：从目标结点往上走到根。`,at(-1))];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"path",code,code,at(current,extra),act?[act]:[]));
    let p=idx;
    step(`p = ${target}`,p,{},action("locate","定位到目标结点",{value:target}),true);
    for(;;){
      path.unshift(p);
      step(`path.push(p)`,p,{p:arr[p],len:path.length},action("move",`路径上加入 ${arr[p]}`,{value:arr[p]}));
      if(p===0)break;
      p=Math.floor((p-1)/2);
      step("p = parent(p)",p,{p:arr[p]},action("move",`上溯到父结点 ${arr[p]}`,{value:arr[p]}),true);
    }
    step("return path",0,{path:path.map((i)=>arr[i]).join("→")});
    return makeTrace(req,"根到指定结点路径",`target=${target}`,path.map((i)=>arr[i]).join("→"),steps);
  }
  const other=treeArray(req.params.other);const max=Math.max(arr.length,other.length);
  /* 结构相似：逐个位置看"是不是都空 / 是不是都非空"，只要有一边空一边不空就结束。 */
  let same=true;let k=0;
  const cmpAt=(i,extra)=>view("tree_compare",[row("left",arr),row("right",other),row("meta",[],{operation:op,index:i,...extra})]);
  const stepsSim=[makeStep(sid++,"init","两棵二叉树","逐个位置比较：要么都空，要么都非空。",cmpAt(-1,{}))];
  const step=(code,i,extra,act,line)=>stepsSim.push(makeStep(sid++,line?"line":"compare",code,code,cmpAt(i,extra),act?[act]:[]));
  for(k=0;k<max;k++){
    const a=arr[k]??null,b=other[k]??null;
    const bothNull=a===null&&b===null;
    step(`if (a[${k}] == NULL && b[${k}] == NULL) → ${bothNull}`,k,{都空:bothNull?1:0},null,true);
    if(bothNull)continue;
    const bothPresent=a!==null&&b!==null;
    step(`if (a[${k}] && b[${k}]) → ${bothPresent}`,k,{都非空:bothPresent?1:0},action("compare","比较对应结点",{target:k}),true);
    if(!bothPresent){same=false;break;}
  }
  step("return "+(same?"1":"0"),-1,{结论:same?"结构相似":"结构不同"});
  return makeTrace(req,"判断两棵二叉树结构相似","两棵树",same?"结构相似":"结构不同",stepsSim);
}
function forestTreesOf(groups){return groups.map((g)=>({root:String(g[0]),children:g.slice(1).map(String)}));}
function forestPreorder(trees){const out=[];trees.forEach((t,ti)=>{out.push({label:t.root,ti,index:0});t.children.forEach((c,i)=>out.push({label:c,ti,index:i+1}));});return out;}
function forestBtRow(bt){return row("tree",[],{nodes:bt.nodes.map((l)=>({id:l,label:l,key:l})),edges:bt.edges.map((e)=>[e[0],e[1],e[2]])});}
function forestDotRow(trees){return row("trees",trees.map((t)=>[t.root,...t.children]));}

function simulateForest(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  const groups=Array.isArray(req.params.trees)?req.params.trees:[["A","B","C"],["D","E"],["F"]];
  if(!groups.length)throw new SimulationInputError("EMPTY_FOREST","森林至少需要一棵树（trees 为空）","trees");
  if(groups.length>8)throw new SimulationInputError("INPUT_TOO_LARGE",`森林动画最多演示 8 棵树，当前 ${groups.length} 棵`,"trees");
  groups.forEach((g,i)=>{if(!Array.isArray(g)||!g.length)throw new SimulationInputError("EMPTY_TREE",`第 ${i+1} 棵树为空：每棵树至少要有一个结点`,`trees[${i}]`);if(g.length>10)throw new SimulationInputError("INPUT_TOO_LARGE",`第 ${i+1} 棵树有 ${g.length} 个结点，超过单棵 10 个的上限`,`trees[${i}]`);});
  const trees=forestTreesOf(groups);
  const order=forestPreorder(trees);
  const nextRootOf=(ti)=>ti+1<trees.length?trees[ti+1].root:undefined;
  /* 每个结点在二叉树里的两个孩子：第一个孩子作左孩子；同层下一个结点作右孩子（根之间也按兄弟看待）。 */
  const childOf=(item)=>(item.index===0?trees[item.ti].children[0]:undefined);
  const siblingOf=(item)=>(item.index===0?nextRootOf(item.ti):trees[item.ti].children[item.index]);
  let sid=1;
  const bt={nodes:[],edges:[]};
  const buildBt=()=>{for(const item of order){
    const label=item.label;
    if(!bt.nodes.includes(label))bt.nodes.push(label);
    const l=childOf(item),r=siblingOf(item);
    if(l!==undefined&&!bt.nodes.includes(l))bt.nodes.push(l);
    if(l!==undefined)bt.edges.push([label,l,"L"]);
    if(r!==undefined&&!bt.nodes.includes(r))bt.nodes.push(r);
    if(r!==undefined)bt.edges.push([label,r,"R"]);
  }};
  const rowsWith=(extraRows,extra)=>view("forest",[forestDotRow(trees),forestBtRow(bt),...extraRows,row("meta",[],{operation:op,...extra})]);
  const steps=[makeStep(sid++,"init",op==="to_binary_tree"?"森林":"孩子-兄弟二叉树",
    op==="to_binary_tree"?`${trees.length} 棵树：${trees.map((t)=>[t.root,...t.children].join("")).join(" | ")}。按"第一孩子作左孩子、下一兄弟作右孩子"转换。`:"这棵二叉树就是森林的孩子-兄弟表示：右链上的每个结点都是一棵新树的根。",
    rowsWith([],{}))];
  const snap=(code,extraRows,extra,act,line)=>steps.push(makeStep(sid++,line?"line":op,code,code,rowsWith(extraRows,extra),act?[act]:[]));

  if(op==="to_binary_tree"){
    for(const item of order){
      const label=item.label;
      if(!bt.nodes.includes(label))bt.nodes.push(label);
      snap(`p = ${label}`,[],{current:label},null,true);
      const l=childOf(item),r=siblingOf(item);
      bt.edges=bt.edges.filter((e)=>!(e[0]===label&&e[2]==="L"));
      if(l!==undefined){if(!bt.nodes.includes(l))bt.nodes.push(l);bt.edges.push([label,l,"L"]);}
      snap(`p->lchild = ${l===undefined?"NULL":l}`,[],{current:label,slot:"lchild"},action("link","第一个孩子作左孩子",{value:l??null}));
      bt.edges=bt.edges.filter((e)=>!(e[0]===label&&e[2]==="R"));
      if(r!==undefined){if(!bt.nodes.includes(r))bt.nodes.push(r);bt.edges.push([label,r,"R"]);}
      snap(`p->rchild = ${r===undefined?"NULL":r}`,[],{current:label,slot:"rchild",hook:r??"NULL"},action("link","下一个兄弟作右孩子",{value:r??null}));
    }
    return makeTrace(req,"森林转换为二叉树","孩子兄弟关系",`二叉树 ${bt.nodes.length} 个结点`,steps);
  }

  /* 反方向：先把孩子-兄弟二叉树摆出来当起点，再沿右链把每一棵树的根剪开。 */
  buildBt();
  steps[0]=makeStep(1,"init","孩子-兄弟二叉树",`右链上的每个结点都是下一棵树的根：${trees.map((t)=>t.root).join(" → ")}。`,rowsWith([],{}));
  const roots=[trees[0].root];
  const cut=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":op,code,code,rowsWith([row("roots",roots)],extra),act?[act]:[]));
  cut("p = root",{current:roots[0]},null,true);
  let p=roots[0];
  for(;;){
    const q=(bt.edges.find((e)=>e[0]===p&&e[2]==="R")||[])[1];
    cut("q = p->rchild",{current:q??p,q:q??"NULL"},null,true);
    if(q===undefined)break;
    bt.edges=bt.edges.filter((e)=>!(e[0]===p&&e[2]==="R"));
    roots.push(q);
    cut("p->rchild = NULL",{current:p,slot:"rchild",cut:q},action("split",`断开 ${p} → ${q}：${q} 成为下一棵树的根`,{from:p,to:q}));
    cut("p = q",{current:q},null,true);
    p=q;
  }
  cut("p == NULL",{current:p,done:roots.length});
  return makeTrace(req,"二叉树还原为森林","孩子兄弟关系",`还原出 ${roots.length} 棵树`,steps);
}

function simulateUnionFindAux(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  const n=int(req.params.size,6,2,30,"size");
  if(op==="initialize"){
    /* 先把 n 个单元开出来（还不是集合），再**一格一格**写 -1——init 从 1 帧变成 n+1 帧，
       每一帧数组都真的变一格。逐格帧标 line：精简版会把它压成"一步到位"。 */
    const parent=Array(n).fill(null);
    let sid=1;
    const at=(extra={})=>view("union_find",[row("parent",parent),row("meta",[],{operation:op,size:n,...extra})]);
    const steps=[makeStep(sid++,"init","parent[n]",`先开出 ${n} 个单元：每个元素各自成一个集合。`,at())];
    for(let i=0;i<n;i++){
      parent[i]=-1;
      steps.push(makeStep(sid++,"line",`parent[${i}] = -1`,`parent[${i}] = -1`,at({current:i}),[action("assign","置为 -1（自己就是根）",{target:i,value:-1})]));
    }
    return makeTrace(req,"并查集初始化",`n=${n}`,`parent=[${parent.join(",")}]`,steps);
  }
  const rawParent=req.params.parent!==undefined?req.params.parent:req.initial_state.data;
  const provided=normalizeParentArray(rawParent);
  const parent=provided??[-6,0,0,2,3,4];
  const size=parent.length;
  const x=int(req.params.element,Math.min(5,size-1),0,size-1,"element");
  let sid=1;
  const at=(extra={})=>view("union_find",[row("parent",parent),row("meta",[],{operation:op,...extra})]);
  const steps=[makeStep(sid++,"init","parent[]",`parent=[${parent.join(",")}]（负值 = 根，其绝对值为集合大小）。先沿 parent 找根，再把沿途结点直接连到根。`,at())];
  const snap=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"compress",code,code,at(extra),act?[act]:[]));
  /* 第一段：走到根 */
  snap(`root = ${x}`,{current:x,root:x},action("assign","先假定自己是根",{value:x}),true);
  let root=x;
  for(;;){
    const up=parent[root]>=0;
    snap(`while (parent[root] >= 0) → ${up}`,{current:root,root,"parent[root]":parent[root]},null,true);
    if(!up)break;
    const next=parent[root];
    snap("root = parent[root]",{current:next,root:next},action("move",`root 上移到 ${next}`,{from:root,to:next}),true);
    root=next;
  }
  /* 第二段：从出发点开始，把沿途每一个结点直接挂到根上 */
  snap(`x = ${x}`,{current:x,x,root},action("assign","回到出发点做压缩",{value:x}),true);
  let cur=x;
  for(;;){
    const more=cur!==root;
    snap(`while (x != root) → ${more}`,{current:cur,x:cur,root,"parent[x]":parent[cur]},null,true);
    if(!more)break;
    const t=parent[cur];
    snap("t = parent[x]",{current:cur,x:cur,root,t},null,true);
    parent[cur]=root;
    snap("parent[x] = root",{current:cur,x:cur,root,t,"parent[x]":root},action("link",`${cur} 直接连到根 ${root}`,{from:cur,to:root}),true);
    snap("x = t",{current:t,x:t,root},action("move","x 移到原来的父亲",{value:t}),true);
    cur=t;
  }
  snap("return root",{current:root,root,"return":root});
  return makeTrace(req,"并查集路径压缩查找",`element=${x}`,`root=${root}`,steps);
}

function graphData(req,defaults={}){return normalizeGraphSpec(req,defaults);}
function graphRows(g,extra={}){return[row("graph",[],{nodes:g.nodes.map((x,i)=>({id:x,label:x,index:i})),edges:g.edges}),row("meta",[],{directed:g.directed,...extra})];}
function simulateGraphAux(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  // compute_indegree：入度是有向图概念，缺省按有向图处理，显式 directed=false 直接报错；
  // 建图/遍历操作缺省按无向图；连通分量无论 directed 均按无向边处理。
  const g=graphData(req,{directed:op==="compute_indegree"});
  if(op==="build_cross_list"&&req.params.directed===false)throw new SimulationInputError("INVALID_PARAM","十字链表用于存储有向图：directed 不能为 false","directed");
  if(op==="compute_indegree"&&!g.directed)return api.makeRuntimeError(req,"计算各顶点入度","UNDIRECTED_INDEGREE","入度是针对有向图的概念：请设置 directed=true，或改用求连通分量等无向图算法。",view("graph",graphRows(g,{operation:op})),"无向图");
  const n=g.nodes.length,idxOf=(v)=>g.nodes.indexOf(v);
  let sid=1;
  const vertexNodes=()=>g.nodes.map((x)=>({id:x,label:x}));
  const edgeGraphRow=(edges)=>row("graph",[],{nodes:vertexNodes(),edges,directed:g.directed});
  const steps=[makeStep(sid++,"init","图结构",`${n} 个顶点，${g.edges.length} 条边；${g.directed?"有向图":"无向图"}。`,view("graph",graphRows(g,{operation:op})))];
  const snap=(code,rows,extra,act,line)=>steps.push(makeStep(sid++,line?"line":op,code,code,view("graph",[...rows,row("meta",[],{operation:op,directed:g.directed,...extra})]),act?[act]:[]));

  if(op.startsWith("build_")){
    if(!g.edges.length)throw new SimulationInputError("EMPTY_EDGES","建图演示至少需要一条边（edges 为空）","edges");
    if(op==="build_adjacency_matrix"){
      /* 邻接矩阵：一条边写一格，无向图连对称格一起写——每写一格矩阵面板都变。 */
      const m=Array.from({length:n},()=>Array(n).fill(null));
      const cells=()=>m.map((r)=>r.map((x)=>x===null?"∞":x));
      for(const [u,v,w] of g.edges){
        const i=idxOf(u),j=idxOf(v);
        m[i][j]=w;
        snap(`A[${i}][${j}] = ${w}`,[edgeGraphRow([[u,v]]),row("matrix",cells(),{focusCell:[i,j]})],{edge:[u,v]},action("link","写入矩阵单元",{from:u,to:v,value:w}));
        if(!g.directed){
          m[j][i]=w;
          snap(`A[${j}][${i}] = ${w}`,[edgeGraphRow([[u,v]]),row("matrix",cells(),{focusCell:[j,i]})],{edge:[u,v],symmetric:true},action("link","无向图同时写对称单元",{from:v,to:u,value:w}));
        }
      }
      return makeTrace(req,"建立图的邻接矩阵","边集合","建立完成",steps);
    }
    if(op==="build_adjacency_list"){
      /* 邻接表：`p = malloc(); p->adjvex = j; p->next = adj[i].first` 一行、`adj[i].first = p` 一行。 */
      const adj=g.nodes.map(()=>[]);
      const listRow=()=>row("adj",adj.map((l)=>l.length?l.join(" → "):"∧"));
      let k=0;
      for(const [u,v] of g.edges){
        const i=idxOf(u),j=idxOf(v),id=`e${++k}`;
        snap(`p = malloc(); p->adjvex = ${j}; p->next = adj[${i}].first`,[edgeGraphRow([[u,v]]),listRow()],{p:id,edge:[u,v]},null,true);
        adj[i].unshift(j);
        snap(`adj[${i}].first = p`,[edgeGraphRow([[u,v]]),listRow()],{p:id,edge:[u,v]},action("link",`${u} 的邻接链头插 ${v}`,{from:u,to:v}));
        if(!g.directed){
          const id2=`e${++k}`;
          snap(`q = malloc(); q->adjvex = ${i}; q->next = adj[${j}].first`,[edgeGraphRow([[u,v]]),listRow()],{p:id2,edge:[u,v],symmetric:true},null,true);
          adj[j].unshift(i);
          snap(`adj[${j}].first = q`,[edgeGraphRow([[u,v]]),listRow()],{p:id2,edge:[u,v],symmetric:true},action("link",`${v} 的邻接链头插 ${u}`,{from:v,to:u}));
        }
      }
      return makeTrace(req,"建立图的邻接表","边集合","建立完成",steps);
    }
    /* 十字链表：每个边结点同时挂进**出边链**（tail）和**入边链**（head）。 */
    const out=g.nodes.map(()=>[]),inn=g.nodes.map(()=>[]);
    const outRow=()=>row("out",out.map((l)=>l.length?l.join(" → "):"∧"));
    const inRow=()=>row("in",inn.map((l)=>l.length?l.join(" → "):"∧"));
    let k=0;
    for(const [u,v] of g.edges){
      const i=idxOf(u),j=idxOf(v),id=`e${++k}`;
      snap(`p = malloc(); p->tailvex = ${i}; p->headvex = ${j}`,[edgeGraphRow([[u,v]]),outRow(),inRow()],{p:id,edge:[u,v]},null,true);
      out[i].unshift(`${u}→${v}`);
      snap(`p->tlink = tail[${i}]; tail[${i}] = p`,[edgeGraphRow([[u,v]]),outRow(),inRow()],{p:id,edge:[u,v]},action("link",`接进 ${u} 的出边链`,{from:u,to:v}));
      inn[j].unshift(`${u}→${v}`);
      snap(`p->hlink = head[${j}]; head[${j}] = p`,[edgeGraphRow([[u,v]]),outRow(),inRow()],{p:id,edge:[u,v]},action("link",`接进 ${v} 的入边链`,{from:u,to:v}));
    }
    return makeTrace(req,"建立有向图十字链表","边集合","建立完成",steps);
  }

  if(op==="compute_indegree"){
    /* 逐边扫描：`indegree[v] = indegree[v] + 1`，每扫一条边入度面板就变一格。 */
    const indegree=Object.fromEntries(g.nodes.map((x)=>[x,0]));
    for(const [u,v] of g.edges){
      if(!(v in indegree))continue;
      indegree[v]+=1;
      snap(`indegree[${v}] = indegree[${v}] + 1`,[...graphRows(g,{operation:op,edge:[u,v]}),row("indegree",g.nodes.map((x)=>({vertex:x,value:indegree[x]})),{focusIndex:g.nodes.indexOf(v)})],{edge:[u,v],v:indegree[v]},action("count","终点入度加 1",{target:v,value:indegree[v]}));
    }
    return makeTrace(req,"计算各顶点入度","有向图",g.nodes.map((x)=>`${x}:${indegree[x]}`).join(", "),steps);
  }

  if(op==="dfs_nonrecursive"){
    /* 显式栈：`s.push(邻居)` 一行、`v = s.pop()` 一行、`visited[v] = 1` 一行。 */
    const start=String(req.params.start??g.nodes[0]);
    requireVertex(g,start,"起点 start");
    const adj=new Map(g.nodes.map((x)=>[x,[]]));
    for(const [u,v] of g.edges){adj.get(u)?.push(v);if(!g.directed)adj.get(v)?.push(u);}
    const stack=[start],seen=new Set(),order=[];
    const rows=(current,extra={})=>[...graphRows(g,{operation:op,current,visited:[...order]}),row("stack",[...stack],{focusIndex:stack.length-1}),row("meta",[],{operation:op,directed:g.directed,...extra})];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"visit",code,code,view("graph",rows(current,extra)),act?[act]:[]));
    step(`s.push(${start})`,start,{},action("push","起点入栈",{value:start}),true);
    while(stack.length){
      step("while (!s.empty()) → true",stack[stack.length-1],{s:stack.join(",")},null,true);
      const u=stack.pop();
      step("v = s.pop()",u,{v:u},action("move","退栈",{value:u}),true);
      const already=seen.has(u);
      step(`if (visited[${u}]) → ${already}`,u,{v:u,visited:already?1:0},null,true);
      if(already)continue;
      seen.add(u);order.push(u);
      step(`visited[${u}] = 1`,u,{v:u,visited:order.length},action("visit",`访问 ${u}`,{value:u}));
      const ns=[...(adj.get(u)||[])].reverse();
      for(const v of ns){
        if(seen.has(v))continue;
        stack.push(v);
        step(`s.push(${v})`,u,{v:u,pushed:v},action("push",`${v} 入栈`,{value:v}),true);
      }
    }
    step("while (!s.empty()) → false",null,{s:"(空)"});
    return makeTrace(req,"非递归深度优先搜索",`start=${start}`,order.join("→"),steps);
  }

  if(op==="connected_components"){
    /* 从每个未访问顶点启动一次遍历；`component++`、`u = q.pop()`、`visited[u] = 1` 各一行。 */
    const adj=new Map(g.nodes.map((x)=>[x,[]]));
    for(const [u,v] of g.edges){adj.get(u)?.push(v);adj.get(v)?.push(u);}
    const seen=new Set();const members=[];let component=0;
    const rows=(current,extra={})=>[...graphRows(g,{operation:op,visited:[...seen],current}),row("members",members.join(" ")),row("meta",[],{operation:op,component,directed:g.directed,...extra})];
    const step=(code,current,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"component",code,code,view("graph",rows(current,extra)),act?[act]:[]));
    for(const start of g.nodes){
      const inside=seen.has(start);
      step(`if (visited[${start}]) → ${inside}`,start,{v:start,visited:inside?1:0},null,true);
      if(inside)continue;
      component++;
      step("component++",start,{component},action("mark",`第 ${component} 个连通分量`,{value:component}),true);
      const q=[start];
      members.length=0;
      step(`q.push(${start})`,start,{q:q.join(",")},action("push","起点入队",{value:start}),true);
      while(q.length){
        const u=q.shift();
        step("u = q.pop()",u,{q:q.join(",")},null,true);
        if(seen.has(u)){step(`if (visited[${u}]) → true`,u,{visited:1},null,true);continue;}
        seen.add(u);members.push(u);
        step(`visited[${u}] = 1`,u,{component,members:members.join("")},action("visit",`${u} 属于第 ${component} 个分量`,{value:u}));
        for(const v of adj.get(u)||[]){
          if(seen.has(v))continue;
          q.push(v);
          step(`q.push(${v})`,u,{q:q.join(","),pushed:v},action("push",`${v} 入队`,{value:v}),true);
        }
      }
    }
    return makeTrace(req,"求图的连通分量","图",`components=${component}`,steps);
  }
  throw new SimulationInputError("UNSUPPORTED_OPERATION",`图辅助演示不支持操作 ${op}`,"operation");
}

function bstInsert(root,key){if(!root)return {key,left:null,right:null};if(key<root.key)root.left=bstInsert(root.left,key);else if(key>root.key)root.right=bstInsert(root.right,key);return root;}
function bstRows(root,extra={}){const nodes=[],edges=[];let id=0;function walk(n,parent=null,side=""){if(!n)return;const my=id++;nodes.push({id:my,label:String(n.key),key:n.key});if(parent!==null)edges.push([parent,my,side]);walk(n.left,my,"L");walk(n.right,my,"R");}walk(root);return[row("tree",[],{nodes,edges}),row("meta",[],extra)];}
/* 二叉排序树的建树与查找（辅助引擎这一侧）：
   建树 = 逐个关键字走一遍比较路径再挂到空位置；查找 = 比较一行、进左/右子树一行。 */
function simulateBSTAux(req,api){
  const {makeStep,makeTrace,action}=helpers(api);const op=req.operation;
  const vals=numArray(req.params.values??req.initial_state.data,[45,24,53,12,28,90]);
  if(req.params.key!==undefined&&!Number.isFinite(Number(req.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(req.params.key)} 必须是数字`,"key");
  let root=null,sid=1;
  if(op==="create"){
    if(!vals.length)throw new SimulationInputError("EMPTY_INPUT","关键字列表为空：无法创建二叉排序树","values");
    const steps=[makeStep(sid++,"init","空二叉排序树","依次插入输入关键字，每个都先按大小关系走到空位置。",view("tree",bstRows(null,{operation:op})))];
    const step=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"insert",code,code,view("tree",bstRows(root,{operation:op,...extra})),act?[act]:[]));
    for(const v of vals){
      let n=root,parent=null;
      while(n){
        const goLeft=v<n.key;
        step(`if (${v} < ${n.key}) → ${goLeft}`,{current:n.key,insert:v},null,true);
        const next=goLeft?n.left:n.right;
        if(next)step(`p = p->${goLeft?"left":"right"}`,{current:next.key,insert:v,p:next.key},null,true);
        parent=n;n=next;
      }
      step(`s = malloc(); s->data = ${v}`,{current:parent?parent.key:null,insert:v,p:parent?parent.key:"NULL"},action("allocate","申请新结点",{value:v}),true);
      root=bstInsert(root,v);
      step(parent===null?"root = s":`p->${v<parent.key?"left":"right"} = s`,{current:v,insert:v},action("insert","挂到空位置上",{value:v}));
    }
    return makeTrace(req,"创建二叉排序树",`keys=${vals.join(",")}`,"创建完成",steps);
  }
  for(const v of vals)root=bstInsert(root,v);
  const key=Number(req.params.key??28);
  const steps=[makeStep(sid++,"init","二叉排序树","从根开始，利用有序性一路比较。",view("tree",bstRows(root,{operation:op,key})))];
  const step=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":op==="search_recursive"?"call":"compare",code,code,view("tree",bstRows(root,{operation:op,key,...extra})),act?[act]:[]));
  let cur=root;
  while(cur){
    const hit=key===cur.key;
    step(`if (p->key == ${key}) → ${hit}`,{current:cur.key},action("compare","比较关键字",{value:cur.key}),true);
    if(hit){
      step("return p",{current:cur.key,found:key},action("visit",`找到 ${key}`,{value:key}));
      return makeTrace(req,op==="search_recursive"?"递归查找二叉排序树":"非递归查找二叉排序树",`key=${key}`,`found=${key}`,steps);
    }
    const goLeft=key<cur.key;
    const next=goLeft?cur.left:cur.right;
    step(op==="search_recursive"?`SearchBST(p->${goLeft?"left":"right"}, ${key})`:`p = p->${goLeft?"left":"right"}`,{current:next?next.key:null},action("move",`进入${goLeft?"左":"右"}子树`,{value:next?next.key:null}),true);
    cur=next;
  }
  step("if (p == NULL) return NULL",{current:null,found:"NULL"},action("miss","到达空子树，查找失败",{value:null}));
  return makeTrace(req,op==="search_recursive"?"递归查找二叉排序树":"非递归查找二叉排序树",`key=${key}`,"not found",steps);
}
function avlBuildFromKeys(keys){let root=null;for(const k of keys){root=(function ins(n){if(!n)return{key:k,left:null,right:null};if(k<n.key)n.left=ins(n.left);else if(k>n.key)n.right=ins(n.right);return n;})(root);}return root;}
function avlTreeRow(roots){const nodes=[],edges=[];for(const r of roots)(function walk(n,parentId,side){if(!n)return;const my=String(n.key);nodes.push({id:my,label:my,key:n.key});if(parentId!==null)edges.push([parentId,my,side]);walk(n.left,my,"L");walk(n.right,my,"R");})(r,null,"");return row("tree",[],{nodes,edges});}

function simulateAVLRotation(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  const presets={rotate_ll:[30,20,10],rotate_rr:[10,20,30],rotate_lr:[30,10,20],rotate_rl:[10,30,20]};
  /* 旋转演示的 required 为空，resolver 不回填 demo 参数：空 data 视为未提供，回落到教材标准失衡序列 */
  const given=Array.isArray(req.initial_state.data)&&req.initial_state.data.length?numArray(req.initial_state.data):presets[op].slice();
  if(given.length<3)throw new SimulationInputError("INVALID_ROTATION_INPUT",`AVL 旋转演示需要 3 个关键字的失衡局部（如 ${JSON.stringify(presets[op])}），当前只有 ${given.length} 个`,"initialData");
  const keys=given.slice(0,3);
  const type={rotate_ll:"LL",rotate_rr:"RR",rotate_lr:"LR",rotate_rl:"RL"}[op];
  const title={rotate_ll:"LL 型右旋",rotate_rr:"RR 型左旋",rotate_lr:"LR 型先左后右旋",rotate_rl:"RL 型先右后左旋"}[op];
  const P=avlBuildFromKeys(keys);
  /* 画面上当前有几棵"根"：旋转中途会断开，断开的每一支各算一棵。 */
  const pieces=[P];
  const at=(extra={})=>view("tree",[avlTreeRow(pieces.slice()),row("meta",[],{operation:op,root:pieces.map(r=>String(r.key)).join("、"),...extra})]);
  let sid=1;const steps=[];
  /* title 与 note 都写那行代码——渲染器据此排成等宽字体；`phase: "line"` 的是纯指针记账，
     精简版会把这些帧压掉，留给"真的改了哪条链"的那几帧。 */
  const line=(text,extra,act)=>steps.push(makeStep(sid++,"line",text,text,at(extra),act?[act]:[]));
  const hold=(text,extra,act)=>steps.push(makeStep(sid++,"rotate",text,text,at(extra),act?[act]:[]));
  const forest=(...roots)=>{pieces.length=0;for(const r of roots)pieces.push(r);};

  steps.push(makeStep(sid++,"init","p = root",`${type} 型失衡：最小不平衡子树的根是 ${P.key}，它的${type==="RR"||type==="RL"?"右":"左"}子树比另一侧高 2。`,at()));

  if(op==="rotate_ll"||op==="rotate_rr"){
    /* 单旋：q 顶上来、p 降下去。LL 是右旋（q 取左孩子），RR 是左旋（q 取右孩子）。 */
    const right=op==="rotate_ll";
    const outer=right?"left":"right";
    const inner=right?"right":"left";
    const q=P[outer];
    line(`q = p->${outer}`,{current:String(q.key)},action("move",`q 指向 p 的${right?"左":"右"}孩子`,{value:q.key}));
    const moved=q[inner];
    P[outer]=moved;
    forest(P,q);
    hold(`p->${outer} = q->${inner}`,{current:String(P.key)},action("link",`p 的${right?"左":"右"}子树改挂 q 的${inner==="right"?"右":"左"}子树`,{value:moved?moved.key:null}));
    q[inner]=P;
    forest(q);
    hold(`q->${inner} = p`,{current:String(P.key)},action("rotate",`p 降为 q 的${inner==="right"?"右":"左"}孩子`,{value:P.key}));
    line("p = q",{current:String(q.key)},action("move","q 成为这棵子树的根",{value:q.key}));
  } else {
    /* 双旋：先对 p 的孩子做一次单旋，再对 p 做一次——中间那次单旋真的出现在画面上。 */
    const right=op==="rotate_lr";
    const outer=right?"left":"right";
    const inner=right?"right":"left";
    const q=P[outer],r=q[inner];
    line(`q = p->${outer}`,{current:String(q.key)},action("move",`q 指向 p 的${right?"左":"右"}孩子`,{value:q.key}));
    line(`r = q->${inner}`,{current:String(r.key)},action("move",`r 指向 q 的${inner==="right"?"右":"左"}孩子`,{value:r.key}));
    const moved1=r[outer];
    q[inner]=moved1;
    forest(P,r);
    hold(`q->${inner} = r->${outer}`,{current:String(q.key)},action("link",`q 的${inner==="right"?"右":"左"}子树改挂 r 的${outer==="right"?"右":"左"}子树`,{value:moved1?moved1.key:null}));
    P[outer]=r;
    forest(P,q);
    hold(`p->${outer} = r`,{current:String(P.key)},action("link",`p 的${right?"左":"右"}孩子换成 r`,{value:r.key}));
    r[outer]=q;
    forest(P);
    hold(`r->${outer} = q`,{current:String(P.key)},action("link",`q 降为 r 的${outer==="right"?"右":"左"}孩子`,{value:q.key}));
    /* 到这里 p 的孩子已经是 r，剩下与单旋完全一样。 */
    const moved2=r[inner];
    P[outer]=moved2;
    forest(P,r);
    hold(`p->${outer} = r->${inner}`,{current:String(P.key)},action("link",`p 的${right?"左":"右"}子树改挂 r 的${inner==="right"?"右":"左"}子树`,{value:moved2?moved2.key:null}));
    r[inner]=P;
    forest(r);
    hold(`r->${inner} = p`,{current:String(P.key)},action("rotate",`p 降为 r 的${inner==="right"?"右":"左"}孩子`,{value:P.key}));
    line("p = r",{current:String(r.key)},action("move","r 成为这棵子树的根",{value:r.key}));
  }
  return makeTrace(req,`AVL ${title}`,keys.join(","),`局部根=${pieces[0].key}`,steps);
}

/* B 树结点内的三个原子操作：定位序号、腾位置插入、分裂提升。
   一帧 = 一行：`i++` 一格、`key[j] = key[j-1]` 挪一格、`mid = n / 2` 一行。 */
/* B 树结点内的三个原子操作：定位序号、腾位置插入、分裂提升中间关键字。
   一帧 = 一行：`i++` 一格、`key[j] = key[j-1]` 挪一格、`mid = n / 2` 一行。
   代码行里写**变量名**（`key[i]`），当前下标/当前值走 meta 胶囊——不然"代码"每帧都在变。 */
function simulateBTreeAux(req,api){
  const {makeStep,makeTrace,action}=helpers(api),op=req.operation;
  const keys=numArray(req.initial_state.data,[10,20,30,40]);
  if(!keys.length)throw new SimulationInputError("EMPTY_TREE","B 树结点为空：请在 initialData 中提供结点关键字","initialData");
  if(req.params.key!==undefined&&!Number.isFinite(Number(req.params.key)))throw new SimulationInputError("INVALID_PARAM",`参数 key=${JSON.stringify(req.params.key)} 必须是数字`,"key");
  const key=Number(req.params.key??25),order=int(req.params.order,4,3,8,"order");
  let sid=1;
  if(op==="locate_position"){
    /* 找"不大于 k 的最大关键字序号"：`key[i] <= k` 就一直右移。 */
    const arr=[...keys];
    const at=(extra={})=>view("btree",[row("node",arr),row("meta",[],{operation:op,key,order,...extra})]);
    const steps=[makeStep(sid++,"init","B 树结点",`当前结点：[${arr.join(", ")}]，m=${order}；找不大于 k=${key} 的最大关键字序号。`,at())];
    const step=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"compare",code,code,at(extra),act?[act]:[]));
    step("i = 0",{current:0,i:0},null,true);
    let i=0;
    for(;;){
      const cont=i<arr.length&&arr[i]<=key;
      step(`while (i < n && key[i] <= k) → ${cont}`,{current:Math.min(i,arr.length-1),i,"key[i]":i<arr.length?arr[i]:null,n:arr.length},null,true);
      if(!cont)break;
      i++;
      step("i++",{current:Math.min(i,arr.length-1),i},null,true);
    }
    step("return i - 1",{current:Math.max(0,i-1),i,ipos:Math.max(0,i-1)},action("visit",`不大于 ${key} 的最大序号是 ${Math.max(0,i-1)}`,{value:Math.max(0,i-1)}));
    return makeTrace(req,"B 树结点内定位关键字序号",`k=${key}`,`ipos=${Math.max(0,i-1)}`,steps);
  }
  if(op==="node_insert"){
    /* 先定位，再从最后一个关键字起**一格一格往后挪**，最后把新关键字写进腾出来的位置。 */
    const arr=[...keys];
    const at=(extra={})=>view("btree",[row("node",arr),row("meta",[],{operation:op,key,order,...extra})]);
    const steps=[makeStep(sid++,"init","B 树结点",`当前结点：[${arr.join(", ")}]，m=${order}；把 ${key} 插到正确的位置上。`,at())];
    const step=(code,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"compare",code,code,at(extra),act?[act]:[]));
    step("i = 0",{current:0,i:0},null,true);
    let i=0;
    for(;;){
      const cont=i<arr.length&&arr[i]<key;
      step(`while (i < n && k > key[i]) → ${cont}`,{current:Math.min(i,arr.length-1),i,"key[i]":i<arr.length?arr[i]:null,n:arr.length},null,true);
      if(!cont)break;
      i++;
      step("i++",{current:Math.min(i,arr.length-1),i},null,true);
    }
    arr.push(null);
    for(let j=arr.length-1;j>i;j--){
      arr[j]=arr[j-1];
      step(`key[${j}] = key[${j-1}]`,{current:j,i},null,true);
    }
    arr[i]=key;
    step("key[i] = k",{current:i,i,"key[i]":key},action("insert","新关键字写入腾出来的位置",{value:key}));
    step("n = n + 1",{current:i,i,n:arr.length},action("assign","结点关键字数加一",{value:arr.length}));
    return makeTrace(req,"B 树结点内插入",`[${keys.join(",")}]`,`[${arr.join(",")}]`,steps);
  }
  /* split：结点已经满了，先插进去，再从中位数处劈成两半，中位数上移到父结点。 */
  const full=[...keys,key].sort((a,b)=>a-b);
  const mid=Math.floor(full.length/2);
  const left=full.slice(0,mid),right=full.slice(mid+1);
  const at=(rows,extra={})=>view("btree",[...rows,row("meta",[],{operation:op,key,order,...extra})]);
  const steps=[makeStep(sid++,"init","满结点",`插入 ${key} 之前结点是 [${keys.join(", ")}]，m=${order}。`,at([row("node",[...keys])]))];
  const step=(code,rows,extra,act,line)=>steps.push(makeStep(sid++,line?"line":"split",code,code,at(rows,extra),act?[act]:[]));
  step("p->key[ipos] = k",[row("node",full)],{current:mid},action("insert",`先插进满结点，现在有 ${full.length} 个关键字`,{value:key}),true);
  step("mid = n / 2",[row("node",full)],{current:mid,mid,n:full.length},null,true);
  step("s = p->key[mid]",[row("node",full)],{current:mid,mid,s:full[mid]},action("read","中位数就是上移的那个关键字",{value:full[mid]}),true);
  step("p->n = mid",[row("node",full),row("left",left)],{current:mid,mid,s:full[mid]},null,true);
  step("q->key[0..] = p->key[mid+1..]",[row("node",full),row("left",left),row("right",right)],{current:mid,mid,s:full[mid]},action("split","右半搬进新结点 q",{value:right}),true);
  step("parent->key[i] = s; parent->child[i+1] = q",[row("left",left),row("promote",[full[mid]]),row("right",right)],{mid,s:full[mid]},action("link","中间关键字提升到父结点",{value:full[mid]}));
  return makeTrace(req,"B 树结点分裂",`[${full.join(",")}]`,`promote=${full[mid]}`,steps);
}

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
    for(const p of positions){const idx=Math.max(0,Math.min(ds.length-1,p-1));steps.push(makeStep(sid++,"select",`观察第 ${p} 位`,`第 ${p} 位数字是 ${ds[idx]}，选位的原则是这一位在所有关键字里分布尽量均匀。`,st([row("digits",ds,{focusIndex:idx})],{phase:"scan"}),[action("select","观察数位",{target:idx})]));}
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

function simulateLinkedRadix(req,api){const {makeStep,makeTrace,action}=helpers(api);let a=numArray(req.initial_state.data,[329,457,657,839,436,720,355]),sid=1;const maxDigits=Math.max(...a.map(x=>String(Math.abs(Math.trunc(x))).length));const steps=[makeStep(sid++,"init","静态链表保存待排记录","使用 next 指针把记录组织成当前链表顺序。",view("sort",[row("records",a),row("next",a.map((_,i)=>i+1<a.length?i+1:-1)),row("meta",[],{operation:req.operation})]))];let exp=1;for(let pass=1;pass<=maxDigits;pass++,exp*=10){const buckets=Array.from({length:10},()=>[]);for(const v of a){const d=Math.floor(Math.abs(v)/exp)%10;buckets[d].push(v);steps.push(makeStep(sid++,"distribute","按当前位分配到链式队列",`${v} 的第 ${pass} 位是 ${d}，接到 ${d} 号桶尾。`,view("sort",[row("records",a,{focusIndex:a.indexOf(v)}),row("buckets",buckets.map((b,i)=>({bucket:i,values:b}))),row("meta",[],{pass,current:v,digit:d})]),[action("link","记录接入桶尾",{target:d,value:v})]));}a=buckets.flat();steps.push(makeStep(sid++,"collect","按桶号重新链接记录","依次连接 0～9 号桶的首尾指针，得到新的静态链表顺序。",view("sort",[row("records",a),row("next",a.map((_,i)=>i+1<a.length?i+1:-1)),row("meta",[],{pass})]),[action("link","按桶序收集",{value:a})]));}return makeTrace(req,"链式基数排序",`[${numArray(req.initial_state.data,[329,457,657,839,436,720,355]).join(",")}]`,`[${a.join(",")}]`,steps);}

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
