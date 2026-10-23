<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AdminPageFrame from "../../admin/components/AdminPageFrame.vue";
import { formatDateTime } from "../../shared/api";
import LoadingState from "../../shared/components/LoadingState.vue";
import ErrorState from "../../shared/components/ErrorState.vue";
import RetryButton from "../../shared/components/RetryButton.vue";
import LearningMapChapters from "../../shared/components/LearningMapChapters.vue";
import { teacherApi, teacherErrorMessage, type TeacherClass, type TeacherStudent, type TeacherStudentFootprint } from "../api";

/**
 * A teacher's own classes: who is in them, and how each student is doing.
 *
 * The roster stays cheap on purpose - one row per student with last activity and classroom work. Opening a
 * student makes exactly one audited call for their whole footprint: courses with progress, classroom work,
 * bookmarks and the mastery map. What a lesson costs never shows here; that is the operations console's job.
 */
const classes = ref<TeacherClass[]>([]);
const classId = ref("");
const students = ref<TeacherStudent[]>([]);
const picked = ref<TeacherStudent | null>(null);
const footprint = ref<TeacherStudentFootprint | null>(null);

const loadingClasses = ref(true);
const loadingStudents = ref(false);
const loadingFootprint = ref(false);
const error = ref("");
const footprintError = ref("");

let footprintRequest = 0;

const root = computed(() => footprint.value?.learningMap.nodes.find((node) => node.level === 0) ?? null);

function percent(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : `${Math.round(value * 100)}%`;
}

async function loadClasses(): Promise<void> {
  loadingClasses.value = true;
  error.value = "";
  try {
    classes.value = await teacherApi.classes();
    if (classes.value.length > 0) {
      classId.value = classes.value[0].id;
      await loadStudents();
    }
  } catch (failure) {
    error.value = teacherErrorMessage(failure, "读取班级");
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
  footprint.value = null;
  try {
    students.value = await teacherApi.students(classId.value);
  } catch (failure) {
    students.value = [];
    error.value = teacherErrorMessage(failure, "读取学生名单");
  } finally {
    loadingStudents.value = false;
  }
}

async function open(student: TeacherStudent): Promise<void> {
  const request = ++footprintRequest;
  picked.value = student;
  footprint.value = null;
  footprintError.value = "";
  loadingFootprint.value = true;
  try {
    const result = await teacherApi.studentFootprint(student.id);
    if (request !== footprintRequest) return;
    footprint.value = result;
  } catch (failure) {
    if (request === footprintRequest) footprintError.value = teacherErrorMessage(failure, "读取该学生的学习情况");
  } finally {
    if (request === footprintRequest) loadingFootprint.value = false;
  }
}

onMounted(loadClasses);
</script>

<template>
  <AdminPageFrame
    title="我的班级"
    description="本班学生的学习情况。点开某个学生可以看到他的课程进度和学习图谱——和该学生自己看到的是同一份计算。"
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

      <ErrorState v-if="error" title="读取失败" :message="error"><RetryButton @retry="loadClasses" /></ErrorState>

      <LoadingState v-if="loadingStudents" label="正在读取名单…" />
      <div v-else class="admin-data-surface">
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
              <td class="admin-nowrap">{{ formatDateTime(student.lastActivityAt) }}</td>
              <td>{{ student.classroomSteps }}</td>
              <td>
                <button type="button" class="tp__open" @click="open(student)">学习情况</button>
              </td>
            </tr>
            <tr v-if="students.length === 0">
              <td colspan="6" class="admin-muted">这个班还没有学生。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="picked" class="admin-panel tp" aria-label="学生学习情况">
      <header class="admin-section-head">
        <h2>{{ picked.username ?? picked.email }} 的学习情况</h2>
        <p v-if="root" class="admin-muted">
          整体掌握 {{ percent(root.mastery) }} · 可信 {{ percent(root.confidence) }}
        </p>
      </header>

      <LoadingState v-if="loadingFootprint" label="正在计算…" />
      <ErrorState v-else-if="footprintError" title="学习情况读取失败" :message="footprintError">
        <RetryButton @retry="open(picked)" />
      </ErrorState>

      <template v-else-if="footprint">
        <div class="signal-strip tp__strip">
          <div class="signal-strip__item"><span class="signal-strip__label">课堂步数</span><strong>{{ footprint.classroomEvents }}</strong></div>
          <div class="signal-strip__item"><span class="signal-strip__label">课堂会话</span><strong>{{ footprint.classroomSessions }}</strong></div>
          <div class="signal-strip__item"><span class="signal-strip__label">书签</span><strong>{{ footprint.bookmarks }}</strong></div>
          <div class="signal-strip__item"><span class="signal-strip__label">最近活跃</span><strong>{{ footprint.lastActivityAt ? formatDateTime(footprint.lastActivityAt) : "从未" }}</strong></div>
        </div>

        <template v-if="footprint.courses.length">
          <h3 class="tp__sub">课程进度</h3>
          <ul class="tp__courses">
            <li v-for="course in footprint.courses" :key="course.id">
              <span class="tp__course-title" :title="course.title">{{ course.title }}</span>
              <span class="admin-muted admin-nowrap">
                {{ course.completedPoints }}/{{ course.totalPoints }} 知识点<template v-if="course.lastPointTitle"> · 学到「{{ course.lastPointTitle }}」</template>
              </span>
            </li>
          </ul>
        </template>

        <h3 class="tp__sub">学习图谱</h3>
        <LearningMapChapters :nodes="footprint.learningMap.nodes" expandable />
      </template>
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

.tp__strip {
  margin-bottom: 12px;
}

.tp__sub {
  margin: 14px 0 8px;
  font-size: 15px;
}

.tp__courses {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 6px;
  font-size: 14px;
}

.tp__courses li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}

.tp__course-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
