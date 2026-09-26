"""Recipes for the runnable examples that ship with the classroom code library.

Each group assembles one complete program:

    common headers
  + group["extra"]        hand-written glue the listing assumes but never shows
                          (a `#define StackElementType char`, a sibling type block, a helper)
  + group["parts"]        listings inlined **verbatim**, marked with a comment
  + group["driver"]       our `main`, which exercises the listing and prints something readable

`fixes` carries the book's own typos: a patch is applied to a listing before it is inlined, and the
group `note` says so in the UI. Nothing is patched silently.

Everything here is compiled and run by `scripts/build-code-library.py`; the stdout it records is what
the resource ships as the expected output. Keep the programs C99-clean and free of platform-specific
calls (no `conio.h`, `kbhit`, `flushall`, `gets`): the sandbox that runs them is Linux.
"""

import io as _io
import json as _json
import os as _os

_LIBRARY = _os.path.join(
    _os.path.dirname(_os.path.abspath(__file__)), "..", "..",
    "backend", "spring", "src", "main", "resources",
    "classroom-code", "textbook", "library.json",
)

# ---------------------------------------------------------------- chapter 2, linear lists

SEQLIST_TYPE = """#define TRUE 1
#define FALSE 0
#define ElemType int
#define MAXSIZE 100
typedef struct
{
    ElemType elem[MAXSIZE];
    int last;
} SeqList;
"""

LINKLIST_TYPE = """#define TRUE 1
#define FALSE 0
typedef char ElemType;
typedef struct Node
{
    ElemType data;
    struct Node *next;
} Node, *LinkList;
"""

LINKQUEUE_TYPE = """#define TRUE 1
#define FALSE 0
typedef int QueueElementType;
typedef struct Node
{
    QueueElementType data;
    struct Node *next;
} LinkQueueNode;
typedef struct
{
    LinkQueueNode *front;
    LinkQueueNode *rear;
} LinkQueue;
"""

TSMATRIX_TYPE = """#define MAXSIZE 100
#define ElementType int

typedef struct
{
    int row,col;
    ElementType e;
} Triple;

typedef struct
{
    Triple data[MAXSIZE+1];
    int m,n,len;
} TSMatrix;
"""

TRANSPOSE_BOTH_DRIVER = r"""
/* 同一个矩阵先用"列序递增"转一次，再用"按位快速"转一次，结果应当一样。 */
int main(void)
{
    TSMatrix A, B, C;
    int i;

    A.m = 3; A.n = 3; A.len = 3;
    A.data[1].row = 1; A.data[1].col = 1; A.data[1].e = 5;
    A.data[2].row = 2; A.data[2].col = 3; A.data[2].e = 7;
    A.data[3].row = 3; A.data[3].col = 2; A.data[3].e = 9;

    TransposeTSMatrix(A, &B);
    printf("列序递增转置 (%d 行 %d 列):\n", B.m, B.n);
    for (i = 1; i <= B.len; i++)
        printf("  (%d,%d)=%d\n", B.data[i].row, B.data[i].col, B.data[i].e);

    FastTransposeTSMatrix(A, &C);
    printf("按位快速转置 (%d 行 %d 列):\n", C.m, C.n);
    for (i = 1; i <= C.len; i++)
        printf("  (%d,%d)=%d\n", C.data[i].row, C.data[i].col, C.data[i].e);
    return 0;
}
"""

CROSSLIST_TYPE = """#define ElementType int

typedef struct OLNode
{
    int row,col;
    int value;
    struct OLNode *right;
    struct OLNode *down;
} OLNode, *OLink;

typedef struct
{
    OLink *row_head;
    OLink *col_head;
    int m,n,len;
} CrossList;
"""

PRINT_CROSSLIST = r"""
/* 打印函数是示例自己加的：顺着每行的链表把矩阵铺开。 */
void PrintCrossList(CrossList *M)
{
    OLink p;
    int i, j;

    printf("矩阵 %d 行 %d 列，%d 个非零元素:\n", M->m, M->n, M->len);
    for (i = 1; i <= M->m; i++)
    {
        for (j = 1; j <= M->n; j++)
        {
            int value = 0;
            for (p = M->row_head[i]; p != NULL; p = p->right)
                if (p->col == j)
                    value = p->value;
            printf("%4d", value);
        }
        printf("\n");
    }
}
"""

GLIST_TYPE = """typedef enum {ATOM, LIST} ElemTag;
typedef struct GLNode
{
    ElemTag tag;
    union
    {
        AtomType atom;
        struct
        {
            struct GLNode *hp, *tp;
        } htp;
    } atom_htp;
} GLNode, *GList;
"""

GLIST_GLUE = """typedef char AtomType;
#define OK 1
#define ERROR 0
"""

GLIST_HELPERS = r"""
/* 建表函数是示例自己加的：一个原子结点和一个"表"结点（表头 + 表尾）。 */
static GLNode *atom(char c)
{
    GLNode *p = (GLNode *)malloc(sizeof(GLNode));
    p->tag = ATOM;
    p->atom_htp.atom = c;
    return p;
}

static GLNode *cell(GLNode *head, GLNode *tail)
{
    GLNode *p = (GLNode *)malloc(sizeof(GLNode));
    p->tag = LIST;
    p->atom_htp.htp.hp = head;
    p->atom_htp.htp.tp = tail;
    return p;
}
"""

ADJMATRIX_PLAIN_DRIVER = r"""
int main(void)
{
    AdjMatrix G;
    int i, j;

    G.vexnum = 3;
    G.arcnum = 2;
    G.kind = DN;
    G.vexs[0] = 'A'; G.vexs[1] = 'B'; G.vexs[2] = 'C';
    for (i = 0; i < 3; i++)
        for (j = 0; j < 3; j++)
            G.arcs[i][j].adj = INFINITY;
    G.arcs[0][1].adj = 5;              /* A -> B，权 5 */
    G.arcs[1][2].adj = 7;              /* B -> C，权 7 */

    printf("有向网的邻接矩阵（32768 表示不可达）:\n");
    for (i = 0; i < G.vexnum; i++)
    {
        for (j = 0; j < G.vexnum; j++)
            printf("%7d", G.arcs[i][j].adj);
        printf("\n");
    }
    return 0;
}
"""

ADJMATRIX_DRIVER = r"""
int main(void)
{
    AdjMatrix G;
    int i, j;

    G.vexnum = 3;
    G.arcnum = 2;
    G.kind = DN;
    G.vexs[0] = 'A'; G.vexs[1] = 'B'; G.vexs[2] = 'C';
    for (i = 0; i < 3; i++)
        for (j = 0; j < 3; j++)
            G.arcs[i][j].adj = INFINITY;
    G.arcs[0][1].adj = 5;              /* A -> B，权 5 */
    G.arcs[1][2].adj = 7;              /* B -> C，权 7 */

    printf("有向网的邻接矩阵（32768 表示不可达）:\n");
    for (i = 0; i < G.vexnum; i++)
    {
        for (j = 0; j < G.vexnum; j++)
            printf("%7d", G.arcs[i][j].adj);
        printf("\n");
    }
    printf("顶点 'B' 的下标: %d\n", LocateVertex(&G, 'B'));
    return 0;
}
"""

SEQSTACK_TYPE = """#define TRUE 1
#define FALSE 0
#define Stack_Size 50
typedef char StackElementType;
typedef struct
{
    StackElementType elem[Stack_Size];
    int top;
} SeqStack;
"""

DQSTACK_TYPE = """#define TRUE 1
#define FALSE 0
#define M 100
typedef char StackElementType;
typedef struct
{
    StackElementType Stack[M];
    StackElementType top[2];
} DqStack;
"""

LINKSTACK_TYPE = """#define TRUE 1
#define FALSE 0
typedef char StackElementType;
typedef struct node
{
    StackElementType data;
    struct node *next;
} LinkStackNode, *LinkStack;
"""

LINKSTACK_TYPE_INT = LINKSTACK_TYPE.replace("typedef char StackElementType;", "typedef int StackElementType;")

SEQSTACK_CHAR_DRIVER = r"""
/* 示例把上面的基本运算串起来跑一遍：入栈、取栈顶、出栈，再看判空判满。 */
int main(void)
{
    SeqStack S;
    char word[] = "abcde";
    char x;
    int i;

    InitStack(&S);
    for (i = 0; word[i] != '\0'; i++)
        Push(&S, word[i]);

    printf("入栈后元素个数: %d\n", S.top + 1);
    GetTop(&S, &x);
    printf("栈顶元素: %c\n", x);
    printf("出栈顺序: ");
    while (!IsEmpty(&S))
    {
        Pop(&S, &x);
        printf("%c ", x);
    }
    printf("\n空栈再出栈: %d\n", Pop(&S, &x));
    printf("判满: %d\n", IsFull(&S));
    return 0;
}
"""

GRAPH_TRAVERSE_PROGRAM = r"""#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <malloc.h>

/* 这一张图：A-B、A-C、B-D、C-D、D-E，用邻接表存。 */
#define MAX_VERTEX_NUM 20
#define TRUE 1
#define FALSE 0

typedef char VertexData;

typedef struct ArcNode
{
    int adjvex;
    struct ArcNode *nextarc;
} ArcNode;

typedef struct VertexNode
{
    VertexData data;
    ArcNode *firstarc;
} VertexNode;

typedef struct
{
    VertexNode vertex[MAX_VERTEX_NUM];
    int vexnum, arcnum;
} AdjList;

int visited[MAX_VERTEX_NUM];
int pre[MAX_VERTEX_NUM];

void AddArc(AdjList *g, int i, int j)          /* 头插法建邻接表 */
{
    ArcNode *p = (ArcNode *)malloc(sizeof(ArcNode));
    p->adjvex = j;
    p->nextarc = g->vertex[i].firstarc;
    g->vertex[i].firstarc = p;
}

int FirstAdjVertex(AdjList *g, int v0)
{
    return g->vertex[v0].firstarc != NULL ? g->vertex[v0].firstarc->adjvex : -1;
}

int NextAdjVertex(AdjList *g, int v0, int w)
{
    ArcNode *p = g->vertex[v0].firstarc;
    while (p != NULL && p->adjvex != w)
        p = p->nextarc;
    return (p != NULL && p->nextarc != NULL) ? p->nextarc->adjvex : -1;
}

void visit(AdjList *g, int v0, int *order, int *count)
{
    (void)g;
    order[(*count)++] = v0;
}

/* 深度优先搜索（递归） */
void DepthFirstSearch(AdjList *g, int v0, int *order, int *count)
{
    int w;
    visit(g, v0, order, count);
    visited[v0] = TRUE;
    w = FirstAdjVertex(g, v0);
    while (w != -1)
    {
        if (!visited[w])
            DepthFirstSearch(g, w, order, count);
        w = NextAdjVertex(g, v0, w);
    }
}

/* 深度优先搜索（用栈代替递归，进栈时可能重复，出栈时判重） */
void DepthFirstSearchStack(AdjList *g, int v0, int *order, int *count)
{
    int stack[MAX_VERTEX_NUM];
    int top = -1, v, w;

    stack[++top] = v0;
    while (top >= 0)
    {
        v = stack[top--];
        if (!visited[v])
        {
            visit(g, v, order, count);
            visited[v] = TRUE;
        }
        w = FirstAdjVertex(g, v);
        while (w != -1)
        {
            if (!visited[w])
                stack[++top] = w;
            w = NextAdjVertex(g, v, w);
        }
    }
}

/* 广度优先搜索（队列） */
void BreadthFirstSearch(AdjList *g, int v0, int *order, int *count)
{
    int queue[MAX_VERTEX_NUM];
    int front = 0, rear = 0, v, w;

    visit(g, v0, order, count);
    visited[v0] = TRUE;
    queue[rear++] = v0;
    while (front < rear)
    {
        v = queue[front++];
        w = FirstAdjVertex(g, v);
        while (w != -1)
        {
            if (!visited[w])
            {
                visit(g, w, order, count);
                visited[w] = TRUE;
                queue[rear++] = w;
            }
            w = NextAdjVertex(g, v, w);
        }
    }
}

/* 找一条 u 到 v 的简单路径，pre[] 记前驱 */
int DFS_path(AdjList *g, int u, int v)
{
    int w;
    if (u == v)
        return TRUE;
    for (w = FirstAdjVertex(g, u); w != -1; w = NextAdjVertex(g, u, w))
        if (pre[w] == -1)
        {
            pre[w] = u;
            if (DFS_path(g, w, v))
                return TRUE;
        }
    return FALSE;
}

void PrintOrder(AdjList *g, int *order, int count)
{
    int i;
    for (i = 0; i < count; i++)
        printf("%c ", g->vertex[order[i]].data);
}

void PrintPath(AdjList *g, int v)
{
    if (pre[v] != v)
        PrintPath(g, pre[v]);
    printf("%c ", g->vertex[v].data);
}

int main(void)
{
    AdjList g;
    int order[MAX_VERTEX_NUM];
    int count, i;

    g.vexnum = 5;
    g.arcnum = 5;
    for (i = 0; i < g.vexnum; i++)
    {
        g.vertex[i].data = (char)('A' + i);
        g.vertex[i].firstarc = NULL;
    }
    AddArc(&g, 0, 1); AddArc(&g, 0, 2); AddArc(&g, 1, 3); AddArc(&g, 2, 3); AddArc(&g, 3, 4);

    for (i = 0; i < g.vexnum; i++)
        visited[i] = FALSE;
    count = 0;
    DepthFirstSearch(&g, 0, order, &count);
    printf("递归深度优先（从 A 出发）: ");
    PrintOrder(&g, order, count);
    printf("\n");

    for (i = 0; i < g.vexnum; i++)
        visited[i] = FALSE;
    count = 0;
    DepthFirstSearchStack(&g, 0, order, &count);
    printf("非递归深度优先: ");
    PrintOrder(&g, order, count);
    printf("\n");

    for (i = 0; i < g.vexnum; i++)
        visited[i] = FALSE;
    count = 0;
    BreadthFirstSearch(&g, 0, order, &count);
    printf("广度优先: ");
    PrintOrder(&g, order, count);
    printf("\n");

    for (i = 0; i < g.vexnum; i++)
        pre[i] = -1;
    pre[0] = 0;
    printf("A 到 E 的一条简单路径: ");
    if (DFS_path(&g, 0, 4))
        PrintPath(&g, 4);
    else
        printf("不存在");
    printf("\n");
    return 0;
}
"""

