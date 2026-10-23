<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AdminPageFrame from "../../admin/components/AdminPageFrame.vue";
import { formatDate } from "../../admin/api";
import { teacherApi, type TeacherClass, type TeacherStudent } from "../api";
import type { LearningMap, LearningMapNode } from "../../shared/types/contracts";

/**
 * A teacher's own classes: who is in them, and how each student is doing.
 *
 * The list stays cheap on purpose - one row per student with last activity and classroom work - and the mastery
 * map is fetched only for the student the teacher actually opens. Reading somebody else's profile is the most
 * sensitive read in the product, so the server decides who is allowed; this page only asks.
 */
const classes = ref<TeacherClass[]>([]);
const classId = ref("");
const students = ref<TeacherStudent[]>([]);
const picked = ref<TeacherStudent | null>(null);
const map = ref<LearningMap | null>(null);

const loadingClasses = ref(true);
const loadingStudents = ref(false);
const loadingMap = ref(false);
const error = ref("");

const root = computed<LearningMapNode | null>(() => map.value?.nodes.find((node) => node.level === 0) ?? null);
const chapters = computed<LearningMapNode[]>(() =>
  (map.value?.nodes ?? []).filter((node) => node.level === 1).sort((left, right) => left.id.localeCompare(right.id))
);

function percent(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : `${Math.round(value * 100)}%`;
}

async function loadClasses(): Promise<void> {
  loadingClasses.value = true;
  try {
    classes.value = await teacherApi.classes();
    if (classes.value.length > 0) {
      classId.value = classes.value[0].id;
      await loadStudents();
    }
  } catch {
    error.value = "读取班级失败，请稍后重试。";
  } finally {
    loadingClasses.value = false;
  }
}

async function loadStudents(): Promise<void> {
  if (classId.value === "") {
    students.value = [];
    return;
  }
  loadingStudents.value = true;
  picked.value = null;
  map.value = null;
  try {
    students.value = await teacherApi.students(classId.value);
  } catch {
    students.value = [];
    error.value = "读取学生名单失败。";
  } finally {
    loadingStudents.value = false;
  }
}

async function open(student: TeacherStudent): Promise<void> {
  picked.value = student;
  map.value = null;
  loadingMap.value = true;
  try {
    map.value = await teacherApi.studentMap(student.id);
  } catch {
    error.value = "读取该学生的学习图谱失败（可能不在你带的班里）。";
  } finally {
    loadingMap.value = false;
  }
}

onMounted(loadClasses);
</script>

<template>
  <AdminPageFrame
    title="我的班级"
    description="本班学生的学习情况。点开某个学生可以看到他的学习图谱——和该学生自己看到的是同一份计算。"
  >
    <section class="tp">
      <div class="tp__bar">
        <label class="admin-field">
          <span>班级</span>
          <select v-model="classId" :disabled="loadingClasses || classes.length === 0" @change="loadStudents">
            <option v-for="item in classes" :key="item.id" :value="item.id">
              {{ item.name }}<template v-if="item.term"> · {{ item.term }}</template>（{{ item.students }} 人）
            </option>
          </select>
        </label>
        <p v-if="!loadingClasses && classes.length === 0" class="admin-muted">
          你名下还没有班级。班级与名单由管理员维护，建好后这里会自动出现。
        </p>
      </div>

      <p v-if="error" class="admin-danger">{{ error }}</p>

      <div class="admin-data-surface">
        <table class="tp__table">
          <thead>
            <tr>
              <th>学生</th>
              <th>账号</th>
              <th>状态</th>
              <th>最近活跃</th>
              <th>课堂步数</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="student in students" :key="student.id" :class="{ 'tp__row--active': picked?.id === student.id }">
              <td>{{ student.username ?? "未命名" }}</td>
              <td class="admin-muted">{{ student.email }}</td>
              <td>{{ student.status }}</td>
              <td class="admin-nowrap">{{ formatDate(student.lastActivityAt) }}</td>
              <td>{{ student.classroomSteps }}</td>
              <td>
                <button type="button" class="tp__open" @click="open(student)">学习图谱</button>
              </td>
            </tr>
            <tr v-if="!loadingStudents && students.length === 0">
              <td colspan="6" class="admin-muted">这个班还没有学生。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="picked" class="admin-panel tp">
      <header class="admin-section-head">
        <h2>{{ picked.username ?? picked.email }} 的学习图谱</h2>
        <p class="admin-muted">
          {{ root ? `整体掌握 ${percent(root.mastery)} · 可信 ${percent(root.confidence)}` : "计算中…" }}
        </p>
      </header>

      <p v-if="loadingMap" class="admin-muted">正在计算…</p>
      <p v-else-if="!map" class="admin-muted">没有读到数据。</p>

      <ul v-else class="tp__chapters">
        <li v-for="chapter in chapters" :key="chapter.id">
          <div class="tp__chapter-head">
            <span>{{ chapter.title }}</span>
            <span class="admin-muted">{{ percent(chapter.mastery) }} · 可信 {{ percent(chapter.confidence) }}</span>
          </div>
          <div class="tp__bar-track">
            <div class="tp__bar-fill" :style="{ width: `${Math.round((chapter.mastery ?? 0) * 100)}%` }" />
          </div>
        </li>
      </ul>
    </section>
  </AdminPageFrame>
</template>

<style scoped>
.tp + .tp {
  margin-top: 18px;
}

.tp__bar {
  display: flex;
  align-items: flex-end;
  gap: 16px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.tp__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.tp__table th,
.tp__table td {
  padding: 9px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(12, 44, 52, 0.1);
}

.tp__table th {
  font-weight: 600;
  color: var(--admin-muted);
  white-space: nowrap;
}

.tp__row--active {
  background: rgba(8, 118, 111, 0.06);
}

.tp__open {
  border: 1px solid rgba(8, 118, 111, 0.4);
  background: transparent;
  color: inherit;
  border-radius: 8px;
  padding: 5px 10px;
  cursor: pointer;
}

.tp__open:hover {
  background: rgba(8, 118, 111, 0.1);
}

.tp__chapters {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.tp__chapter-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 5px;
  font-size: 14px;
}

.tp__bar-track {
  height: 8px;
  border-radius: 999px;
  background: rgba(12, 44, 52, 0.1);
  overflow: hidden;
}

.tp__bar-fill {
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(8, 118, 111, 0.55), rgba(8, 118, 111, 0.9));
}
</style>
