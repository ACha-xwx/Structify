const { normalizeOperationRequest } = require('./dsvp-engine');

function cap(capability, structure, operation, label, description, requiredArguments, optionalArguments, demoArguments, textbook) {
  return Object.freeze({ capability, structure, operation, label, description, requiredArguments, optionalArguments, demoArguments, textbook });
}

const entries = [
  cap('stack.push','stack','push','栈入栈','元素写入栈顶并改变 top',['value'],['initialData','capacity'],{initialData:[2,5],value:7,capacity:10},'3.1 栈'),
  cap('stack.pop','stack','pop','栈出栈','读取栈顶、top 前移',['initialData'],['capacity'],{initialData:[2,5,7],capacity:10},'3.1 栈'),
  cap('queue.enqueue','queue','enqueue','队列入队','元素从队尾进入',['value'],['initialData','capacity'],{initialData:[4,7],value:9,capacity:10},'3.2 队列'),
  cap('queue.dequeue','queue','dequeue','队列出队','队首离开并更新队首',['initialData'],['capacity'],{initialData:[4,7,9],capacity:10},'3.2 队列'),
  cap('sequential_list.insert','sequential_list','insert','顺序表插入','后续元素后移、写入新元素',['initialData','position','value'],['capacity'],{initialData:[10,20,30,40],position:3,value:25,capacity:10},'2.2 顺序表基本运算'),
  cap('sequential_list.delete','sequential_list','delete','顺序表删除','后续元素前移、表长减 1',['initialData','position'],['capacity'],{initialData:[10,20,30,40],position:2,capacity:10},'2.2 顺序表基本运算'),
  cap('sequential_list.merge','sequential_list','merge','顺序表合并','双指针合并两个非递减表',['left','right'],['capacity'],{left:[1,3,5],right:[2,4,6],capacity:10},'2.2 顺序表合并'),

  cap('linked_list.initialize','linked_list','initialize','初始化单链表','申请头结点并建立空表',[],[],{},'算法 2.5 单链表初始化'),
  cap('linked_list.build_head','linked_list','build_head','头插法建立单链表','新结点始终插到头结点之后',['values'],[],{values:[1,2,3,4]},'算法 2.6 头插法建表'),
  cap('linked_list.build_tail','linked_list','build_tail','尾插法建立单链表','尾指针持续后移建立输入同序链表',['values'],[],{values:[1,2,3,4]},'算法 2.7 尾插法建表'),
  cap('linked_list.search_position','linked_list','search_position','单链表按位置查找','沿 next 找到第 i 个结点',['initialData','position'],[],{initialData:[10,20,30,40],position:3},'算法 2.8 按位置查找'),
  cap('linked_list.search_value','linked_list','search_value','单链表按值查找','沿 next 比较 data 与 key',['initialData','key'],[],{initialData:[10,20,30,40],key:30},'算法 2.9 按值查找'),
  cap('linked_list.length','linked_list','length','求单链表长度','遍历数据结点并计数',['initialData'],[],{initialData:[10,20,30,40]},'算法 2.10 求表长'),
  cap('linked_list.reverse','linked_list','reverse','单链表逆置','逐结点反转 next 指针',['initialData'],[],{initialData:[10,20,30,40]},'第 2 章练习：链表逆置'),
  cap('linked_list.insert','linked_list','insert','单链表插入','定位前驱并修改 next',['initialData','position','value'],[],{initialData:[10,20,30],position:2,value:15},'2.3 单链表'),
  cap('linked_list.delete','linked_list','delete','单链表删除','定位结点并让前驱越过待删结点',['initialData','position'],[],{initialData:[10,20,30],position:2},'2.3 单链表'),
  cap('linked_list.merge','linked_list','merge','单链表有序合并','比较当前结点并接到结果链尾',['left','right'],[],{left:[1,3,5],right:[2,4,6]},'2.3 单链表'),
  cap('circular_linked_list.initialize','circular_linked_list','initialize','初始化循环单链表','头结点 next 指向自身',[],[],{},'2.4 循环单链表'),
  cap('circular_linked_list.build','circular_linked_list','build','建立循环单链表','尾插数据结点并保持尾结点回到表头',['values'],[],{values:[1,2,3,4]},'2.4 循环单链表建立'),
  cap('circular_linked_list.merge_head_pointer','circular_linked_list','merge_head_pointer','循环链表合并（头指针）','先找两个表尾再重接首尾',['left','right'],[],{left:[1,3,5],right:[2,4,6]},'算法 2.14 循环链表合并'),
  cap('circular_linked_list.merge_tail_pointer','circular_linked_list','merge_tail_pointer','循环链表合并（尾指针）','利用尾指针直接完成首尾重接',['left','right'],[],{left:[1,3,5],right:[2,4,6]},'算法 2.15 循环链表合并'),
  cap('static_linked_list.initialize','static_linked_list','initialize','静态链表初始化','用 cursor 建立备用空间链',['size'],[],{size:8},'算法 2.18 静态单链表初始化'),
  cap('static_linked_list.allocate','static_linked_list','allocate','静态链表申请结点','从备用链摘下一个空闲结点',['size'],['usedIndices','value'],{size:8,usedIndices:[1,2,3],value:'NEW'},'算法 2.19 静态链表空间分配'),
  cap('static_linked_list.free','static_linked_list','free','静态链表回收结点','把释放结点重新接回备用链',['size','index'],['usedIndices'],{size:8,index:2,usedIndices:[1,2,3]},'算法 2.20 静态链表空间回收'),
  cap('doubly_linked_list.insert','doubly_linked_list','insert','双向链表插入','同步修改 prior 与 next',['initialData','position','value'],[],{initialData:[10,20,30],position:2,value:15},'2.4 双向链表'),
  cap('doubly_linked_list.delete','doubly_linked_list','delete','双向链表删除','前后相邻结点越过待删结点',['initialData','position'],[],{initialData:[10,20,30],position:2},'2.4 双向链表'),
  cap('polynomial.build','polynomial','build','建立一元多项式链表','按指数有序插入多项式各项',['terms'],[],{terms:[{coef:3,exp:4},{coef:5,exp:2},{coef:1,exp:0}]},'算法 2.21 建立多项式链表'),
  cap('polynomial.add','polynomial','add','一元多项式相加','按指数比较合并同类项',['left','right'],[],{left:[{coef:3,exp:4},{coef:2,exp:2},{coef:1,exp:0}],right:[{coef:5,exp:3},{coef:-2,exp:2},{coef:4,exp:0}]},'2.x 一元多项式'),

  cap('stack.peek','stack','peek','读取栈顶元素','读取 top 所指元素但不改变 top',['initialData'],[],{initialData:[2,5,7]},'算法 3.4 读栈顶'),
  cap('double_stack.initialize','double_stack','initialize','双端顺序栈初始化','两个栈从共享数组两端向中间增长',[],['capacity','left','right'],{capacity:8,left:[1,2],right:[9,8]},'算法 3.5 双端栈初始化'),
  cap('double_stack.push_left','double_stack','push_left','双端栈左侧进栈','左栈 top 向右增长',['value'],['capacity','left','right'],{value:3,capacity:8,left:[1,2],right:[9,8]},'算法 3.6 双端栈进栈'),
  cap('double_stack.push_right','double_stack','push_right','双端栈右侧进栈','右栈 top 向左增长',['value'],['capacity','left','right'],{value:7,capacity:8,left:[1,2],right:[9,8]},'算法 3.6 双端栈进栈'),
  cap('double_stack.pop_left','double_stack','pop_left','双端栈左侧出栈','左端 top 回退',[],['capacity','left','right'],{capacity:8,left:[1,2],right:[9,8]},'算法 3.7 双端栈出栈'),
  cap('double_stack.pop_right','double_stack','pop_right','双端栈右侧出栈','右端 top 回退',[],['capacity','left','right'],{capacity:8,left:[1,2],right:[9,8]},'算法 3.7 双端栈出栈'),
  cap('linked_stack.push','linked_stack','push','链栈进栈','申请新结点并接到链首',['value'],['initialData'],{initialData:[2,5,7],value:9},'算法 3.8 链栈进栈'),
  cap('linked_stack.pop','linked_stack','pop','链栈出栈','摘下链首栈顶并更新 top',['initialData'],[],{initialData:[2,5,7]},'算法 3.9 链栈出栈'),
  cap('stack_app.bracket_match','stack_app','bracket_match','括号匹配','左括号进栈、右括号与栈顶配对',['text'],[],{text:'{[()()]}'},'3.x 栈的应用：括号匹配'),
  cap('stack_app.expression_evaluate','stack_app','expression_evaluate','表达式求值','操作数栈与运算符栈协同',['expression'],[],{expression:'3+4*2-(6/3)'},'3.x 栈的应用：表达式求值'),
  cap('recursion.hanoi','recursion','hanoi','汉诺塔递归','递归拆解并移动圆盘',['n'],[],{n:4},'3.x 递归与汉诺塔'),
  cap('recursion.factorial_recursive','recursion','factorial_recursive','阶乘递归调用','显示递归压栈与逐层返回',['n'],[],{n:5},'例 3.4 / 算法 3.14 阶乘递归'),
  cap('recursion.factorial_iterative','recursion','factorial_iterative','阶乘非递归','循环累计乘积',['n'],[],{n:5},'算法 3.15 阶乘非递归'),
  cap('recursion.fibonacci_recursive','recursion','fibonacci_recursive','Fibonacci 递归调用','展开递归调用树和返回值',['n'],[],{n:6},'3.2 递归算法示例'),
  cap('recursion.fibonacci_iterative','recursion','fibonacci_iterative','Fibonacci 非递归','滚动保存前两项',['n'],[],{n:8},'算法 3.13 Fibonacci 非递归'),
  cap('linked_queue.initialize','linked_queue','initialize','链队列初始化','front 和 rear 同指头结点',[],[],{},'算法 3.16 链队列初始化'),
  cap('linked_queue.enqueue','linked_queue','enqueue','链队列入队','尾插新结点并更新 rear',['value'],['initialData'],{initialData:[4,7,9],value:12},'算法 3.17 链队列入队'),
  cap('linked_queue.dequeue','linked_queue','dequeue','链队列出队','摘下队首数据结点并处理 rear',['initialData'],[],{initialData:[4,7,9]},'算法 3.18 链队列出队'),
  cap('circular_queue.initialize','circular_queue','initialize','循环队列初始化','front=rear=0',[ 'capacity' ],[],{capacity:6},'算法 3.19 循环队列初始化'),
  cap('queue_app.yanghui_triangle','queue_app','yanghui_triangle','队列生成杨辉三角','利用上一行相邻元素生成下一行',['rows'],[],{rows:6},'算法 3.22 杨辉三角'),
  cap('circular_buffer.process_input','circular_buffer','process_input','键盘输入循环缓冲区','字符循环写入并在满时消费最早字符',['input'],['capacity'],{input:'ABCDEFGH',capacity:6},'算法 3.23 键盘循环缓冲区'),
  cap('circular_queue.enqueue','circular_queue','enqueue','循环队列入队','rear 取模后移',['initialData','value'],['capacity'],{initialData:[4,7],value:9,capacity:6},'3.2 循环队列'),
  cap('circular_queue.dequeue','circular_queue','dequeue','循环队列出队','front 取模后移',['initialData'],['capacity'],{initialData:[4,7,9],capacity:6},'3.2 循环队列'),

  cap('string.insert','string','insert','顺序串插入','字符移动并插入子串',['text','position','value'],[],{text:'DATA',position:3,value:'XX'},'4.x 串的基本运算'),
  cap('string.delete','string','delete','顺序串删除','删除区间并前移后续字符',['text','position'],['count'],{text:'DATASTRUCTURE',position:5,count:4},'4.x 串的基本运算'),
  cap('string.brute_force_match','string','brute_force_match','BF 模式匹配','主串起点逐次后移并比较',['text','pattern'],[],{text:'ABABCABCACBAB',pattern:'ABCAC'},'4.x 模式匹配'),
  cap('string.kmp_match','string','kmp_match','KMP 模式匹配','利用 next 回退模式指针而主串不回退',['text','pattern'],[],{text:'ABABCABCACBAB',pattern:'ABCAC'},'4.x KMP 模式匹配'),

  cap('string.compare','string','compare','串比较','从左到右比较对应字符',['left','right'],[],{left:'DATA',right:'DATE'},'算法 4.3 串比较'),
  cap('heap_string.insert','heap_string','insert','堆串插入','动态申请新空间并复制前段、插入串、后段',['text','position','value'],[],{text:'DATA',position:3,value:'XX'},'算法 4.5 堆串插入'),
  cap('heap_string.assign','heap_string','assign','堆串赋值','释放旧空间并按新串长度重新申请',['text','value'],[],{text:'OLD',value:'DATASTRUCTURE'},'算法 4.6 堆串赋值'),
  cap('special_matrix.compress_map','special_matrix','compress_map','特殊矩阵压缩下标映射','把二维下标按矩阵类型映射到压缩数组',['n','kind','i','j'],[],{n:5,kind:'lower_triangular',i:4,j:2},'5.3 特殊矩阵压缩存储'),
  cap('sparse_matrix.transpose','sparse_matrix','transpose','稀疏矩阵转置','按列扫描三元组并交换行列下标',['matrix'],[],{matrix:[[0,5,0],[2,0,3],[0,0,4]]},'5.x 稀疏矩阵转置'),
  cap('sparse_matrix.fast_transpose','sparse_matrix','fast_transpose','稀疏矩阵快速转置','统计列非零元并用 cpot 直接定位',['matrix'],[],{matrix:[[0,5,0],[2,0,3],[0,0,4]]},'5.x 稀疏矩阵快速转置'),
  cap('sparse_matrix.cross_list_build','sparse_matrix','cross_list_build','稀疏矩阵十字链表建立','非零结点同时接入行链和列链',['matrix'],[],{matrix:[[0,5,0],[2,0,3],[0,0,4]]},'5.x 稀疏矩阵十字链表'),
  cap('generalized_list.head','generalized_list','head','广义表取表头','选择最外层第一个表元素',['initialData'],[],{initialData:['a',['b','c'],['d',['e']]]},'5.4 广义表基本运算'),
  cap('generalized_list.tail','generalized_list','tail','广义表取表尾','去掉表头后形成表尾',['initialData'],[],{initialData:['a',['b','c'],['d',['e']]]},'5.x 广义表基本运算'),
  cap('generalized_list.length','generalized_list','length','广义表求长度','统计最外层表元素个数',['initialData'],[],{initialData:['a',['b','c'],['d',['e']]]},'5.x 广义表基本运算'),
  cap('generalized_list.depth','generalized_list','depth','广义表求深度','递归进入子表并取最大深度',['initialData'],[],{initialData:['a',['b','c'],['d',['e']]]},'5.x 广义表基本运算'),
  cap('generalized_list.atom_count','generalized_list','atom_count','广义表原子计数','递归累计所有原子结点',['initialData'],[],{initialData:['a',['b','c'],['d',['e']]]},'5.x 广义表递归运算'),
  cap('generalized_list.copy','generalized_list','copy','广义表复制','递归复制原子和子表结构',['initialData'],[],{initialData:['a',['b','c'],['d',['e']]]},'5.x 广义表复制'),

  cap('tree.build','tree','build','建立二叉树','按层次序列建立结点及父子边',['initialData'],[],{initialData:['A','B','C','D','E',null,'F']},'6.x 二叉树建立'),
  cap('tree.preorder','tree','preorder','二叉树先序遍历','根-左-右访问',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 二叉树遍历'),
  cap('tree.inorder','tree','inorder','二叉树中序遍历','左-根-右访问',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 二叉树遍历'),
  cap('tree.postorder','tree','postorder','二叉树后序遍历','左-右-根访问',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 二叉树遍历'),
  cap('tree.levelorder','tree','levelorder','二叉树层序遍历','借助队列逐层访问',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 二叉树层序遍历'),
  cap('tree.inorder_stack','tree','inorder_stack','非递归中序遍历','沿左链进栈，退栈访问后转右子树',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 非递归遍历'),
  cap('tree.postorder_stack','tree','postorder_stack','非递归后序遍历','显式栈保存回访状态',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 非递归遍历'),
  cap('tree.thread_inorder','tree','thread_inorder','中序线索化','空孩子指针改作前驱/后继线索',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'6.x 线索二叉树'),
  cap('tree.thread_predecessor','tree','thread_predecessor','中序线索树求前驱','沿线索确定给定结点的前驱',['initialData','target'],[],{initialData:['A','B','C','D','E','F','G'],target:'A'},'6.x 线索二叉树'),
  cap('tree.thread_successor','tree','thread_successor','中序线索树求后继','沿线索确定给定结点的后继',['initialData','target'],[],{initialData:['A','B','C','D','E','F','G'],target:'A'},'6.x 线索二叉树'),
  cap('tree.leaf_output','tree','leaf_output','遍历输出叶子结点','遍历时判断左右孩子均为空并输出',['initialData'],[],{initialData:['A','B','C','D','E',null,'F']},'算法 6.5 输出叶子结点'),
  cap('tree.leaf_count','tree','leaf_count','统计叶子结点数','遍历并累计叶结点',['initialData'],[],{initialData:['A','B','C','D','E',null,'F']},'算法 6.6 统计叶子结点'),
  cap('tree.build_extended_preorder','tree','build_extended_preorder','扩展先序序列建二叉树','# 表示空子树，递归建立二叉链表',['sequence'],[],{sequence:'ABD##E##C#F##'},'算法 6.7 建立二叉树'),
  cap('tree.height_postorder','tree','height_postorder','后序遍历求树高','由左右子树高度向根合并',['initialData'],[],{initialData:['A','B','C','D','E',null,'F']},'算法 6.8 后序求高度'),
  cap('tree.height_preorder','tree','height_preorder','先序遍历求树高','按当前结点层次持续更新最大深度',['initialData'],[],{initialData:['A','B','C','D','E',null,'F']},'算法 6.9 先序求高度'),
  cap('tree.thread_first','tree','thread_first','线索树求中序第一个结点','沿左孩子/线索定位中序首结点',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'算法 6.16 中序首结点'),
  cap('tree.thread_traverse','tree','thread_traverse','遍历中序线索二叉树','不断沿中序后继线索访问',['initialData'],[],{initialData:['A','B','C','D','E','F','G']},'算法 6.17 线索树遍历'),
  cap('tree.path_to_node','tree','path_to_node','根到指定结点路径','从根沿父子关系展示到目标的路径',['initialData','target'],[],{initialData:['A','B','C','D','E',null,'F'],target:'E'},'第 6 章习题：根到结点路径'),
  cap('tree.similarity','tree','similarity','判断二叉树结构相似','递归比较对应位置是否同为空/非空',['initialData','other'],[],{initialData:['A','B','C','D',null,'E'],other:['X','Y','Z','Q',null,'R']},'第 6 章遍历判定'),
  cap('forest.to_binary_tree','forest','to_binary_tree','森林转换为二叉树','第一孩子作左孩子、下一兄弟作右孩子',['trees'],[],{trees:[['A','B','C'],['D','E'],['F']]},'6.5 树森林与二叉树关系'),
  cap('forest.binary_tree_to_forest','forest','binary_tree_to_forest','二叉树还原为森林','按左孩子右兄弟关系拆回树森林',['trees'],[],{trees:[['A','B','C'],['D','E'],['F']]},'6.5 树森林与二叉树关系'),
  cap('huffman.build','huffman','build','构造哈夫曼树','反复选择两个最小权值合并',['weights'],['symbols'],{weights:[2,3,4,7],symbols:['A','B','C','D']},'6.5 哈夫曼树'),
  cap('huffman.encode','huffman','encode','哈夫曼编码','建树后沿左右分支形成编码',['weights'],['symbols'],{weights:[2,3,4,7],symbols:['A','B','C','D']},'6.5 哈夫曼编码'),
  cap('union_find.find','union_find','find','并查集查找','沿 parent 指针找到集合代表元',['parent','element'],[],{parent:[-3,0,0,-2,3],element:2},'6.x 并查集'),
  cap('union_find.union','union_find','union','并查集合并','按集合树大小连接两个根',['parent','a','b'],[],{parent:[-2,0,-2,2],a:1,b:3},'6.x 并查集'),

  cap('union_find.initialize','union_find','initialize','并查集初始化','每个元素自成一个集合',['size'],[],{size:6},'6.7 并查集初始化'),
  cap('union_find.path_compress_find','union_find','path_compress_find','并查集路径压缩查找','找到根后把沿途结点直接连到根',['parent','element'],[],{parent:[-6,0,0,2,3,4],element:5},'6.7 并查集路径压缩'),
  cap('graph.build_adjacency_matrix','graph','build_adjacency_matrix','建立图的邻接矩阵','逐条边写入矩阵单元',['nodes','edges'],['directed'],{nodes:['A','B','C','D'],edges:[['A','B',1],['A','C',1],['C','D',1]],directed:true},'算法 7.1 邻接矩阵建图'),
  cap('graph.build_adjacency_list','graph','build_adjacency_list','建立图的邻接表','逐条边接入相应顶点的邻接链',['nodes','edges'],['directed'],{nodes:['A','B','C','D'],edges:[['A','B',1],['A','C',1],['C','D',1]],directed:false},'7.2 邻接表存储'),
  cap('graph.build_cross_list','graph','build_cross_list','建立有向图十字链表','边结点同时接入出边链和入边链',['nodes','edges'],[],{nodes:['A','B','C','D'],edges:[['A','B',1],['C','B',1],['B','D',1]]},'算法 7.2 十字链表建图'),
  cap('graph.dfs_nonrecursive','graph','dfs_nonrecursive','非递归深度优先搜索','使用显式栈替代递归深入与回溯',['nodes','edges','start'],['directed'],{nodes:['A','B','C','D','E'],edges:[['A','B',1],['A','C',1],['B','D',1],['C','E',1]],start:'A'},'算法 7.7 非递归 DFS'),
  cap('graph.connected_components','graph','connected_components','求图的连通分量','从每个未访问顶点启动一次遍历',['nodes','edges'],[],{nodes:['A','B','C','D','E','F'],edges:[['A','B',1],['B','C',1],['D','E',1]]},'7.4 连通性问题'),
  cap('graph.dfs','graph','dfs','图的深度优先遍历','沿未访问邻接点深入再回溯',['nodes','edges','start'],['directed'],{nodes:['A','B','C','D','E'],edges:[['A','B',1],['A','C',1],['B','D',1],['C','E',1]],start:'A'},'7.x 图遍历'),
  cap('graph.bfs','graph','bfs','图的广度优先遍历','借助队列按层扩展邻接点',['nodes','edges','start'],['directed'],{nodes:['A','B','C','D','E'],edges:[['A','B',1],['A','C',1],['B','D',1],['C','E',1]],start:'A'},'7.x 图遍历'),
  cap('graph.path_search','graph','path_search','图中简单路径搜索','搜索并恢复起点到终点的一条路径',['nodes','edges','start','target'],['directed'],{nodes:['A','B','C','D','E'],edges:[['A','B',1],['A','C',1],['B','D',1],['C','E',1],['D','E',1]],start:'A',target:'E'},'7.x 路径'),
  cap('graph.prim','graph','prim','Prim 最小生成树','从当前顶点集选择最小跨边',['nodes','edges','start'],[],{nodes:['A','B','C','D'],edges:[['A','B',2],['A','C',5],['B','C',1],['B','D',4],['C','D',2]],start:'A'},'7.x 最小生成树'),
  cap('graph.kruskal','graph','kruskal','Kruskal 最小生成树','边按权递增，跳过成环边',['nodes','edges'],[],{nodes:['A','B','C','D'],edges:[['A','B',2],['A','C',5],['B','C',1],['B','D',4],['C','D',2]]},'7.x 最小生成树'),
  cap('graph.topological_sort','graph','topological_sort','拓扑排序','反复输出入度为 0 的顶点',['nodes','edges'],[],{nodes:['A','B','C','D','E'],edges:[['A','C',1],['B','C',1],['B','D',1],['C','E',1],['D','E',1]]},'7.4 拓扑排序'),
  cap('graph.critical_path','graph','critical_path','关键路径','拓扑序计算 ve/vl 并找关键活动',['nodes','edges'],[],{nodes:['A','B','C','D','E'],edges:[['A','B',3],['A','C',2],['B','D',2],['C','D',4],['D','E',2]]},'7.4 关键路径'),
  cap('graph.dijkstra','graph','dijkstra','Dijkstra 最短路径','确定最短顶点并松弛相邻边',['nodes','edges','start'],[],{nodes:['A','B','C','D'],edges:[['A','B',2],['A','C',5],['B','C',1],['B','D',4],['C','D',2]],start:'A'},'7.x 单源最短路径'),
  cap('graph.floyd','graph','floyd','Floyd 最短路径','逐个允许中间顶点并更新距离矩阵',['nodes','edges'],['directed'],{nodes:['A','B','C','D'],edges:[['A','B',2],['A','C',6],['B','C',1],['C','D',2],['B','D',7]],directed:true},'7.x 各对顶点最短路径'),

  cap('search.sequential','search','sequential','顺序查找','从表头逐项比较',['initialData','key'],[],{initialData:[7,13,18,24,31,42,55],key:24},'8.x 顺序查找'),
  cap('search.binary','search','binary','折半查找','维护 low/mid/high 缩小有序区间',['initialData','key'],[],{initialData:[7,13,18,24,31,42,55],key:31},'8.x 折半查找'),
  cap('search.block','search','block','分块查找','先查索引块，再块内顺序查找',['initialData','key'],['blockSize'],{initialData:[7,13,18,24,31,42,55,63,70],key:42,blockSize:3},'8.x 分块查找'),
  cap('bst.search','bst','search','二叉排序树查找','按关键字大小选择左右子树',['initialData','key'],[],{initialData:[45,24,53,12,28,90],key:28},'8.x 二叉排序树'),
  cap('bst.insert','bst','insert','二叉排序树插入','查找空位置并插入叶结点',['initialData','key'],[],{initialData:[45,24,53,12,28,90],key:35},'8.x 二叉排序树'),
  cap('bst.delete','bst','delete','二叉排序树删除','按叶/单子树/双子树三种情况调整',['initialData','key'],[],{initialData:[45,24,53,12,28,90],key:24},'8.x 二叉排序树'),
  cap('bst.create','bst','create','创建二叉排序树','依次按 BST 插入规则建立树',['values'],[],{values:[45,24,53,12,28,90]},'算法 8.5 创建二叉排序树'),
  cap('avl.rotate_ll','avl','rotate_ll','AVL LL 型调整','对失衡结点执行一次右旋',[],['initialData'],{initialData:[30,20,10]},'8.3 AVL LL 型平衡调整'),
  cap('avl.rotate_rr','avl','rotate_rr','AVL RR 型调整','对失衡结点执行一次左旋',[],['initialData'],{initialData:[10,20,30]},'8.3 AVL RR 型平衡调整'),
  cap('avl.rotate_lr','avl','rotate_lr','AVL LR 型调整','先左旋左子树再右旋失衡根',[],['initialData'],{initialData:[30,10,20]},'8.3 AVL LR 型平衡调整'),
  cap('avl.rotate_rl','avl','rotate_rl','AVL RL 型调整','先右旋右子树再左旋失衡根',[],['initialData'],{initialData:[10,30,20]},'8.3 AVL RL 型平衡调整'),
  cap('avl.insert','avl','insert','AVL 插入与平衡','BST 插入后执行 LL/RR/LR/RL 调整',['initialData','key'],[],{initialData:[30,20,40,10],key:5},'8.x AVL 树'),
  cap('btree.search','btree','search','B 树查找','结点内比较并沿孩子下降',['initialData','key'],['order'],{initialData:[10,20,30,40,50,60],key:40,order:3},'8.x B 树'),
  cap('btree.insert','btree','insert','B 树插入','插入并在溢出时分裂提升',['initialData','key'],['order'],{initialData:[10,20,30,40,50,60],key:35,order:3},'8.x B 树'),
  cap('btree.delete','btree','delete','B 树删除','删除并进行借位或合并',['initialData','key'],['order'],{initialData:[10,20,30,40,50,60],key:30,order:3},'8.x B 树'),
  cap('btree.locate_position','btree','locate_position','B树结点内定位序号','查找小于等于 k 的最大关键字序号',['initialData','key'],['order'],{initialData:[10,20,30,40],key:25,order:4},'算法 8.11 B树结点内定位'),
  cap('btree.node_insert','btree','node_insert','B树结点内插入关键字','结点内腾位置并写入新关键字',['initialData','key'],['order'],{initialData:[10,20,30],key:25,order:5},'算法 8.13 结点内插入'),
  cap('btree.split','btree','split','B树结点分裂','满结点分成左右两部分并提升中间关键字',['initialData','key'],['order'],{initialData:[10,20,30,40],key:25,order:4},'算法 8.14 B树分裂'),
  cap('hash_table.linear_probe_insert','hash_table','linear_probe_insert','线性探测插入','冲突时地址依次加 1 探测',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59],key:69,tableSize:11},'8.4 哈希冲突处理'),
  cap('hash_table.linear_probe_search','hash_table','linear_probe_search','线性探测查找','沿与插入相同探测序列查找',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59,69],key:69,tableSize:11},'8.4 哈希查找'),
  cap('hash_table.quadratic_probe_insert','hash_table','quadratic_probe_insert','二次探测插入','按平方增量处理冲突',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59],key:69,tableSize:11},'8.4 二次探测再散列'),
  cap('hash_table.random_probe_insert','hash_table','random_probe_insert','伪随机探测插入','按确定的伪随机探测序列处理冲突',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59],key:69,tableSize:11},'8.4 伪随机探测再散列'),
  cap('hash_table.random_probe_search','hash_table','random_probe_search','伪随机探测查找','按相同伪随机序列查找',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59,69],key:69,tableSize:11},'8.4 伪随机探测再散列'),
  cap('hash_table.rehash_insert','hash_table','rehash_insert','再哈希插入','冲突后使用第二哈希函数确定步长',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59],key:69,tableSize:11},'8.4 再哈希法'),
  cap('hash_table.chaining_insert','hash_table','chaining_insert','链地址法插入','关键字接入对应同义词链',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59],key:69,tableSize:11},'8.4 链地址法'),
  cap('hash_table.chaining_search','hash_table','chaining_search','链地址法查找','定位桶后沿同义词链比较',['initialData','key'],['tableSize'],{initialData:[18,41,22,44,59,69],key:69,tableSize:11},'8.4 链地址法'),

  cap('sort.quick_partition','sort','quick_partition','一趟快速排序划分','双向扫描使枢轴最终就位',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'算法 9.6 一趟快速排序'),
  cap('sort.heap_adjust','sort','heap_adjust','重建堆过程','待调整记录沿较大孩子方向下沉',['initialData'],['root'],{initialData:[13,70,65,50,60,20,30],root:0},'算法 9.8 重建堆'),
  cap('sort.heap_build','sort','heap_build','建立初始堆','从最后一个非叶结点向根逐个调整',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'算法 9.9 建初堆'),
  cap('sort.merge_two','sort','merge_two','合并相邻两个有序子序列','比较两段当前记录并顺序写入结果',['initialData'],['mid'],{initialData:[1,4,7,2,5,8],mid:3},'算法 9.11 两个有序子序列合并'),
  cap('sort.dutch_flag','sort','dutch_flag','荷兰国旗三色分类','维护 0/1/2 三个区域的边界',['initialData'],[],{initialData:[2,0,1,2,1,0,0,2,1]},'第 9 章习题：荷兰国旗问题'),
  cap('sort.direct_insertion','sort','direct_insertion','直接插入排序','逐个把记录插入前部有序区',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 插入排序'),
  cap('sort.binary_insertion','sort','binary_insertion','折半插入排序','折半定位插入位置再搬移',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 插入排序'),
  cap('sort.shell','sort','shell','希尔排序','按增量分组做插入排序',['initialData'],['gaps'],{initialData:[49,38,65,97,76,13,27],gaps:[3,1]},'9.x 希尔排序'),
  cap('sort.bubble','sort','bubble','冒泡排序','相邻比较交换使最大记录逐趟就位',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 交换排序'),
  cap('sort.quick','sort','quick','快速排序','选枢轴划分并递归处理子区间',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 快速排序'),
  cap('sort.simple_selection','sort','simple_selection','简单选择排序','从未排序区选择最小记录',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 选择排序'),
  cap('sort.tournament_selection','sort','tournament_selection','树形/锦标赛选择排序','比较树逐轮选出当前最小记录',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 树形选择排序'),
  cap('sort.heap','sort','heap','堆排序','建堆、堆顶交换、向下调整',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 堆排序'),
  cap('sort.merge','sort','merge','归并排序','相邻有序段逐层归并',['initialData'],[],{initialData:[49,38,65,97,76,13,27]},'9.x 归并排序'),
  cap('sort.radix','sort','radix','基数排序','按位分配到桶并依次收集',['initialData'],[],{initialData:[329,457,657,839,436,720,355]},'9.x 基数排序'),

  // 教材逐算法补全：线性表 / 栈 / 图 / 查找 / 哈希函数构造 / 基数与外排序缓冲
  cap('sequential_list.search','search','sequential','顺序表按值查找','按教材算法 2.1 逐项比较定位关键字',['initialData','key'],[],{initialData:[12,25,37,48,59],key:37},'算法 2.1 顺序表按值查找'),
  cap('stack.initialize','stack','initialize','顺序栈初始化','建立空栈并设置 top=-1',[],['capacity'],{capacity:10},'算法 3.1 顺序栈初始化'),
  cap('graph.compute_indegree','graph','compute_indegree','计算各顶点入度','逐边扫描并累计有向图各顶点入度',['nodes','edges'],['directed'],{nodes:['A','B','C','D'],edges:[['A','B'],['A','C'],['B','D'],['C','D']],directed:true},'算法 7.12 计算各顶点入度'),
  cap('search.sequential_sentinel','search','sequential_sentinel','带监视哨的顺序查找','把关键字置于 r[0] 后从表尾向前比较',['initialData','key'],[],{initialData:[12,25,37,48,59],key:37},'算法 8.1 带监视哨的顺序查找'),
  cap('bst.search_recursive','bst','search_recursive','二叉排序树递归查找','根据大小关系递归进入左/右子树',['initialData','key'],[],{initialData:[45,24,53,12,28,90],key:28},'算法 8.6 二叉排序树递归查找'),
  cap('bst.search_nonrecursive','bst','search_nonrecursive','二叉排序树非递归查找','用循环沿比较路径向下查找',['initialData','key'],[],{initialData:[45,24,53,12,28,90],key:28},'算法 8.7 二叉排序树非递归查找'),
  cap('hash_function.digit_analysis','hash_function','digit_analysis','数字分析法构造哈希函数','从已知关键字中选取分布较均匀的若干位作为地址',['key','positions'],[],{key:'81346532',positions:[4,7]},'8.4.1 数字分析法'),
  cap('hash_function.mid_square','hash_function','mid_square','平方取中法构造哈希函数','关键字平方后取中间若干位作为地址',['key'],['width'],{key:11052501,width:3},'8.4.1 平方取中法'),
  cap('hash_function.folding_shift','hash_function','folding_shift','移位叠加法构造哈希函数','按地址位数分段、低位对齐叠加并舍弃最高进位',['key'],['blockSize','discardTailDigits','modulus'],{key:'12360324711202065',blockSize:3,discardTailDigits:2,modulus:1000},'8.4.1 分段叠加法·移位法'),
  cap('hash_function.folding_boundary','hash_function','folding_boundary','折叠叠加法构造哈希函数','分段后奇数段正序、偶数段倒序再叠加',['key'],['blockSize','discardTailDigits','modulus'],{key:'12360324711202065',blockSize:3,discardTailDigits:2,modulus:1000},'8.4.1 分段叠加法·折叠法'),
  cap('hash_function.division_remainder','hash_function','division_remainder','除留余数法构造哈希函数','按教材 H(key)=key mod p 计算哈希地址',['key','modulus'],[],{key:18,modulus:13},'8.4.1 除留余数法'),
  cap('hash_function.pseudo_random','hash_function','pseudo_random','伪随机数法构造哈希函数','展示教材 H(key)=random(key) 的给定映射，不擅自规定随机函数公式',['key','randomValue'],[],{key:12345,randomValue:7},'8.4.1 伪随机数法'),
  cap('sort.linked_radix','sort','linked_radix','链式基数排序','静态链表记录按位分配到 0~9 队列并重新链接',['initialData'],[],{initialData:[329,457,657,839,436,720,355]},'算法 9.14 链式基数排序'),
  cap('external_sort.disk_buffer_merge','external_sort','disk_buffer_merge','外排序输入输出缓冲归并','用两个输入缓冲区和一个输出缓冲区模拟外存二路归并',['runs'],['pageSize'],{runs:[[1,5,9],[2,6,8]],pageSize:2},'10.1 外部排序二路归并的输入/输出缓冲过程'),

  cap('external_sort.two_way_merge','external_sort','two_way_merge','外排序二路归并','归并多个初始顺串直到剩一个',['runs'],[],{runs:[[1,7,13],[2,8,12],[3,6,15],[4,9,11]]},'10.1 外部归并排序'),
  cap('external_sort.multiway_merge','external_sort','multiway_merge','外排序多路归并','每次同时归并 k 个顺串',['runs'],['ways'],{runs:[[1,7,13],[2,8,12],[3,6,15],[4,9,11]],ways:3},'10.1 多路归并'),
  cap('external_sort.replacement_selection','external_sort','replacement_selection','置换选择生成初始顺串','内存工作区持续选择可输出记录',['input'],['memorySize'],{input:[12,7,18,3,15,9,20,4],memorySize:3},'10.x 置换选择')
];

const ANIMATION_CAPABILITY_REGISTRY = Object.freeze(Object.fromEntries(entries.map((item) => [item.capability, item])));

function animationCapabilityList() {
  return entries.map((item) => ({
    capability: item.capability,
    structure: item.structure,
    operation: item.operation,
    label: item.label,
    description: item.description,
    requiredArguments: [...item.requiredArguments],
    optionalArguments: [...item.optionalArguments],
    textbook: item.textbook,
    hasCanonicalDemo: Boolean(item.demoArguments)
  }));
}

function capabilityTextbookChapter(item) {
  const text = String(item?.textbook || '');
  const algorithm = text.match(/(?:算法\s*)?(\d+)\./);
  if (algorithm) return Number(algorithm[1]);
  const chapter = text.match(/第\s*(\d+)\s*章/);
  return chapter ? Number(chapter[1]) : null;
}

function lessonChapterFromId(lessonId) {
  const match = String(lessonId || '').match(/^(\d{1,2})-/);
  return match ? Number(match[1]) : null;
}

function animationCapabilityPrompt(options = {}) {
  // 语义识别从模型完成，但只把“当前教材章”的真实能力给模型，避免 160+ 个能力重新撑大备课 prompt。
  // 这里按 lessonId/教材元数据筛选能力，不对用户自然语言做关键词或正则意图判断。
  const chapter = lessonChapterFromId(options.lessonId);
  const visible = chapter
    ? entries.filter((item) => capabilityTextbookChapter(item) === chapter)
    : entries;
  return visible.map((item) => `${item.capability}[${item.requiredArguments.join('|') || '-'}]：${item.label}`).join('\n');
}

function clampConfidence(value) {
  const number = Number(value);
  if (!Number.isFinite(number)) return 0;
  return Math.max(0, Math.min(1, number));
}

function sanitize(value, depth = 0) {
  if (depth > 7) return null;
  if (value === null || value === undefined) return null;
  if (typeof value === 'number') return Number.isFinite(value) ? value : null;
  if (typeof value === 'string') return value.slice(0, 400);
  if (typeof value === 'boolean') return value;
  if (Array.isArray(value)) return value.map((item) => sanitize(item, depth + 1)); // 不截断：超限由 normalize 阶段显式报错
  if (typeof value === 'object') {
    const out = {};
    for (const [key, item] of Object.entries(value).slice(0, 80)) out[String(key).slice(0, 80)] = sanitize(item, depth + 1);
    return out;
  }
  return null;
}

function normalizeVisualizationIntent(value, options = {}) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const validChunkIds = options.validChunkIds instanceof Set ? options.validChunkIds : null;
  const sourceChunkIds = Array.isArray(value.sourceChunkIds)
    ? value.sourceChunkIds.map((item) => String(item || '').trim()).filter((id) => id && (!validChunkIds || validChunkIds.has(id))).slice(0, 8)
    : [];
  return {
    needed: value.needed === true || value.recommended === true,
    confidence: clampConfidence(value.confidence),
    capability: String(value.capability || '').trim(),
    purpose: String(value.purpose || value.reason || '').trim().slice(0, 240),
    arguments: sanitize(value.arguments && typeof value.arguments === 'object' && !Array.isArray(value.arguments) ? value.arguments : {}) || {},
    sourceChunkIds
  };
}

function isMissing(value) {
  if (value === null || value === undefined || value === '') return true;
  // 显式提供的数组（包括空数组）视为“已提供”：空数组是用户主动给的边界输入，
  // 应交给引擎给出明确结果/报错，而不是被演示数据静默替换。
  if (Array.isArray(value)) return false;
  if (typeof value === 'object') return Object.keys(value).length === 0;
  return false;
}

function clone(value) { return value === undefined ? undefined : JSON.parse(JSON.stringify(value)); }

function buildAnimationRequest(def, args, sourceRef) {
  if (def.capability === 'sequential_list.merge') {
    const left = args.left || [], right = args.right || [];
    const capacity = args.capacity || Math.min(100, Math.max(10, left.length + right.length + 2));
    return { version:'1.0', structure:def.structure, operation:def.operation, params:{capacity}, initial_state:{data:[left,right],metadata:{capacity}}, options:{language:'c',explain_level:'beginner'}, source_ref:sourceRef };
  }
  if (['stack','queue','sequential_list'].includes(def.structure)) {
    const data = Array.isArray(args.initialData) ? args.initialData : [];
    const capacity = args.capacity || Math.min(100, Math.max(10, data.length + 2));
    const params = { capacity };
    if (['push','enqueue','insert'].includes(def.operation)) params.value = args.value;
    if (['insert','delete'].includes(def.operation)) params.position = args.position;
    return { version:'1.0', structure:def.structure, operation:def.operation, params, initial_state:{data,metadata:{capacity}}, options:{language:'c',explain_level:'beginner'}, source_ref:sourceRef };
  }

  const initialData = args.initialData ?? args.parent ?? args.runs ?? [];
  const params = {};
  for (const [key,value] of Object.entries(args)) {
    if (['initialData','parent','runs'].includes(key)) continue;
    params[key] = clone(value);
  }
  // 引擎中的对应教材结构直接从 initial_state.data 读取这些状态。
  return {
    version:'1.0', structure:def.structure, operation:def.operation,
    params,
    initial_state:{data:clone(initialData),metadata:{}},
    options:{language:'c',explain_level:'beginner'}, source_ref:sourceRef
  };
}

function resolveVisualizationIntent(value, options = {}) {
  const intent = normalizeVisualizationIntent(value, options);
  const minimumConfidence = Number.isFinite(Number(options.minimumConfidence)) ? Number(options.minimumConfidence) : 0.7;
  if (!intent || !intent.needed) return { status:'not-needed', intent, toolRequest:null, missingArguments:[] };
  if (intent.confidence < minimumConfidence) return { status:'low-confidence', intent, toolRequest:null, missingArguments:[] };
  // 只认自有属性：__proto__/constructor/toString 等原型链键在此前会命中继承成员，
  // 绕过 !def 判断并在后面以未捕获 TypeError 泄露内部报错。
  const def = Object.hasOwn(ANIMATION_CAPABILITY_REGISTRY, intent.capability)
    ? ANIMATION_CAPABILITY_REGISTRY[intent.capability]
    : null;
  if (!def) return { status:'unsupported', intent, toolRequest:null, missingArguments:[] };

  const args = clone(intent.arguments || {}) || {};
  let missingArguments = def.requiredArguments.filter((name) => isMissing(args[name]));
  let usedDemoFallback = false;
  if (missingArguments.length && options.allowDemoFallback === true && def.demoArguments) {
    for (const name of missingArguments) if (!isMissing(def.demoArguments[name])) args[name] = clone(def.demoArguments[name]);
    for (const name of def.optionalArguments) if (isMissing(args[name]) && !isMissing(def.demoArguments[name])) args[name] = clone(def.demoArguments[name]);
    missingArguments = def.requiredArguments.filter((name) => isMissing(args[name]));
    usedDemoFallback = missingArguments.length === 0;
  }
  if (missingArguments.length) return { status:'missing-arguments', intent, capability:def, toolRequest:null, missingArguments };

  // 用户给的参数名必须落在能力声明的参数清单内：拼错/多余参数此前会被静默忽略，
  // 现在显式报错，让调用方（模型/前端）能发现并改正。
  const declaredArguments = new Set([...def.requiredArguments, ...def.optionalArguments, ...Object.keys(def.demoArguments || {})]);
  const unknownArguments = Object.keys(args).filter((name) => !declaredArguments.has(name));
  if (unknownArguments.length) {
    return {
      status:'invalid-arguments', intent, capability:def, missingArguments:[],
      error:`参数 ${unknownArguments.join('、')} 不被 ${def.capability} 支持（可接受：${declaredArguments.size ? [...declaredArguments].join('、') : '无参数'}）——请检查拼写`,
      toolRequest:null
    };
  }

  const sourceRef = String(usedDemoFallback ? (options.demoSourceRef || '系统标准教学示例（非教材原例）') : (options.sourceRef || '当前课堂教材')).slice(0,160);
  try {
    const request = normalizeOperationRequest(buildAnimationRequest(def,args,sourceRef));
    return {
      status:'ready', intent, capability:def, missingArguments:[],
      toolRequest:{ kind:'animation', protocol:'dsvp/1', capability:def.capability, purpose:intent.purpose, confidence:intent.confidence, sourceChunkIds:intent.sourceChunkIds, demoFallback:usedDemoFallback, request }
    };
  } catch (error) {
    return { status:'invalid-arguments', intent, capability:def, missingArguments:[], error:error?.message || String(error), toolRequest:null };
  }
}

module.exports = {
  ANIMATION_CAPABILITY_REGISTRY,
  animationCapabilityList,
  animationCapabilityPrompt,
  normalizeVisualizationIntent,
  resolveVisualizationIntent
};