PRIM_PROGRAM = r"""#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define MAX_VERTEX_NUM 10
#define INFINITY 32768
#define TRUE 1
#define FALSE 0
#define Error -1

typedef char VertexData;

typedef struct
{
    int adj;
} ArcNode;

typedef struct
{
    VertexData vexs[MAX_VERTEX_NUM];
    ArcNode arcs[MAX_VERTEX_NUM][MAX_VERTEX_NUM];
    int vexnum, arcnum;
} AdjMatrix;

typedef struct
{
    VertexData adjvex;
    int lowcost;
} Closedge;

int LocateVertex(AdjMatrix *G, VertexData v)
{
    int j = Error, k;
    for (k = 0; k < G->vexnum; k++)
        if (G->vexs[k] == v)
        {
            j = k;
            break;
        }
    return j;
}

int Minium(Closedge closedge[], int n)      /* 在还没并入 U 的顶点里挑权最小的 */
{
    int i, k = -1, min = INFINITY;
    for (i = 0; i < n; i++)
        if (closedge[i].lowcost > 0 && closedge[i].lowcost < min)
        {
            min = closedge[i].lowcost;
            k = i;
        }
    return k;
}

void MiniSpanTree_Prim(AdjMatrix gn, VertexData u)
{
    Closedge closedge[MAX_VERTEX_NUM];
    int k = LocateVertex(&gn, u);
    int i, e, k0, total = 0;

    closedge[k].lowcost = 0;                 /* U = {u} */
    for (i = 0; i < gn.vexnum; i++)
        if (i != k)
        {
            closedge[i].adjvex = u;
            closedge[i].lowcost = gn.arcs[k][i].adj;
        }

    for (e = 1; e <= gn.vexnum - 1; e++)
    {
        k0 = Minium(closedge, gn.vexnum);
        if (k0 < 0)
            break;
        printf("第 %d 条边: %c - %c，权 %d\n", e, closedge[k0].adjvex, gn.vexs[k0], closedge[k0].lowcost);
        total += closedge[k0].lowcost;
        closedge[k0].lowcost = 0;            /* v0 并入 U */
        for (i = 0; i < gn.vexnum; i++)
            if (gn.arcs[k0][i].adj < closedge[i].lowcost)
            {
                closedge[i].lowcost = gn.arcs[k0][i].adj;
                closedge[i].adjvex = gn.vexs[k0];
            }
    }
    printf("最小生成树的权之和: %d\n", total);
}

int main(void)
{
    AdjMatrix gn;
    int i, j;
    int edges[][3] = {
        {'A', 'B', 6}, {'A', 'C', 1}, {'A', 'D', 5},
        {'B', 'C', 5}, {'B', 'E', 3},
        {'C', 'D', 5}, {'C', 'E', 6}, {'C', 'F', 4},
        {'D', 'F', 2}, {'E', 'F', 6},
    };
    int count = (int)(sizeof(edges) / sizeof(edges[0]));

    gn.vexnum = 6;
    gn.arcnum = count;
    gn.vexs[0] = 'A'; gn.vexs[1] = 'B'; gn.vexs[2] = 'C';
    gn.vexs[3] = 'D'; gn.vexs[4] = 'E'; gn.vexs[5] = 'F';
    for (i = 0; i < gn.vexnum; i++)
        for (j = 0; j < gn.vexnum; j++)
            gn.arcs[i][j].adj = (i == j) ? 0 : INFINITY;
    for (i = 0; i < count; i++)
    {
        int a = LocateVertex(&gn, (VertexData)edges[i][0]);
        int b = LocateVertex(&gn, (VertexData)edges[i][1]);
        gn.arcs[a][b].adj = edges[i][2];
        gn.arcs[b][a].adj = edges[i][2];
    }

    printf("从 A 出发构造最小生成树:\n");
    MiniSpanTree_Prim(gn, 'A');
    return 0;
}
"""

TOPO_CRITICAL_PROGRAM = r"""#include <stdio.h>
#include <stdlib.h>

/* v1..v6 就是 A..F；活动：A->B(3) A->C(2) B->D(2) B->E(3) C->D(4) C->F(3) D->F(2) E->F(1) */
#define MAX_VERTEX_NUM 10
#define TRUE 1
#define FALSE 0
#define Error -1
#define Ok 1

typedef char VertexData;

typedef struct ArcNode
{
    int adjvex;
    int weight;                       /* 活动持续时间 */
    struct ArcNode *nextarc;
} ArcNode;

typedef struct
{
    VertexData data;
    ArcNode *firstarc;
} VertexNode;

typedef struct
{
    VertexNode vertex[MAX_VERTEX_NUM];
    int vexnum, arcnum;
} AdjList;

int ve[MAX_VERTEX_NUM];               /* 每个顶点的最早发生时间 */

void AddArc(AdjList *g, int i, int j, int weight)   /* 头插，邻接表里同一顶点的出弧顺序会反过来 */
{
    ArcNode *p = (ArcNode *)malloc(sizeof(ArcNode));
    p->adjvex = j;
    p->weight = weight;
    p->nextarc = g->vertex[i].firstarc;
    g->vertex[i].firstarc = p;
}

/* 求各顶点的入度 */
void FindID(AdjList G, int indegree[MAX_VERTEX_NUM])
{
    int i;
    ArcNode *p;

    for (i = 0; i < G.vexnum; i++)
        indegree[i] = 0;
    for (i = 0; i < G.vexnum; i++)
    {
        p = G.vertex[i].firstarc;
        while (p != NULL)
        {
            indegree[p->adjvex]++;
            p = p->nextarc;
        }
    }
}

/* 拓扑排序：顺带把各顶点的最早发生时间算出来 */
int TopoOrder(AdjList G, int topological[])
{
    int indegree[MAX_VERTEX_NUM];
    int stack[MAX_VERTEX_NUM];
    int top = -1, count = 0, i, j, k;
    ArcNode *p;

    FindID(G, indegree);
    for (i = 0; i < G.vexnum; i++)
    {
        ve[i] = 0;
        if (indegree[i] == 0)
            stack[++top] = i;
    }

    while (top >= 0)
    {
        j = stack[top--];
        topological[count++] = j;
        p = G.vertex[j].firstarc;
        while (p != NULL)
        {
            k = p->adjvex;
            if (--indegree[k] == 0)
                stack[++top] = k;
            if (ve[j] + p->weight > ve[k])
                ve[k] = ve[j] + p->weight;
            p = p->nextarc;
        }
    }
    return count < G.vexnum ? Error : Ok;
}

void CriticalPath(AdjList G)
{
    int topological[MAX_VERTEX_NUM];
    int vl[MAX_VERTEX_NUM];
    int i, j, k, dut, ei, li;
    ArcNode *p;

    if (TopoOrder(G, topological) != Ok)
    {
        printf("这个有向图里有回路，没有关键路径\n");
        return;
    }

    for (i = 0; i < G.vexnum; i++)
        vl[i] = ve[G.vexnum - 1];       /* 汇点的最迟发生时间就是它的最早发生时间 */
    for (i = G.vexnum - 1; i >= 0; i--) /* 按逆拓扑顺序求各顶点的 vl */
    {
        j = topological[i];
        p = G.vertex[j].firstarc;
        while (p != NULL)
        {
            k = p->adjvex;
            dut = p->weight;
            if (vl[k] - dut < vl[j])
                vl[j] = vl[k] - dut;
            p = p->nextarc;
        }
    }

    printf("活动   持续时间  最早开始  最迟开始  是否关键\n");
    for (j = 0; j < G.vexnum; j++)
    {
        p = G.vertex[j].firstarc;
        while (p != NULL)
        {
            k = p->adjvex;
            dut = p->weight;
            ei = ve[j];
            li = vl[k] - dut;
            printf("%c -> %c %6d %9d %9d      %s\n",
                   G.vertex[j].data, G.vertex[k].data, dut, ei, li, ei == li ? "是" : "否");
            p = p->nextarc;
        }
    }
}

int main(void)
{
    AdjList g;
    int topological[MAX_VERTEX_NUM];
    int i, order[MAX_VERTEX_NUM];
    struct { int from, to, weight; } activities[] = {
        {0, 1, 3}, {0, 2, 2}, {1, 3, 2}, {1, 4, 3},
        {2, 3, 4}, {2, 5, 3}, {3, 5, 2}, {4, 5, 1},
    };
    int total = (int)(sizeof(activities) / sizeof(activities[0]));

    g.vexnum = 6;
    g.arcnum = total;
    for (i = 0; i < g.vexnum; i++)
    {
        g.vertex[i].data = (char)('A' + i);
        g.vertex[i].firstarc = NULL;
    }
    /* 倒着加，让邻接表里的出弧顺序和输入顺序一致 */
    for (i = total - 1; i >= 0; i--)
        AddArc(&g, activities[i].from, activities[i].to, activities[i].weight);

    FindID(g, order);
    printf("各顶点的入度: ");
    for (i = 0; i < g.vexnum; i++)
        printf("%c=%d ", g.vertex[i].data, order[i]);
    printf("\n");

    TopoOrder(g, topological);
    printf("拓扑排序结果: ");
    for (i = 0; i < g.vexnum; i++)          /* 排序成功时 vexnum 个顶点都会被填上 */
        printf("%c ", g.vertex[topological[i]].data);
    printf("\n");

    printf("各顶点的最早发生时间 ve: ");
    for (i = 0; i < g.vexnum; i++)
        printf("%c=%d ", g.vertex[i].data, ve[i]);
    printf("\n");

    CriticalPath(g);
    return 0;
}
"""

