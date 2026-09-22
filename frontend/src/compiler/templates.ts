/**
 * The five starter programs the legacy single-page editor shipped with (基础输入 / 栈 / 链表 /
 * 队列 / 二叉树). Restored verbatim so returning learners see the same examples they remember.
 */
import type { MessageKey } from "../shared/i18n/messages";

export interface CompilerTemplate {
  id: string;
  labelKey: MessageKey;
  file: string;
  stdin: string;
  code: string;
}

export const compilerTemplates: CompilerTemplate[] = [
  {
    id: "hello",
    labelKey: "compiler.template.hello",
    file: "basic_io.c",
    stdin: "5\n",
    code: `#include <stdio.h>

int main(void) {
  int n;
  if (scanf("%d", &n) != 1) {
    printf("请输入一个整数\\n");
    return 0;
  }
  printf("n = %d\\n", n);
  printf("n * n = %d\\n", n * n);
  return 0;
}
`
  },
  {
    id: "stack",
    labelKey: "compiler.template.stack",
    file: "stack_push_pop.c",
    stdin: "",
    code: `#include <stdio.h>

#define MAX_SIZE 10

typedef struct {
  int data[MAX_SIZE];
  int top;
} Stack;

void init(Stack *s) {
  s->top = -1;
}

int push(Stack *s, int value) {
  if (s->top == MAX_SIZE - 1) return 0;
  s->data[++s->top] = value;
  return 1;
}

int pop(Stack *s, int *value) {
  if (s->top == -1) return 0;
  *value = s->data[s->top--];
  return 1;
}

void printStack(Stack *s) {
  printf("stack bottom -> top: ");
  for (int i = 0; i <= s->top; i++) {
    printf("%d ", s->data[i]);
  }
  printf("\\n");
}

int main(void) {
  Stack s;
  int value;
  init(&s);

  push(&s, 1);
  push(&s, 2);
  push(&s, 3);
  printStack(&s);

  if (pop(&s, &value)) {
    printf("pop: %d\\n", value);
  }
  printStack(&s);
  return 0;
}
`
  },
  {
    id: "list",
    labelKey: "compiler.template.list",
    file: "linked_list_head_insert.c",
    stdin: "",
    code: `#include <stdio.h>
#include <stdlib.h>

typedef struct Node {
  int data;
  struct Node *next;
} Node;

Node *headInsert(Node *head, int value) {
  Node *node = (Node *)malloc(sizeof(Node));
  node->data = value;
  node->next = head;
  return node;
}

void printList(Node *head) {
  for (Node *p = head; p != NULL; p = p->next) {
    printf("%d -> ", p->data);
  }
  printf("NULL\\n");
}

void freeList(Node *head) {
  while (head != NULL) {
    Node *next = head->next;
    free(head);
    head = next;
  }
}

int main(void) {
  Node *head = NULL;
  head = headInsert(head, 3);
  head = headInsert(head, 2);
  head = headInsert(head, 1);

  printf("头插法结果：\\n");
  printList(head);
  freeList(head);
  return 0;
}
`
  },
  {
    id: "queue",
    labelKey: "compiler.template.queue",
    file: "circular_queue.c",
    stdin: "",
    code: `#include <stdio.h>

#define MAX_SIZE 6

typedef struct {
  int data[MAX_SIZE];
  int front;
  int rear;
} Queue;

void init(Queue *q) {
  q->front = 0;
  q->rear = 0;
}

int isEmpty(Queue *q) {
  return q->front == q->rear;
}

int isFull(Queue *q) {
  return (q->rear + 1) % MAX_SIZE == q->front;
}

int enqueue(Queue *q, int value) {
  if (isFull(q)) return 0;
  q->data[q->rear] = value;
  q->rear = (q->rear + 1) % MAX_SIZE;
  return 1;
}

int dequeue(Queue *q, int *value) {
  if (isEmpty(q)) return 0;
  *value = q->data[q->front];
  q->front = (q->front + 1) % MAX_SIZE;
  return 1;
}

void printQueue(Queue *q) {
  printf("queue front -> rear: ");
  for (int i = q->front; i != q->rear; i = (i + 1) % MAX_SIZE) {
    printf("%d ", q->data[i]);
  }
  printf("\\nfront=%d rear=%d\\n", q->front, q->rear);
}

int main(void) {
  Queue q;
  int value;
  init(&q);

  enqueue(&q, 10);
  enqueue(&q, 20);
  enqueue(&q, 30);
  printQueue(&q);

  dequeue(&q, &value);
  printf("dequeue: %d\\n", value);
  printQueue(&q);
  return 0;
}
`
  },
  {
    id: "tree",
    labelKey: "compiler.template.tree",
    file: "binary_tree_traversal.c",
    stdin: "",
    code: `#include <stdio.h>
#include <stdlib.h>

typedef struct Node {
  char data;
  struct Node *left;
  struct Node *right;
} Node;

Node *newNode(char data) {
  Node *node = (Node *)malloc(sizeof(Node));
  node->data = data;
  node->left = NULL;
  node->right = NULL;
  return node;
}

void preorder(Node *root) {
  if (root == NULL) return;
  printf("%c ", root->data);
  preorder(root->left);
  preorder(root->right);
}

void inorder(Node *root) {
  if (root == NULL) return;
  inorder(root->left);
  printf("%c ", root->data);
  inorder(root->right);
}

void postorder(Node *root) {
  if (root == NULL) return;
  postorder(root->left);
  postorder(root->right);
  printf("%c ", root->data);
}

void freeTree(Node *root) {
  if (root == NULL) return;
  freeTree(root->left);
  freeTree(root->right);
  free(root);
}

int main(void) {
  Node *root = newNode('A');
  root->left = newNode('B');
  root->right = newNode('C');
  root->left->left = newNode('D');
  root->left->right = newNode('E');

  printf("preorder: ");
  preorder(root);
  printf("\\n");

  printf("inorder: ");
  inorder(root);
  printf("\\n");

  printf("postorder: ");
  postorder(root);
  printf("\\n");

  freeTree(root);
  return 0;
}
`
  }
];