SHORTEST_PROGRAM = r"""#include <stdio.h>
#include <stdlib.h>

#define MAX_VERTEX_NUM 10
#define INFINITY 32768
#define TRUE 1
#define FALSE 0
#define Error -1
#define Ok 1

typedef char VertexData;

typedef struct
{
    int adj;
} ArcNode;

typedef struct
{
    VertexData vexs[MAX_VERTEX_NUM];
    ArcNode arcs[MAX_VERTEX_NUM][MAX_VERTEX_NUM];
    int vexnum, arcnum;
} AdjMatrix;

int LocateVertex(AdjMatrix *G, VertexData v)
{
    int j = Error, k;
    for (k = 0; k < G->vexnum; k++)
        if (G->vexs[k] == v)
        {
            j = k;
            break;
        }
    return j;
}

void InitMatrix(AdjMatrix *g, int n)
{
    int i, j;
    g->vexnum = n;
    g->arcnum = 0;
    for (i = 0; i < n; i++)
    {
        g->vexs[i] = (char)('A' + i);
        for (j = 0; j < n; j++)
            g->arcs[i][j].adj = (i == j) ? 0 : INFINITY;
    }
}

void AddArc(AdjMatrix *g, int i, int j, int weight)
{
    g->arcs[i][j].adj = weight;
    g->arcnum++;
}

/* Dijkstra：dist[i] 是当前最短路径长度，path[i] 记下前驱 */
void ShortestPath_DJS(AdjMatrix g, int v0, int dist[MAX_VERTEX_NUM], int path[MAX_VERTEX_NUM])
{
    int s[MAX_VERTEX_NUM];
    int i, k, t, min;

    for (i = 0; i < g.vexnum; i++)
    {
        dist[i] = g.arcs[v0][i].adj;
        path[i] = dist[i] < INFINITY ? v0 : -1;
        s[i] = FALSE;
    }
    s[v0] = TRUE;
    dist[v0] = 0;

    for (t = 1; t <= g.vexnum - 1; t++)
    {
        min = INFINITY;
        k = -1;
        for (i = 0; i < g.vexnum; i++)
            if (!s[i] && dist[i] < min)
            {
                k = i;
                min = dist[i];
            }
        if (k < 0)
            break;
        s[k] = TRUE;
        for (i = 0; i < g.vexnum; i++)
            if (!s[i] && g.arcs[k][i].adj != INFINITY && dist[k] + g.arcs[k][i].adj < dist[i])
            {
                dist[i] = dist[k] + g.arcs[k][i].adj;
                path[i] = k;
            }
    }
}

void PrintPath(AdjMatrix g, int path[], int v)
{
    if (path[v] != -1 && path[v] != v)
        PrintPath(g, path, path[v]);
    printf("%c ", g.vexs[v]);
}

/* Floyd：dist[i][j] 是 vi 到 vj 的最短路径长度 */
void ShortestPath_Floyd(AdjMatrix g, int dist[MAX_VERTEX_NUM][MAX_VERTEX_NUM])
{
    int i, j, k;

    for (i = 0; i < g.vexnum; i++)
        for (j = 0; j < g.vexnum; j++)
            dist[i][j] = g.arcs[i][j].adj;

    for (k = 0; k < g.vexnum; k++)
        for (i = 0; i < g.vexnum; i++)
            for (j = 0; j < g.vexnum; j++)
                if (dist[i][k] + dist[k][j] < dist[i][j])
                    dist[i][j] = dist[i][k] + dist[k][j];
}

int main(void)
{
    AdjMatrix g;
    int dist[MAX_VERTEX_NUM], path[MAX_VERTEX_NUM];
    int all[MAX_VERTEX_NUM][MAX_VERTEX_NUM];
    int i, j;

    InitMatrix(&g, 4);                 /* A B C D */
    AddArc(&g, 0, 1, 1);               /* A -> B，1 */
    AddArc(&g, 0, 2, 4);               /* A -> C，4 */
    AddArc(&g, 1, 2, 2);               /* B -> C，2 */
    AddArc(&g, 1, 3, 6);               /* B -> D，6 */
    AddArc(&g, 2, 3, 3);               /* C -> D，3 */

    ShortestPath_DJS(g, 0, dist, path);
    printf("Dijkstra：从 A 到各顶点的最短路径\n");
    for (i = 1; i < g.vexnum; i++)
    {
        printf("  A -> %c 长度 %d，路径: ", g.vexs[i], dist[i]);
        PrintPath(g, path, i);
        printf("\n");
    }

    ShortestPath_Floyd(g, all);
    printf("Floyd：每对顶点之间的最短路径长度（%d 表示不可达）\n", INFINITY);
    printf("     ");
    for (j = 0; j < g.vexnum; j++)
        printf("%6c", g.vexs[j]);
    printf("\n");
    for (i = 0; i < g.vexnum; i++)
    {
        printf("%4c ", g.vexs[i]);
        for (j = 0; j < g.vexnum; j++)
            printf("%6d", all[i][j]);
        printf("\n");
    }
    return 0;
}
"""

ORTHLIST_DRIVER = r"""
int main(void)
{
    OrthList g;
    ArcNode a1, a2;

    g.vexnum = 3;
    g.arcnum = 2;
    g.kind = DG;
    g.vertex[0].data = 'A'; g.vertex[0].firstin = NULL; g.vertex[0].firstout = &a1;
    g.vertex[1].data = 'B'; g.vertex[1].firstin = &a1;  g.vertex[1].firstout = &a2;
    g.vertex[2].data = 'C'; g.vertex[2].firstin = &a2;  g.vertex[2].firstout = NULL;

    a1.tailvex = 0; a1.headvex = 1; a1.tlink = NULL; a1.hlink = NULL;
    a2.tailvex = 1; a2.headvex = 2; a2.tlink = NULL; a2.hlink = NULL;

    printf("顶点 'C' 的下标: %d\n", LocateVertex(&g, 'C'));
    printf("顶点 'Z' 找不到时返回: %d\n", LocateVertex(&g, 'Z'));
    printf("A 的第一条出弧指向 %c\n", g.vertex[g.vertex[0].firstout->headvex].data);
    return 0;
}
"""

ORTHLIST_LOCATE = r"""/* 原段调用的 LocateVertex 在书里由另一个文件给出，示例在这里补出实现。 */
int LocateVertex(OrthList *G, VertexData v)
{
    int j = Error, k;
    for (k = 0; k < G->vexnum; k++)
        if (G->vertex[k].data == v)
        {
            j = k;
            break;
        }
    return j;
}
"""


def _listing(fragment_id: str) -> str:
    """The listing exactly as it ships, so a hand-assembled program cannot drift from the resource."""
    with _io.open(_LIBRARY, encoding="utf-8") as handle:
        library = _json.load(handle)
    for chapter in library["chapters"]:
        for fragment in chapter["fragments"]:
            if fragment["id"] == fragment_id:
                return fragment["code"]
    raise KeyError(fragment_id)


# Assembled by hand: the type, then LocateVertex, then the function that calls it, then a main.
ORTHLIST_PROGRAM = (
    "#include <stdio.h>\n#include <stdlib.h>\n#include <malloc.h>\n\n"
    + _listing("ch07-OrthList").replace(
        "typedef char VertexNode;", "/* 与下面的结构体同名的 typedef，示例删掉 */")
    + "\n"
    + ORTHLIST_LOCATE
    + "\n"
    + _listing("ch07-7.2").replace('#include "OrthList.h"\n', "").replace(
        "void main()",
        "/* 书里的这个 main 要从键盘逐条输入顶点和弧，示例换成下面手工建表的 main。 */\nvoid interactive_main()")
    + ORTHLIST_DRIVER
)

GROUPS = [
    # -------------------------------------------------------------------- ch02
    {
        "id": "ch02-linklist",
        "members": ["ch02-linklist"],
        "note": "示例补了一个 main：头插 a、b、c 三个结点，再顺着 next 打印（这一段只是类型定义）。",
        "parts": ["ch02-linklist"],
        "driver": r"""
/* 示例：用上面的结点类型手工搭一条头插法链表，再顺着 next 打印。 */
int main(void)
{
    Node *head = NULL;
    Node *node;
    char letters[] = "abc";
    int i;

    for (i = 0; i < 3; i++)
    {
        node = (Node *)malloc(sizeof(Node));
        node->data = letters[i];
        node->next = head;
        head = node;
    }

    printf("头插法建表后的顺序: ");
    for (node = head; node != NULL; node = node->next)
        printf("%c ", node->data);
    printf("\n");

    for (node = head; node != NULL; )
    {
        Node *next = node->next;
        free(node);
        node = next;
    }
    return 0;
}
""",
        "expect": "头插法建表后的顺序: c b a \n",
    },
    {
        "id": "ch02-linklist1",
        "members": ["ch02-linklist1"],
        "note": "示例补了一个 main：头插 1、2、3 三个结点，再顺着 next 打印（这一段只是类型定义）。",
        "parts": ["ch02-linklist1"],
        "driver": r"""
/* 示例：把上面的结点类型换成整型数据用一遍，头插 1、2、3 后打印。 */
int main(void)
{
    Node *head = NULL;
    Node *node;
    int values[] = {1, 2, 3};
    int i;

    for (i = 0; i < 3; i++)
    {
        node = (Node *)malloc(sizeof(Node));
        node->data = values[i];
        node->next = head;
        head = node;
    }

    printf("头插法建表后的顺序: ");
    for (node = head; node != NULL; node = node->next)
        printf("%d ", node->data);
    printf("\n");
    return 0;
}
""",
        "expect": "头插法建表后的顺序: 3 2 1 \n",
    },
    {
        "id": "ch02-seqlist",
        "members": ["ch02-seqlist"],
        "note": "示例补了一个 main：直接在 elem[] 上模拟尾插和改写，看 last 这个下标怎么随操作变化（这一段只是类型定义）。",
        "parts": ["ch02-seqlist"],
        "driver": r"""
/* 示例：直接在 elem[] 上模拟尾插和改写，看 last 这个下标是怎么随操作变化的。 */
int main(void)
{
    SeqList L;
    int i;

    L.last = -1;                       /* 空表 */
    for (i = 1; i <= 5; i++)
    {
        L.last++;
        L.elem[L.last] = i * 10;       /* 尾插 */
    }

    printf("长度: %d\n", L.last + 1);
    printf("元素: ");
    for (i = 0; i <= L.last; i++)
        printf("%d ", L.elem[i]);
    printf("\n");

    L.elem[2] = 99;                    /* 直接把第 3 个元素改掉 */
    printf("改写第 3 个元素后: ");
    for (i = 0; i <= L.last; i++)
        printf("%d ", L.elem[i]);
    printf("\n");
    return 0;
}
""",
        "expect": "长度: 5\n元素: 10 20 30 40 50 \n改写第 3 个元素后: 10 20 99 40 50 \n",
    },
    {
        "id": "ch02-staticlist",
        "members": ["ch02-staticlist"],
        "note": "示例补了一个 main：用 cursor 串起 4 个结点，再跟着 cursor 走一遍（这一段只是类型定义）。",
        "parts": ["ch02-staticlist"],
        "driver": r"""
/* 示例：静态链表靠 cursor 串起来，跟着 cursor 走就是遍历。 */
int main(void)
{
    StaticList L;
    int i;

    for (i = 0; i < Maxsize; i++)
    {
        L[i].data = (char)('A' + i);
        L[i].cursor = -1;
    }
    L[0].cursor = 1;                   /* A -> B -> C -> D */
    L[1].cursor = 2;
    L[2].cursor = 3;

    printf("静态链表: ");
    for (i = 0; i != -1; i = L[i].cursor)
        printf("%c ", L[i].data);
    printf("\n");
    printf("第 3 个结点的后继下标: %d\n", L[2].cursor);
    return 0;
}
""",
        "expect": "静态链表: A B C D \n第 3 个结点的后继下标: 3\n",
    },
    {
        "id": "ch02-polylist",
        "members": ["ch02-polylist"],
        "parts": ["ch02-polylist"],
        # The listing writes `Polynode *next;` inside the struct that is still being defined, which C
        # cannot resolve; `struct Polynode *next;` is what the book means. Typo kept visible here.
        "fixes": {"ch02-polylist": [("Polynode *next;", "struct Polynode *next;")]},
        "note": "原段结构体里写的是 Polynode *next（此时 Polynode 这个名字还没定义），示例改成 struct Polynode *next。",
        "driver": r"""
/* 示例：手工串出 3x^2 + 5x + 7 三个结点，看看多项式在内存里长什么样。 */
int main(void)
{
    Polynode *a = (Polynode *)malloc(sizeof(Polynode));
    Polynode *b = (Polynode *)malloc(sizeof(Polynode));
    Polynode *c = (Polynode *)malloc(sizeof(Polynode));
    Polynode *p;

    a->coef = 3; a->exp = 2; a->next = b;
    b->coef = 5; b->exp = 1; b->next = c;
    c->coef = 7; c->exp = 0; c->next = NULL;

    printf("多项式: ");
    for (p = a; p != NULL; p = p->next)
    {
        printf("%d", p->coef);
        if (p->exp > 0)
            printf("x^%d", p->exp);
        if (p->next != NULL)
            printf(" + ");
    }
    printf("\n");
    return 0;
}
""",
        "expect": "多项式: 3x^2 + 5x^1 + 7\n",
    },
    {
        "id": "ch02-clinklist",
        "members": ["ch02-clinklist"],
        "note": "示例补了一个 main：读一串整数建循环链表，走一圈确认最后一个结点指回头结点。",
        "parts": ["ch02-clinklist"],
        "driver": r"""
/* 示例：建表函数读一串整数（-1 结束），每读一个就插在头结点后面。
   头结点的指针域最终绕回头结点本身——这就是"循环"。 */
int main(void)
{
    Node head;
    Node *p;
    Node *last = &head;

    head.data = -1;
    head.next = &head;
    create_clinklist(&head);

    printf("循环链表的内容: ");
    for (p = head.next; p != &head; p = p->next)
    {
        printf("%d ", p->data);
        last = p;
    }
    printf("\n最后一个结点指回头结点: %d\n", last->next == &head ? 1 : 0);
    return 0;
}
""",
        "stdin": "1 2 3 -1\n",
        "expect": "请输入循环链表的元素 (以-1结束):\n循环链表的内容: 3 2 1 \n最后一个结点指回头结点: 1\n",
    },
    {
        "id": "ch02-dlinklist",
        "members": ["ch02-dlinklist"],
        "note": "示例补了一个 main：读一个字符串建双向循环链表，顺着 next 正走、顺着 prior 倒走各一遍。",
        "parts": ["ch02-dlinklist"],
        "driver": r"""
/* 示例：建表函数读字符串（$ 结束），prior 和 next 都要接上，
   所以既能顺着 next 正着走，也能顺着 prior 倒着走。 */
int main(void)
{
    DNode head;
    DNode *p;

    CreateList(&head);

    printf("正向 (next): ");
    for (p = head.next; p != &head; p = p->next)
        printf("%c ", p->data);
    printf("\n反向 (prior): ");
    for (p = head.prior; p != &head; p = p->prior)
        printf("%c ", p->data);
    printf("\n");
    return 0;
}
""",
        "stdin": "abc$\n",
        "expect": "正向 (next): a b c \n反向 (prior): c b a \n",
    },

    # -------------------------------------------------------------------- ch03
    {
        "id": "ch03-seqstack-h",
        "members": ["ch03-seqstack"],
        "parts": ["ch03-seqstack"],
        "extra": "#define StackElementType char",
        "driver": SEQSTACK_CHAR_DRIVER,
        "note": "示例补上了这一段用到的 StackElementType（原段只写了 Stack_Size），并加了一个 main。",
    },
    {
        "id": "ch03-seqstack-ops",
        "members": ["ch03-3.1", "ch03-3.2", "ch03-3.3", "ch03-3.4"],
        "parts": ["ch03-3.1", "ch03-3.2", "ch03-3.3", "ch03-3.4"],
        "extra": SEQSTACK_TYPE,
        # 3.3 prints `S->top= =-1`: two separate `=` signs, which is not C. The book means `==`.
        "fixes": {"ch03-3.3": [("S->top= =-1", "S->top==-1")]},
        # Only the four basic operations are in these listings, so the driver judges emptiness by
        # looking at the top index itself instead of calling the IsEmpty that lives elsewhere.
        "driver": r"""
/* 示例把顺序栈的四个基本运算串起来：入栈、取栈顶、出栈，空栈再出栈会返回 FALSE。 */
int main(void)
{
    SeqStack S;
    char word[] = "abcde";
    char x;
    int i;

    InitStack(&S);
    for (i = 0; word[i] != '\0'; i++)
        Push(&S, word[i]);

    printf("入栈后元素个数: %d\n", S.top + 1);
    GetTop(&S, &x);
    printf("栈顶元素: %c\n", x);
    printf("出栈顺序: ");
    while (S.top != -1)
    {
        Pop(&S, &x);
        printf("%c ", x);
    }
    printf("\n空栈再出栈: %d\n", Pop(&S, &x));
    return 0;
}
""",
        "note": "原段判空写成了 S->top= =-1（两个等号之间多了空格），示例改为 ==；另外补齐了顺序栈的类型定义和一个 main。",
    },
    {
        "id": "ch03-seqstack1-h",
        "members": ["ch03-seqstack1"],
        "parts": ["ch03-seqstack1"],
        "driver": SEQSTACK_CHAR_DRIVER,
        "note": "示例补了一个 main（这一段自带 StackElementType 定义）。",
    },
    {
        "id": "ch03-seqstack2-h",
        "members": ["ch03-seqstack2"],
        "parts": ["ch03-seqstack2"],
        "driver": r"""
/* 示例：这一段里整型栈、字符栈各有一套，还带着表达式求值要用的 In/Compare/Execute，
   示例把两套栈各跑一遍，再把三个工具函数各调一次。 */
int main(void)
{
    nStack nums;
    strStack signs;
    int values[] = {3, 8, 5};
    int number;
    char sign;
    char c;
    int i;

    nInitStack(&nums);
    for (i = 0; i < 3; i++)
        nPush(&nums, values[i]);
    printf("整型栈出栈: ");
    while (!nIsEmpty(&nums))
    {
        nPop(&nums, &number);
        printf("%d ", number);
    }
    printf("\n空栈再出栈: %d\n", nPop(&nums, &number));

    strInitStack(&signs);
    strPush(&signs, '+');
    strPush(&signs, '*');
    strGetTop(&signs, &sign);
    printf("字符栈栈顶: %c\n", sign);
    printf("字符栈出栈: ");
    while (!strIsEmpty(&signs))
    {
        strPop(&signs, &c);
        printf("%c ", c);
    }
    printf("\n");

    printf("In('+')=%d  In('a')=%d\n", In('+'), In('a'));
    printf("Compare('+','*')='%c'\n", Compare('+', '*'));
    printf("Execute(6,'*',7)=%d\n", Execute(6, '*', 7));
    return 0;
}
""",
        "note": "示例补了一个 main，把整型栈、字符栈和 In/Compare/Execute 各用一次。",
    },
    {
        "id": "ch03-dqstack-h",
        "members": ["ch03-dqstack"],
        "parts": ["ch03-dqstack"],
        "extra": "#define StackElementType char",
        "fixes": {"ch03-dqstack": [("return(FALSE)\n", "return(FALSE);\n")]},
        "note": "原段 Push 的 default 分支少了分号，示例补上（否则编不过）。",
        "driver": r"""
/* 示例：两栈共享同一片数组，一个从左边长、一个从右边长，碰头就是满。 */
int main(void)
{
    DqStack S;
    char x;

    InitStack(&S);
    Push(&S, 'a', 0);
    Push(&S, 'b', 0);
    Push(&S, 'z', 1);
    Push(&S, 'y', 1);

    printf("0 号栈 top=%d, 1 号栈 top=%d\n", S.top[0], S.top[1]);
    Pop(&S, &x, 0);
    printf("0 号栈弹出: %c\n", x);
    Pop(&S, &x, 1);
    printf("1 号栈弹出: %c\n", x);
    printf("空栈再弹: %d\n", Pop(&S, &x, 0) && Pop(&S, &x, 0) && 0);
    return 0;
}
""",
    },
    {
        "id": "ch03-dqstack-ops",
        "members": ["ch03-3.5", "ch03-3.6", "ch03-3.7"],
        "parts": ["ch03-3.5", "ch03-3.6", "ch03-3.7"],
        "extra": DQSTACK_TYPE,
        "fixes": {"ch03-3.6": [("return(FALSE)\n", "return(FALSE);\n")]},
        "note": "原段 Push 的 default 分支少了分号，示例补上；类型定义（DqStack、M）来自同章的 dqstack.h。",
        "driver": r"""
/* 示例：把两栈共享空间的三个运算串起来。 */
int main(void)
{
    DqStack S;
    char x;
    int i;

    InitStack(&S);
    for (i = 0; i < 3; i++)
        Push(&S, (char)('a' + i), 0);
    for (i = 0; i < 2; i++)
        Push(&S, (char)('x' + i), 1);

    printf("0 号栈: ");
    while (Pop(&S, &x, 0))
        printf("%c ", x);
    printf("\n1 号栈: ");
    while (Pop(&S, &x, 1))
        printf("%c ", x);
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch03-linkstack-h",
        "members": ["ch03-linkstack"],
        "parts": ["ch03-linkstack"],
        "extra": "#define StackElementType char",
        "note": "示例补上了这一段用到的 StackElementType，并在 main 里先把头结点建出来（这一段没有初始化函数）。",
        "driver": r"""
/* 示例：链栈就是没有头结点数据的单链表，Push 插在表头、Pop 摘表头。 */
int main(void)
{
    LinkStack top = (LinkStack)malloc(sizeof(LinkStackNode));
    char word[] = "xyz";
    char x;
    int i;

    top->next = NULL;
    for (i = 0; word[i] != '\0'; i++)
        Push(top, word[i]);

    printf("链栈出栈顺序: ");
    while (top->next != NULL)
    {
        Pop(top, &x);
        printf("%c ", x);
    }
    printf("\n空栈再出栈: %d\n", Pop(top, &x));
    free(top);
    return 0;
}
""",
    },
    {
        "id": "ch03-linkstack-ops",
        "members": ["ch03-3.8", "ch03-3.9"],
        "parts": ["ch03-3.8", "ch03-3.9"],
        "extra": LINKSTACK_TYPE,
        "note": "示例补齐链栈的结点类型，并补一个 main。",
        "driver": r"""
int main(void)
{
    LinkStack top = (LinkStack)malloc(sizeof(LinkStackNode));
    char word[] = "xyz";
    char x;
    int i;

    top->next = NULL;
    for (i = 0; word[i] != '\0'; i++)
        Push(top, word[i]);

    printf("链栈出栈顺序: ");
    while (top->next != NULL)
    {
        Pop(top, &x);
        printf("%c ", x);
    }
    printf("\n");
    free(top);
    return 0;
}
""",
    },
    {
        "id": "ch03-mltliststack-h",
        "members": ["ch03-mltliststack"],
        "parts": ["ch03-mltliststack"],
        "extra": "#define StackElementType int",
        "note": "示例补上了这一段用到的 StackElementType。",
        "driver": r"""
/* 示例：M 个链栈共用一个 top[] 数组，top[i] 就是第 i 号栈的栈顶。 */
int main(void)
{
    int i;
    int x;

    for (i = 0; i < M; i++)
    {
        top[i] = (LinkStack)malloc(sizeof(LinkStackNode));
        top[i]->next = NULL;
    }
    for (i = 1; i <= 3; i++)
        pushi(top, 0, i * 10);
    for (i = 1; i <= 2; i++)
        pushi(top, 1, 100 + i);

    printf("0 号栈出栈: ");
    while (Pop(top, 0, &x))
        printf("%d ", x);
    printf("\n1 号栈出栈: ");
    while (Pop(top, 1, &x))
        printf("%d ", x);
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch03-mltliststack-ops",
        "members": ["ch03-3.10", "ch03-3.11"],
        "parts": ["ch03-3.10", "ch03-3.11"],
        "extra": LINKSTACK_TYPE_INT.replace("#define TRUE 1", "#define M 10\n#define TRUE 1"),
        "note": "示例补齐链栈的结点类型和 M，并补一个 main。",
        "driver": r"""
int main(void)
{
    LinkStack top[3];
    int i;
    int x;

    for (i = 0; i < 3; i++)
    {
        top[i] = (LinkStack)malloc(sizeof(LinkStackNode));
        top[i]->next = NULL;
    }
    for (i = 1; i <= 3; i++)
        pushi(top, 0, i * 10);
    for (i = 1; i <= 2; i++)
        pushi(top, 1, 100 + i);

    printf("0 号栈出栈: ");
    while (Pop(top, 0, &x))
        printf("%d ", x);
    printf("\n1 号栈出栈: ");
    while (Pop(top, 1, &x))
        printf("%d ", x);
    printf("\n2 号栈是空的: %d\n", Pop(top, 2, &x) == 0);
    return 0;
}
""",
    },
    {
        "id": "ch03-linkqueue-ops",
        "members": ["ch03-3.18", "ch03-3.19", "ch03-3.20"],
        "parts": ["ch03-3.18", "ch03-3.19", "ch03-3.20"],
        "extra": LINKQUEUE_TYPE,
        "note": "示例补齐链队列的结点类型，并补一个 main。",
        "driver": r"""
/* 示例：链队列带着头结点，front 指向头结点，空队列就是 front == rear。 */
int main(void)
{
    LinkQueue Q;
    int i;
    int x;

    InitQueue(&Q);
    for (i = 1; i <= 3; i++)
        EnterQueue(&Q, i * 10);

    printf("出队顺序: ");
    while (DeleteQueue(&Q, &x))
        printf("%d ", x);
    printf("\n空队再出队: %d\n", DeleteQueue(&Q, &x));
    return 0;
}
""",
    },
    {
        "id": "ch03-seqqueue-h",
        "members": ["ch03-seqqueue"],
        "parts": ["ch03-seqqueue"],
        "extra": "#define QueueElementType int",
        "note": "示例补上了这一段用到的 QueueElementType。",
        "driver": r"""
/* 示例：循环队列用取模来循环使用数组空间，看 front、rear 是怎么绕回来的。 */
int main(void)
{
    SeqQueue Q;
    int i;
    int x;

    InitQueue(&Q);
    printf("空队判空: %d\n", IsEmpty(&Q));
    for (i = 1; i <= 4; i++)
        EnterQueue(&Q, i * 10);

    GetHead(&Q, &x);
    printf("队头元素: %d\n", x);
    printf("出队顺序: ");
    while (DeleteQueue(&Q, &x))
        printf("%d ", x);
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch03-seqqueue-ops",
        "members": ["ch03-3.21", "ch03-3.22", "ch03-3.23"],
        "parts": ["ch03-3.21", "ch03-3.22", "ch03-3.23"],
        "extra": """#define TRUE 1
#define FALSE 0
#define MAXSIZE 50
typedef int QueueElementType;
typedef struct
{
    QueueElementType element[MAXSIZE];
    int front;
    int rear;
} SeqQueue;
""",
        "note": "示例补齐循环队列的类型和 MAXSIZE，并补一个 main。",
        "driver": r"""
int main(void)
{
    SeqQueue Q;
    int i;
    int x;

    InitQueue(&Q);
    for (i = 1; i <= 3; i++)
        EnterQueue(&Q, i * 10);
    printf("front=%d rear=%d\n", Q.front, Q.rear);

    printf("出队顺序: ");
    while (DeleteQueue(&Q, &x))
        printf("%d ", x);
    printf("\n空队再出队: %d\n", DeleteQueue(&Q, &x));
    return 0;
}
""",
    },
    {
        "id": "ch03-seqqueue1-h",
        "members": ["ch03-seqqueue1"],
        "parts": ["ch03-seqqueue1"],
        "note": "示例补了一个 main。",
        "driver": r"""
int main(void)
{
    SeqQueue Q;
    int i;
    int x;

    InitQueue(&Q);
    for (i = 1; i <= 4; i++)
        EnterQueue(&Q, i * i);
    GetHead(&Q, &x);
    printf("队头元素: %d\n", x);
    printf("出队顺序: ");
    while (DeleteQueue(&Q, &x))
        printf("%d ", x);
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch03-seqqueue2-h",
        "members": ["ch03-seqqueue2"],
        "parts": ["ch03-seqqueue2"],
        "note": "示例补了一个 main。",
        "driver": r"""
int main(void)
{
    SeqQueue Q;
    char word[] = "abc";
    char x;
    int i;

    InitQueue(&Q);
    for (i = 0; word[i] != '\0'; i++)
        EnterQueue(&Q, word[i]);
    GetHead(&Q, &x);
    printf("队头元素: %c\n", x);
    printf("出队顺序: ");
    while (DeleteQueue(&Q, &x))
        printf("%c ", x);
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch03-yanghui",
        "members": ["ch03-3.24"],
        "parts": ["ch03-seqqueue1", "ch03-3.24"],
        "fixes": {"ch03-3.24": [('#include "seqqueue1.h"\n', "")]},
        "note": "示例把 #include \"seqqueue1.h\" 展开成同章的队列代码，并把它自己的 main 保留下来。",
        "stdin": "6\n",
    },
    {
        "id": "ch03-bracket",
        "members": ["ch03-3.12"],
        "parts": ["ch03-seqstack1", "ch03-3.12"],
        "fixes": {
            "ch03-3.12": [
                ('#include "seqstack1.h"\n', ""),
                ("gets(str);", 'fgets(str, sizeof(str), stdin);\n\tstr[strcspn(str, "\\n")] = \'\\0\';'),
            ],
        },
        "note": "示例把 #include \"seqstack1.h\" 展开成同章的栈代码，并把 gets 换成 fgets（新版编译器已不再提供 gets）。",
        "stdin": "(a[b]{c})\n",
    },
    {
        "id": "ch03-exp",
        "members": ["ch03-3.13"],
        "parts": ["ch03-seqstack2", "ch03-3.13"],
        "fixes": {"ch03-3.13": [('#include "seqstack2.h"\n', ""), ("#include <conio.h>\n", "")]},
        "note": "示例把 #include \"seqstack2.h\" 展开成同章的栈代码，并去掉只存在于 Windows 的 conio.h。",
        "stdin": "3+4*2#\n",
    },
    {
        "id": "ch03-hanoi",
        "members": ["ch03-3.14"],
        "parts": ["ch03-3.14"],
        "extra": r"""/* 原段调用了一个书里没给出的 move()，示例把它补成"打印这一步"。 */
void move(char x, int n, char z)
{
    printf("把 %d 号盘从 %c 柱移到 %c 柱\n", n, x, z);
}
""",
        "note": "示例补上了原段调用的 move()（书里没给实现，这里补成打印）和一个 main。",
        "driver": r"""
int main(void)
{
    printf("3 个盘子的移动过程:\n");
    hanoi(3, 'A', 'B', 'C');
    return 0;
}
""",
    },
    {
        "id": "ch03-fib",
        "members": ["ch03-3.16"],
        "parts": ["ch03-3.16"],
        "note": "示例补了一个 main，把前 11 项打出来。",
        "driver": r"""
int main(void)
{
    int i;
    printf("斐波那契数列前 11 项: ");
    for (i = 0; i <= 10; i++)
        printf("%d ", Fib(i));
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch03-fact",
        "members": ["ch03-3.17"],
        "parts": ["ch03-3.17"],
        "note": "示例补了一个 main，把 1! 到 10! 打出来。",
        "driver": r"""
int main(void)
{
    int i;
    for (i = 1; i <= 10; i++)
        printf("%2d! = %ld\n", i, Fact(i));
    return 0;
}
""",
    },
    {
        "id": "ch03-doctor",
        "members": ["ch03-doctor"],
        "parts": ["ch03-seqqueue", "ch03-doctor"],
        "fixes": {
            "ch03-doctor": [('#include "stdio.h"\n', ""), ('#include "seqqueue.h"\n', "")],
        },
        "extra": "#define QueueElementType int",
        "note": "示例把两个 #include 展开成同章的队列代码，并按 a 挂号 / n 就诊 / q 下班喂了一串命令。",
        "stdin": "a101nq",
    },

    # -------------------------------------------------------------------- ch04
    {
        "id": "ch04-heapstr",
        "members": ["ch04-heapstr"],
        "parts": ["ch04-heapstr"],
        "note": "示例补了一个 main：给 ch 申请空间、填上串、打印。",
        "driver": r"""
/* 示例：堆分配存储的串，ch 指向申请来的空间，len 记长度。 */
int main(void)
{
    HString s;
    char source[] = "data";
    int i;

    s.len = (int)strlen(source);
    s.ch = (char *)malloc((s.len + 1) * sizeof(char));
    for (i = 0; i <= s.len; i++)
        s.ch[i] = source[i];

    printf("串内容: %s\n", s.ch);
    printf("串长度: %d\n", s.len);
    printf("第 2 个字符: %c\n", s.ch[1]);
    free(s.ch);
    return 0;
}
""",
    },
    {
        "id": "ch04-lstr",
        "members": ["ch04-lstr"],
        "parts": ["ch04-lstr"],
        "note": "示例补了一个 main：串出一条 a→b→c 的结点链，再顺着 next 打印。",
        "driver": r"""
/* 示例：链式串每个字符占一个结点，head/tail 记账，len 记长度。 */
int main(void)
{
    BLString s;
    Block *node;
    char source[] = "abc";
    int i;

    s.head = NULL;
    s.tail = NULL;
    s.len = 0;
    for (i = 0; source[i] != '\0'; i++)
    {
        node = (Block *)malloc(sizeof(Block));
        node->ch = source[i];
        node->next = NULL;
        if (s.head == NULL)
            s.head = node;
        else
            s.tail->next = node;
        s.tail = node;
        s.len++;
    }

    printf("串内容: ");
    for (node = s.head; node != NULL; node = node->next)
        printf("%c", node->ch);
    printf("\n串长度: %d\n", s.len);
    return 0;
}
""",
    },
    {
        "id": "ch04-seqstring",
        "members": ["ch04-seqstring"],
        "parts": ["ch04-seqstring"],
        "note": "示例补了一个 main，把这一段的建串和输出函数连着跑一遍（输入 3abc 就是建一个 3 个字符的串）。",
        "stdin": "3abc",
        "driver": r"""
int main(void)
{
    SString s;
    createstring(&s);
    printf("\n串的内容: ");
    output(&s);
    printf("串长度: %d\n", s.len);
    return 0;
}
""",
    },

    # -------------------------------------------------------------------- ch05
    {
        "id": "ch05-5.1",
        "members": ["ch05-5.1"],
        "parts": ["ch05-5.1"],
        "extra": "#define ElementType int\n#define m 3\n#define n 2",
        "note": "示例补上了这一段用到的 ElementType 和 m、n 两个常量，再补一个 main。",
        "driver": r"""
int main(void)
{
    ElementType source[m][n] = {{1, 2}, {3, 4}, {5, 6}};
    ElementType dest[n][m];
    int i, j;

    printf("原矩阵 (%d 行 %d 列):\n", m, n);
    for (i = 0; i < m; i++)
    {
        for (j = 0; j < n; j++)
            printf("%4d", source[i][j]);
        printf("\n");
    }

    TransMatrix(source, dest);

    printf("转置后 (%d 行 %d 列):\n", n, m);
    for (i = 0; i < n; i++)
    {
        for (j = 0; j < m; j++)
            printf("%4d", dest[i][j]);
        printf("\n");
    }
    return 0;
}
""",
    },
    {
        "id": "ch05-5.2",
        "members": ["ch05-5.2"],
        "parts": ["ch05-5.2"],
        "extra": TSMATRIX_TYPE,
        "note": "示例补齐三元组表的类型定义和一个 main（A 是 3×3、3 个非零元素）。",
        "driver": r"""
int main(void)
{
    TSMatrix A, B;
    int i;

    A.m = 3; A.n = 3; A.len = 3;
    A.data[1].row = 1; A.data[1].col = 1; A.data[1].e = 5;
    A.data[2].row = 2; A.data[2].col = 3; A.data[2].e = 7;
    A.data[3].row = 3; A.data[3].col = 2; A.data[3].e = 9;

    TransposeTSMatrix(A, &B);

    printf("转置后 %d 行 %d 列，%d 个非零元素:\n", B.m, B.n, B.len);
    for (i = 1; i <= B.len; i++)
        printf("  (%d,%d)=%d\n", B.data[i].row, B.data[i].col, B.data[i].e);
    return 0;
}
""",
    },
    {
        "id": "ch05-5.3",
        "members": ["ch05-5.3"],
        "parts": ["ch05-5.3"],
        "extra": TSMATRIX_TYPE,
        "note": "示例补齐三元组表的类型定义和一个 main（A 是 3×3、3 个非零元素）。",
        "driver": r"""
int main(void)
{
    TSMatrix A, B;
    int i;

    A.m = 3; A.n = 3; A.len = 3;
    A.data[1].row = 1; A.data[1].col = 1; A.data[1].e = 5;
    A.data[2].row = 2; A.data[2].col = 3; A.data[2].e = 7;
    A.data[3].row = 3; A.data[3].col = 2; A.data[3].e = 9;

    FastTransposeTSMatrix(A, &B);

    printf("转置后 %d 行 %d 列，%d 个非零元素:\n", B.m, B.n, B.len);
    for (i = 1; i <= B.len; i++)
        printf("  (%d,%d)=%d\n", B.data[i].row, B.data[i].col, B.data[i].e);
    return 0;
}
""",
    },
    {
        "id": "ch05-array",
        "members": ["ch05-array"],
        "parts": ["ch05-array"],
        "extra": "#define ElementType int",
        "note": "示例补上了这一段用到的 ElementType，并补一个 main 把两种转置都跑一遍。",
        "driver": TRANSPOSE_BOTH_DRIVER,
    },
    {
        "id": "ch05-array1",
        "members": ["ch05-array1"],
        "parts": ["ch05-array1"],
        "note": "示例补了一个 main，两种转置各跑一遍对照结果。",
        "driver": TRANSPOSE_BOTH_DRIVER,
    },
    {
        "id": "ch05-array2",
        "members": ["ch05-array2"],
        "parts": ["ch05-array2"],
        "note": "示例补了一个 main，两种转置各跑一遍对照结果。",
        "driver": TRANSPOSE_BOTH_DRIVER,
    },
    {
        "id": "ch05-5.4",
        "members": ["ch05-5.4", "ch05-crosslistarray"],
        "parts": ["ch05-5.4"],
        "extra": CROSSLIST_TYPE + PRINT_CROSSLIST,
        "fixes": {
            "ch05-5.4": [
                ('#include "crosslistarray.h"\n', ""),
                # The listing nulls the two head arrays *after* allocating them, which makes the
                # very next write crash; each entry has to be nulled instead.
                ("M->row_head=M->col_head=NULL;",
                 "for(i=0;i<=m;i++) M->row_head[i]=NULL;   /*各行链表为空*/\n\tfor(j=0;j<=n;j++) M->col_head[j]=NULL;   /*各列链表为空*/"),
                # and print the result so the example says something
                ("CreateCrossList(&M);", "CreateCrossList(&M);\n\tPrintCrossList(&M);"),
                # The new node's right/down are left uninitialised, so the very first walk over the
                # row list follows a garbage pointer. The book means them to be NULL.
                ("p->value=e;  /*生成结点*/",
                 "p->value=e;\n\t\tp->right=NULL; p->down=NULL;   /*示例补上：原段没有初始化这两个链域*/"),
            ],
        },
        "note": "示例把 #include \"crosslistarray.h\" 展开成同章的类型定义：原段把两个头指针数组分配后又赋成 NULL（一写就崩），示例改成逐个置空；书里的 main 保留，只多加了一行打印。",
        "stdin": "3,3,3\n1,1,5\n2,3,7\n3,2,9\n0,0,0\n",
    },
    {
        "id": "ch05-crosslistarray1",
        "members": ["ch05-crosslistarray1"],
        "parts": ["ch05-crosslistarray1"],
        "helpers": PRINT_CROSSLIST,
        # `#define NULL 0;` poisons every use of NULL with a stray semicolon; the standard NULL wins.
        "fixes": {"ch05-crosslistarray1": [("#define NULL 0;\n", "")]},
        "note": "原段写了 #define NULL 0;（宏里带分号，会让 if 之类的语句语法出错），示例删掉它、用标准库的 NULL；另外补一个 main。",
        "driver": r"""
int main(void)
{
    CrossList M;
    OLNode n1, n2;
    int i;

    n1.row = 1; n1.col = 1; n1.value = 5; n1.right = NULL; n1.down = NULL;
    n2.row = 2; n2.col = 3; n2.value = 7; n2.right = NULL; n2.down = NULL;

    M.m = 3; M.n = 3; M.len = 2;
    M.row_head = (OLink *)malloc((M.m + 1) * sizeof(OLink));
    M.col_head = (OLink *)malloc((M.n + 1) * sizeof(OLink));
    for (i = 0; i <= M.m; i++)
        M.row_head[i] = NULL;
    for (i = 0; i <= M.n; i++)
        M.col_head[i] = NULL;
    M.row_head[1] = &n1;
    M.row_head[2] = &n2;
    M.col_head[1] = &n1;
    M.col_head[3] = &n2;

    PrintCrossList(&M);
    return 0;
}
""",
    },
    {
        "id": "ch05-glist-h",
        "members": ["ch05-glist"],
        "parts": ["ch05-glist"],
        "extra": GLIST_GLUE,
        "helpers": GLIST_HELPERS,
        "fixes": {
            "ch05-glist": [
                # The union is named atom_htp, so `->atom` has to be `->atom_htp.atom`.
                # `->atom` is the union member; `->atom_htp` is the union itself.
                (r"->atom(?!_htp)", "->atom_htp.atom", "re"),
                # Length counts into `k`, which is never declared; CountAtom1/CountAtom2 each call a
                # CountAtom that this listing does not contain.
                ("k++;", "n++;"),
                ("return(k);", "return(n);"),
                ("int d,max;", "int d, max = 0;"),
                ("n=n+CountAtom(", "n=n+CountAtom1("),
                ("n1=CountAtom(", "n1=CountAtom2("),
                ("n2=CountAtom(", "n2=CountAtom2("),
            ],
        },
        "note": "示例把原段的几处笔误改正（Length 里的 k 未声明、CountAtom1/CountAtom2 调用了不存在的 CountAtom、Depth 的 max 未初始化），并补上 AtomType、OK/ERROR 和一个 main。",
        "driver": r"""
/* 示例建一个 ((a, b), c)：外层表两个元素，第一个是子表。 */
int main(void)
{
    GList inner = cell(atom('a'), cell(atom('b'), NULL));
    GList outer = cell(inner, cell(atom('c'), NULL));
    GList copy = NULL;

    printf("内层表的长度: %d\n", Length(inner));
    printf("外层表的深度: %d\n", Depth(outer));
    printf("原子个数: %d\n", CountAtom1(outer));
    printf("表头就是内层表: %d\n", Head(outer) == inner);
    printf("表尾非空: %d\n", Tail(outer) != NULL);

    CopyGList(outer, &copy);
    printf("复制出来的原子个数: %d\n", CountAtom1(copy));
    return 0;
}
""",
    },
    {
        "id": "ch05-glist-ops",
        "members": ["ch05-5.5", "ch05-5.6", "ch05-5.7", "ch05-5.8", "ch05-5.9-1"],
        "parts": ["ch05-5.5", "ch05-5.6", "ch05-5.7", "ch05-5.8", "ch05-5.9-1"],
        "extra": GLIST_GLUE + GLIST_TYPE,
        "helpers": GLIST_HELPERS,
        "fixes": {
            "ch05-5.7": [("k++;", "n++;"), ("return(k);", "return(n);")],
            "ch05-5.8": [("int d, max;", "int d, max = 0;")],
        },
        "note": "示例补齐广义表的类型定义，并改正 Length 里的 k 未声明、Depth 里 max 未初始化；再补一个 main。",
        "driver": r"""
int main(void)
{
    GList inner = cell(atom('a'), cell(atom('b'), NULL));
    GList outer = cell(inner, cell(atom('c'), NULL));

    printf("内层表的长度: %d\n", Length(inner));
    printf("外层表的深度: %d\n", Depth(outer));
    printf("原子个数: %d\n", CountAtom(outer));
    printf("表头就是内层表: %d\n", Head(outer) == inner);
    return 0;
}
""",
    },
    {
        "id": "ch05-glist-count2",
        "members": ["ch05-5.9-2"],
        "parts": ["ch05-5.9-2"],
        "extra": GLIST_GLUE + GLIST_TYPE,
        "helpers": GLIST_HELPERS,
        "note": "示例补齐广义表的类型定义和一个 main：这一段用「分表头、表尾各数一遍」的办法统计原子。",
        "driver": r"""
int main(void)
{
    GList inner = cell(atom('a'), cell(atom('b'), NULL));
    GList outer = cell(inner, cell(atom('c'), NULL));

    printf("内层表的原子个数: %d\n", CountAtom(inner));
    printf("外层表的原子个数: %d\n", CountAtom(outer));
    return 0;
}
""",
    },
    {
        "id": "ch05-glist-copy",
        "members": ["ch05-5.10"],
        "parts": ["ch05-5.10"],
        "extra": GLIST_GLUE + GLIST_TYPE,
        "fixes": {"ch05-5.10": [(r"->atom(?!_htp)", "->atom_htp.atom", "re")]},
        "helpers": GLIST_HELPERS + r"""
/* 数原子个数（示例自带）：原子就是 1，表结点则表头 + 表尾各数一遍。
   注意不能对原子结点取 htp，那是联合体里没有意义的另一半。 */
static int countAtoms(GList L)
{
    if (L == NULL)
        return 0;
    if (L->tag == ATOM)
        return 1;
    return countAtoms(L->atom_htp.htp.hp) + countAtoms(L->atom_htp.htp.tp);
}
""",
        "note": "示例补齐广义表的类型定义和一个 main，复制出来再数一遍原子个数。",
        "driver": r"""
int main(void)
{
    GList inner = cell(atom('a'), cell(atom('b'), NULL));
    GList outer = cell(inner, cell(atom('c'), NULL));
    GList copy = NULL;

    CopyGList(outer, &copy);
    printf("原表原子个数: %d\n", countAtoms(outer));
    printf("复制得到的另一份表: %d\n", copy != outer);
    printf("复制后的原子个数: %d\n", countAtoms(copy));
    return 0;
}
""",
    },
    {
        "id": "ch05-ma",
        "members": ["ch05-ma"],
        "parts": ["ch05-ma"],
        "note": "这一段自带 main（从键盘读一个 3×3 矩阵并找出马鞍点），示例只补了输入样例。",
        "stdin": "1 2 3\n4 5 6\n7 8 9\n",
    },

    # -------------------------------------------------------------------- ch06
    {
        "id": "ch06-bitree",
        "members": ["ch06-bitree"],
        "parts": ["ch06-bitree"],
        "fixes": {"ch06-bitree": [("#include <conio.h>\n", "")]},
        "note": "示例去掉了只存在于 Windows 的 conio.h，并补一个 main：按先序输入建树，再打印出来。",
        "stdin": "AB..C..\n",
        "driver": r"""
/* 打印函数是示例自己加的：这一段只给了建树，没给遍历。 */
void show(BiTree t)
{
    if (t == NULL)
        return;
    printf("%c ", t->data);
    show(t->LChild);
    show(t->RChild);
}

int main(void)
{
    BiTree t = NULL;

    CreateBiTree(&t);            /* 先序输入，"." 表示空 */
    printf("\n建好的树（先序）: ");
    show(t);
    printf("\n");
    return 0;
}
""",
    },
    {
        "id": "ch06-huffman",
        "members": ["ch06-huffman"],
        "parts": ["ch06-huffman"],
        "note": "这一段只是类型定义，示例把它用起来：摆几个权重看看哈夫曼树的数组长什么样。",
        "driver": r"""
int main(void)
{
    HuffmanTree ht;
    HuffmanCode hc;
    int weights[] = {5, 7, 2};
    int i;

    for (i = 1; i <= 3; i++)
    {
        ht[i].weight = weights[i - 1];
        ht[i].parent = 0;
        ht[i].LChild = 0;
        ht[i].RChild = 0;
        hc[i] = NULL;
    }

    printf("叶子结点权重: ");
    for (i = 1; i <= 3; i++)
        printf("%d ", ht[i].weight);
    printf("\n每个叶子先都是独立的根（parent=0）: %d\n", ht[1].parent == 0 && ht[3].parent == 0);
    return 0;
}
""",
    },
    {
        "id": "ch06-threadtree",
        "members": ["ch06-threadtree"],
        "parts": ["ch06-threadtree"],
        "fixes": {"ch06-threadtree": [("#include <conio.h>\n", "")]},
        "note": "示例去掉了 Windows 专有的 conio.h，并补一个 main 把线索标志位摆出来。",
        "driver": r"""
int main(void)
{
    BiTNode a, b;
    BiTree root = &a;

    a.data = 'A';
    a.Ltag = 0; a.Rtag = 1;         /* 右指针是线索，不是孩子 */
    a.LChild = &b;
    a.RChild = &a;                  /* 中序后继就是自己 */

    b.data = 'B';
    b.Ltag = 1; b.Rtag = 1;
    b.LChild = NULL;
    b.RChild = &a;

    printf("根结点: %c, Ltag=%d, Rtag=%d\n", a.data, a.Ltag, a.Rtag);
    printf("右指针是线索（指向中序后继）: %d\n", a.Rtag == 1);
    printf("左孩子: %c\n", root->LChild->data);
    return 0;
}
""",
    },
    {
        "id": "ch06-tree",
        "members": ["ch06-tree"],
        "parts": ["ch06-tree"],
        "note": "这一段只是类型定义，示例用它搭一棵「孩子—兄弟」表示法的树。",
        "driver": r"""
int main(void)
{
    CSNode root, first, second, secondSibling;

    root.data = 'A';
    first.data = 'B';
    second.data = 'C';
    secondSibling.data = 'D';

    root.FirstChild = &first;
    first.FirstChild = NULL;
    first.Nextsibling = &second;
    second.FirstChild = &secondSibling;   /* C 的孩子是 D */
    second.Nextsibling = NULL;
    secondSibling.FirstChild = NULL;
    secondSibling.Nextsibling = NULL;

    printf("根: %c, 第一个孩子: %c\n", root.data, root.FirstChild->data);
    printf("A 的孩子们: ");
    for (CSNode *p = root.FirstChild; p != NULL; p = p->Nextsibling)
        printf("%c ", p->data);
    printf("\nC 的孩子: %c\n", second.FirstChild->data);
    return 0;
}
""",
    },

    # -------------------------------------------------------------------- ch07 (types + the two create functions)
    {
        "id": "ch07-adjmatrix",
        "members": ["ch07-adjmatrix"],
        "parts": ["ch07-adjmatrix"],
        "extra": "#define AdjType int\ntypedef int OtherInfo;",
        "note": "示例补上了这一段用到的 AdjType、OtherInfo，并补一个 main 手工装一个 3 个顶点的有向网。",
        "driver": ADJMATRIX_PLAIN_DRIVER,
    },
    {
        "id": "ch07-7.1",
        "members": ["ch07-7.1"],
        "parts": ["ch07-adjmatrix", "ch07-7.1"],
        "extra": "#define AdjType int\ntypedef int OtherInfo;",
        "fixes": {
            "ch07-7.1": [
                ('#include "adjmatrix.h"\n', ""),
                # the book's main reads the graph from the keyboard one prompt at a time, which cannot
                # be scripted; the example builds the same graph directly instead.
                ("void main()", "/* 书里的这个 main 要从键盘逐条输入顶点和弧，示例换成下面手工建网的 main。 */\nvoid interactive_main()"),
            ],
        },
        "note": "示例把 #include \"adjmatrix.h\" 展开成同章的类型定义，并把需要交互输入的 main 换成一个手工建网的 main。",
        "driver": ADJMATRIX_DRIVER,
    },
    {
        "id": "ch07-adjmatrix1",
        "members": ["ch07-adjmatrix1"],
        "parts": ["ch07-adjmatrix1"],
        "note": "示例补一个 main：手工装好矩阵，再用 LocateVertex 找顶点。",
        "driver": r"""
int main(void)
{
    AdjMatrix G;
    int i, j;

    G.vexnum = 3; G.arcnum = 1; G.kind = DN;
    G.vexs[0] = 'A'; G.vexs[1] = 'B'; G.vexs[2] = 'C';
    for (i = 0; i < 3; i++)
        for (j = 0; j < 3; j++)
            G.arcs[i][j].adj = INFINITY;
    G.arcs[0][2].adj = 9;              /* A -> C，权 9 */

    printf("顶点 'C' 的下标: %d\n", LocateVertex(&G, 'C'));
    printf("顶点 'Z' 找不到时返回: %d\n", LocateVertex(&G, 'Z'));
    printf("A -> C 的权: %d\n", G.arcs[0][2].adj);
    return 0;
}
""",
    },
    {
        "id": "ch07-adjmatrix2",
        "members": ["ch07-adjmatrix2"],
        "parts": ["ch07-adjmatrix2"],
        "note": "示例补一个 main：手工装好矩阵，再用 LocateVertex 找顶点（建网函数 CreateDN 需要逐条交互输入，这里不调用）。",
        "driver": r"""
int main(void)
{
    AdjMatrix G;
    int i, j;

    G.vexnum = 3; G.arcnum = 2; G.kind = DN;
    G.vexs[0] = 'A'; G.vexs[1] = 'B'; G.vexs[2] = 'C';
    for (i = 0; i < 3; i++)
        for (j = 0; j < 3; j++)
            G.arcs[i][j].adj = INFINITY;
    G.arcs[0][1].adj = 5;
    G.arcs[1][2].adj = 7;

    printf("顶点 'B' 的下标: %d\n", LocateVertex(&G, 'B'));
    printf("A -> B 的权: %d\n", G.arcs[0][1].adj);
    printf("B -> C 的权: %d\n", G.arcs[1][2].adj);
    return 0;
}
""",
    },
    {
        "id": "ch07-OrthList",
        "members": ["ch07-OrthList"],
        "parts": ["ch07-OrthList"],
        "fixes": {"ch07-OrthList": [("typedef char VertexNode;", "/* 与下面的结构体同名的 typedef，示例删掉 */")]},
        "note": "原段先 typedef char VertexNode，又用 VertexNode 当结构体名，两者冲突，示例删掉前一行；再补一个 main。",
        "driver": r"""
int main(void)
{
    OrthList g;
    ArcNode a1, a2;

    g.vexnum = 3;
    g.arcnum = 2;
    g.kind = DG;
    g.vertex[0].data = 'A'; g.vertex[0].firstin = NULL; g.vertex[0].firstout = &a1;
    g.vertex[1].data = 'B'; g.vertex[1].firstin = &a1;  g.vertex[1].firstout = &a2;
    g.vertex[2].data = 'C'; g.vertex[2].firstin = &a2;  g.vertex[2].firstout = NULL;

    a1.tailvex = 0; a1.headvex = 1; a1.tlink = NULL; a1.hlink = NULL;
    a2.tailvex = 1; a2.headvex = 2; a2.tlink = NULL; a2.hlink = NULL;

    printf("顶点数 %d, 弧数 %d\n", g.vexnum, g.arcnum);
    printf("A 的第一条出弧: A -> %c\n", g.vertex[g.vertex[0].firstout->headvex].data);
    printf("C 的第一条入弧: %c -> C\n", g.vertex[g.vertex[2].firstin->tailvex].data);
    return 0;
}
""",
    },
    {
        "id": "ch07-7.2",
        "members": ["ch07-7.2"],
        # LocateVertex has to sit between the type and the function that calls it, so this program is
        # assembled by hand instead of by the generic "glue, parts, driver" order.
        "note": "示例把 #include \"OrthList.h\" 展开成同章的类型定义（并删掉与结构体同名的 typedef），补出原段调用的 LocateVertex，再把交互式 main 换成手工建表的 main。",
        "code": ORTHLIST_PROGRAM,
    },
    {
        "id": "ch07-OrthList1",
        "members": ["ch07-OrthList1"],
        "parts": ["ch07-OrthList1"],
        "note": "示例补一个 main：把这一段的结构体用起来，并调用它的 LocateVertex。",
        "driver": r"""
int main(void)
{
    OrthList g;
    ArcNode a1;

    g.vexnum = 2;
    g.arcnum = 1;
    g.kind = DG;
    g.vertex[0].data = 'A'; g.vertex[0].firstin = NULL; g.vertex[0].firstout = &a1;
    g.vertex[1].data = 'B'; g.vertex[1].firstin = &a1;  g.vertex[1].firstout = NULL;
    a1.tailvex = 0; a1.headvex = 1; a1.tlink = NULL; a1.hlink = NULL;

    printf("顶点数 %d, 弧数 %d\n", g.vexnum, g.arcnum);
    printf("顶点 'B' 的下标: %d\n", LocateVertex(&g, 'B'));
    printf("A 的出弧数（看 firstout 非空）: %d\n", g.vertex[0].firstout != NULL);
    return 0;
}
""",
    },
    {
        "id": "ch07-AdjList",
        "members": ["ch07-AdjList"],
        "parts": ["ch07-AdjList"],
        "extra": "typedef char VertexData;\ntypedef int OtherInfo;",
        "fixes": {"ch07-AdjList": [("typedef char VertexNode;", "/* 与下面的结构体同名的 typedef，示例删掉 */")]},
        "note": "原段先 typedef char VertexNode、又把 VertexNode 当结构体名，示例删掉前一行；另外补上 VertexData、OtherInfo 和一个 main。",
        "driver": r"""
int main(void)
{
    AdjList g;
    ArcNode a1, a2;
    ArcNode *p;

    g.vexnum = 3;
    g.arcnum = 2;
    g.kind = DG;
    g.vertex[0].data = 'A'; g.vertex[0].firstarc = &a1;
    g.vertex[1].data = 'B'; g.vertex[1].firstarc = &a2;
    g.vertex[2].data = 'C'; g.vertex[2].firstarc = NULL;
    a1.adjvex = 1; a1.nextarc = NULL;
    a2.adjvex = 2; a2.nextarc = NULL;

    printf("顶点数 %d, 弧数 %d\n", g.vexnum, g.arcnum);
    for (int i = 0; i < g.vexnum; i++)
    {
        printf("%c 的邻接点: ", g.vertex[i].data);
        for (p = g.vertex[i].firstarc; p != NULL; p = p->nextarc)
            printf("%c ", g.vertex[p->adjvex].data);
        printf("\n");
    }
    return 0;
}
""",
    },
    {
        "id": "ch07-AdjMultiList",
        "members": ["ch07-AdjMultiList"],
        "parts": ["ch07-AdjMultiList"],
        "extra": "typedef char VertexData;",
        "fixes": {"ch07-AdjMultiList": [("typedef char VertexNode;", "typedef char VertexData;")]},
        "note": "原段把顶点数据 typedef 成了 VertexNode（和后面的结构体重名），示例改成 VertexData；再补一个 main。",
        "driver": r"""
int main(void)
{
    AdjMultiList g;
    EdgeNode e1;

    g.vexnum = 2;
    g.arcnum = 1;
    g.kind = UDG;
    g.vertex[0].data = 'A'; g.vertex[0].firstedge = &e1;
    g.vertex[1].data = 'B'; g.vertex[1].firstedge = &e1;

    e1.mark = 0; e1.ivex = 0; e1.jvex = 1; e1.ilink = NULL; e1.jlink = NULL;

    printf("顶点数 %d, 边数 %d\n", g.vexnum, g.arcnum);
    printf("这条边连着 %c 和 %c\n", g.vertex[e1.ivex].data, g.vertex[e1.jvex].data);
    printf("顶点 A 的第一条边指向 %c\n", g.vertex[g.vertex[0].firstedge->jvex].data);
    return 0;
}
""",
    },

    # -------------------------------------------------------------------- ch08 / ch09 (type-only listings)
    {
        "id": "ch08-bst",
        "members": ["ch08-bst"],
        "parts": ["ch08-bst"],
        "note": "这一段只是类型定义，示例搭一棵三结点的二叉排序树，再中序打印（打印是示例自己加的）。",
        "driver": r"""
void show(BSTree t)
{
    if (t == NULL)
        return;
    show(t->lchild);
    printf("%d ", t->key);
    show(t->rchild);
}

int main(void)
{
    BSTNode n1, n2, n3;

    n1.key = 20; n1.lchild = &n2; n1.rchild = &n3;
    n2.key = 10; n2.lchild = NULL; n2.rchild = NULL;
    n3.key = 30; n3.lchild = NULL; n3.rchild = NULL;

    printf("中序遍历（就是排好序的顺序）: ");
    show(&n1);
    printf("\n根的关键字: %d\n", n1.key);
    return 0;
}
""",
    },
    {
        "id": "ch08-avltree",
        "members": ["ch08-avltree"],
        "parts": ["ch08-avltree"],
        "note": "这一段只是类型定义，示例把平衡因子 bf 摆出来看。",
        "driver": r"""
int main(void)
{
    AVLTNode root, left, right;

    root.key = 20; root.bf = 0; root.lchild = &left; root.rchild = &right;
    left.key = 10; left.bf = 0; left.lchild = NULL; left.rchild = NULL;
    right.key = 30; right.bf = 0; right.lchild = NULL; right.rchild = NULL;

    printf("根:%d(bf=%d) 左:%d 右:%d\n", root.key, root.bf, root.lchild->key, root.rchild->key);
    printf("这棵树是平衡的（bf 都是 0）: %d\n", root.bf == 0 && left.bf == 0 && right.bf == 0);
    return 0;
}
""",
    },
    {
        "id": "ch08-hash",
        "members": ["ch08-hash"],
        "parts": ["ch08-hash"],
        "note": "这一段只是类型定义，示例按除留余数法把几个关键字放进散列表，看落到了哪些格子里。",
        "driver": r"""
int main(void)
{
    HashTable ht;
    int keys[] = {18, 31, 5, 44};
    int i;

    for (i = 0; i < m; i++)
        ht[i].key = NULLKEY;
    for (i = 0; i < 4; i++)
        ht[keys[i] % m].key = keys[i];

    printf("表长 %d，NULLKEY 表示空\n", m);
    for (i = 0; i < m; i++)
        printf("  [%2d] %s\n", i, ht[i].key == NULLKEY ? "空" : "有值");
    printf("18 %% %d = %d，44 %% %d = %d：两个关键字落在同一个格子里，后写的把先写的挤掉了\n",
           m, 18 % m, m, 44 % m);
    return 0;
}
""",
    },
    {
        "id": "ch08-mbtree",
        "members": ["ch08-mbtree"],
        "parts": ["ch08-mbtree"],
        "note": "这一段只是类型定义，示例摆一个 3 阶 B 树的根结点看字段含义。",
        "driver": r"""
int main(void)
{
    Mbtnode root;
    int i;

    root.parent = NULL;
    root.keynum = 2;
    for (i = 0; i <= m; i++)
    {
        root.key[i] = 0;
        root.ptr[i] = NULL;
    }
    root.key[1] = 10;
    root.key[2] = 20;

    printf("%d 阶 B 树，根里有 %d 个关键字\n", m, root.keynum);
    printf("关键字: ");
    for (i = 1; i <= root.keynum; i++)
        printf("%d ", root.key[i]);
    printf("\n还是叶子（ptr 全空）: %d\n", root.ptr[0] == NULL && root.ptr[1] == NULL);
    return 0;
}
""",
    },
    {
        "id": "ch08-seq",
        "members": ["ch08-seq"],
        "parts": ["ch08-seq"],
        "note": "这一段只是类型定义，示例装 5 条记录并按关键字顺序查找（查找循环是示例自己加的）。",
        "driver": r"""
int main(void)
{
    RecordList L;
    char keys[] = "ceadg";
    char wanted = 'a';
    int i;
    int found = 0;

    L.length = 5;
    for (i = 1; i <= L.length; i++)
    {
        L.r[i].key = keys[i - 1];
        L.r[i].other_data = i;
    }

    printf("表里的关键字: ");
    for (i = 1; i <= L.length; i++)
        printf("%c ", L.r[i].key);
    printf("\n");

    for (i = 1; i <= L.length; i++)
        if (L.r[i].key == wanted)
        {
            found = i;
            break;
        }
    printf("查找 '%c' 的比较次数: %d\n", wanted, found);
    return 0;
}
""",
    },
    {
        "id": "ch09-insort",
        "members": ["ch09-insort"],
        "parts": ["ch09-insort"],
        "note": "这一段只是类型定义，示例装一组记录并按关键字打印（排序算法本章未收录，示例只演示这个记录类型怎么用）。",
        "driver": r"""
int main(void)
{
    RecordType r[6];
    int keys[] = {49, 38, 65, 97, 76};
    int i;

    for (i = 1; i <= 5; i++)
    {
        r[i].key = keys[i - 1];
        r[i].other_data = i;
    }

    printf("待排记录的关键字: ");
    for (i = 1; i <= 5; i++)
        printf("%d ", r[i].key);
    printf("\n记录结构里还有一个 other_data 跟着走: %d\n", r[1].other_data);
    return 0;
}
""",
    },
    {
        "id": "ch09-radixsort",
        "members": ["ch09-radixsort"],
        "parts": ["ch09-radixsort"],
        "note": "这一段只是类型定义，示例装一条记录看子关键字数组和静态链域。",
        "driver": r"""
int main(void)
{
    SLinkList L;
    int i;

    L.length = 2;
    L.keynum = 3;
    for (i = 0; i <= LIST_SIZE; i++)
    {
        L.r[i].next = 0;
        L.r[i].other_data = 0;
    }

    L.r[1].other_data = 1;
    L.r[1].key[1] = 4; L.r[1].key[2] = 9; L.r[1].key[3] = 7;
    L.r[1].next = 2;               /* 1 号记录后面跟着 2 号 */

    L.r[2].other_data = 2;
    L.r[2].key[1] = 2; L.r[2].key[2] = 0; L.r[2].key[3] = 5;
    L.r[2].next = 0;

    printf("记录数 %d, 子关键字个数 %d, 基数 %d\n", L.length, L.keynum, RADIX);
    printf("1 号记录的子关键字: %d %d %d\n", L.r[1].key[1], L.r[1].key[2], L.r[1].key[3]);
    printf("静态链: 1 号 -> %d 号\n", L.r[1].next);
    return 0;
}
""",
    },
    # -------------------------------------------------------------------- ch07 graph algorithms
    # The listings here are written as pseudocode: they call FirstAdjVertex/visit/InitStack and so on
    # without ever defining them, and some of them mix up field names. A runnable version of the same
    # algorithms is what the example ships, and the note says so.
    {
        "id": "ch07-graph-traverse",
        "members": ["ch07-7.3", "ch07-7.4", "ch07-7.5", "ch07-7.6", "ch07-7.7", "ch07-7.8", "ch07-7.9"],
        "note": "原段是伪代码（FirstAdjVertex、visit、visited 都没有给出），示例把它们补出来，并给出同一张图的递归深度优先、非递归深度优先、广度优先和一条简单路径。",
        "code": GRAPH_TRAVERSE_PROGRAM,
    },
    {
        "id": "ch07-prim",
        "members": ["ch07-7.10"],
        "note": "原段是伪代码（Minium、closedge 的用法都不完整），示例给出同一算法在邻接矩阵上的可运行版本：从 A 出发逐条挑最小边。",
        "code": PRIM_PROGRAM,
    },
    {
        "id": "ch07-topo-critical",
        "members": ["ch07-7.11", "ch07-7.12", "ch07-7.13", "ch07-7.14"],
        "note": "原段是伪代码（FindID、Stack、Minium 等都要自己补），示例给出同一张 AOE 网上的拓扑排序、各顶点的最早/最迟发生时间和关键活动。",
        "code": TOPO_CRITICAL_PROGRAM,
    },
    {
        "id": "ch07-shortest",
        "members": ["ch07-7.15", "ch07-7.16"],
        "note": "原段是伪代码（InitList/AddTail/Member 都是抽象操作），示例给出同一算法在邻接矩阵上的可运行版本：Dijkstra 单源最短路径和 Floyd 每对顶点最短路径。",
        "code": SHORTEST_PROGRAM,
    },
]

BLOCKED = {
    "ch03-3.15": "这一段是汉诺塔的执行过程记录（缩进的调用轨迹），不是可编译的代码。",
    "ch03-3.25": "这一段用了 Windows 专有的 conio.h / kbhit() / getch()，在运行环境下编不过。",
    "ch03-car": "这一段是完整的停车场程序（343 行），用了不跨平台的 flushall()，且需要一长串交互输入。",
    "ch03-linkqueue": "这一段末尾的 GetHead 引用了另一章的类型，原样编不过。",
}
