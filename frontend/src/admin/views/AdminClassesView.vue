<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import AdminPageFrame from "../components/AdminPageFrame.vue";
import { adminApi, adminErrorMessage, formatDate } from "../api";
import type { AdminClass, AdminClassMember, ClassRole } from "../../shared/types";

/**
 * Classes and who is in them.
 *
 * A teacher can only see the learners of a class they are enrolled in, so this page is where that permission is
 * actually granted. Adding somebody as a teacher is the whole job in one step: the server also switches on their
 * platform TEACHER role and says whether it did. Removing a member withdraws the class but keeps the role, because
 * the same account may teach other classes.
 */
const classes = ref<AdminClass[]>([]);
const members = ref<AdminClassMember[]>([]);
const selected = ref("");

const loading = ref(true);
const loadingMembers = ref(false);
const busy = ref(false);
const error = ref("");
const notice = ref("");

const newName = ref("");
const newTerm = ref("");
const memberIdentifier = ref("");
const memberRole = ref<ClassRole>("STUDENT");

const current = computed(() => classes.value.find((entry) => entry.id === selected.value) ?? null);

async function loadClasses(): Promise<void> {
  loading.value = true;
  try {
    classes.value = await adminApi.classes();
    if (selected.value && !classes.value.some((entry) => entry.id === selected.value)) selected.value = "";
  } catch (failure) {
    error.value = adminErrorMessage(failure, "读取班级列表");
  } finally {
    loading.value = false;
  }
}

async function loadMembers(): Promise<void> {
  if (!selected.value) {
    members.value = [];
    return;
  }
  loadingMembers.value = true;
  try {
    members.value = await adminApi.classMembers(selected.value);
  } catch (failure) {
    members.value = [];
    error.value = adminErrorMessage(failure, "读取班级名单");
  } finally {
    loadingMembers.value = false;
  }
}

async function pick(classId: string): Promise<void> {
  selected.value = classId;
  await loadMembers();
}

async function create(): Promise<void> {
  const name = newName.value.trim();
  if (name === "") {
    error.value = "请先填写班级名称。";
    return;
  }
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const created = await adminApi.createClass({ name, term: newTerm.value.trim() || undefined });
    newName.value = "";
    newTerm.value = "";
    notice.value = `已创建「${created.name}」。记得把任课老师加进去，他才能看到这个班。`;
    await loadClasses();
    await pick(created.id);
  } catch (failure) {
    error.value = adminErrorMessage(failure, "创建班级");
  } finally {
    busy.value = false;
  }
}

async function addMember(): Promise<void> {
  const identifier = memberIdentifier.value.trim();
  if (identifier === "" || !selected.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    const result = await adminApi.addClassMember(selected.value, { identifier, roleInClass: memberRole.value });
    memberIdentifier.value = "";
    if (memberRole.value === "TEACHER") {
      notice.value = result.teacherRoleGranted
        ? "已加入，并已为他开通教师身份——现在就能在教师端看到本班学生。"
        : "已加入。他本来就有教师身份，现在可以看到本班学生。";
    } else {
      notice.value = "已加入本班。";
    }
    await Promise.all([loadMembers(), loadClasses()]);
  } catch (failure) {
    error.value = adminErrorMessage(failure, "添加成员");
  } finally {
    busy.value = false;
  }
}

async function removeMember(member: AdminClassMember): Promise<void> {
  if (!selected.value) return;
  const who = member.username || member.email;
  if (!window.confirm(`把 ${who} 移出本班？`)) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await adminApi.removeClassMember(selected.value, member.userId);
    notice.value = `已把 ${who} 移出本班。`;
    await Promise.all([loadMembers(), loadClasses()]);
  } catch (failure) {
    error.value = adminErrorMessage(failure, "移除成员");
  } finally {
    busy.value = false;
  }
}

async function removeClass(): Promise<void> {
  const target = current.value;
  if (!target) return;
  if (!window.confirm(`删除班级「${target.name}」？名单会一起删除，学习记录不受影响。`)) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await adminApi.deleteClass(target.id);
    selected.value = "";
    members.value = [];
    notice.value = `已删除「${target.name}」。`;
    await loadClasses();
  } catch (failure) {
    error.value = adminErrorMessage(failure, "删除班级");
  } finally {
    busy.value = false;
  }
}

onMounted(loadClasses);
</script>

<template>
  <AdminPageFrame
    title="班级"
    description="建班、指派任课老师、维护名单。以「教师」身份加入班级会同时开通教师身份，他随即能在教师端看到本班学生；移出只收回这个班。"
  >
    <template #actions>
      <button class="admin-primary" type="button" :disabled="loading" @click="loadClasses">刷新</button>
    </template>

    <section class="admin-panel cls">
      <header class="admin-section-head">
        <h2>新建班级</h2>
        <p class="admin-muted">学期可以不填。</p>
      </header>
      <div class="admin-toolbar">
        <label class="admin-field">
          <span>班级名称</span>
          <input v-model="newName" placeholder="如：数据结构 1 班" @keyup.enter="create" />
        </label>
        <label class="admin-field">
          <span>学期</span>
          <input v-model="newTerm" placeholder="如：2026 秋" @keyup.enter="create" />
        </label>
        <button type="button" class="admin-primary" data-testid="create-class" :disabled="busy" @click="create">创建</button>
      </div>
    </section>

    <section class="admin-panel cls">
      <header class="admin-section-head">
        <h2>全部班级</h2>
        <p class="admin-muted">共 {{ classes.length }} 个 · 点一个班级查看并维护名单</p>
      </header>

      <p v-if="notice" class="admin-muted cls__notice">{{ notice }}</p>
      <p v-if="error" class="admin-danger">{{ error }}</p>

      <div class="admin-data-surface">
        <table class="cls__table">
          <thead>
            <tr>
              <th>班级</th>
              <th>学期</th>
              <th>学生</th>
              <th>任课老师</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="entry in classes"
              :key="entry.id"
              :class="{ 'cls__row--active': entry.id === selected }"
              @click="pick(entry.id)"
            >
              <td>{{ entry.name }}</td>
              <td class="admin-muted">{{ entry.term ?? "—" }}</td>
              <td>{{ entry.students }}</td>
              <td class="admin-muted">{{ entry.teachers.length ? entry.teachers.join("、") : "还没有任课老师" }}</td>
              <td>
                <button
                  type="button"
                  class="cls__link"
                  :disabled="busy || entry.id !== selected"
                  @click.stop="removeClass"
                >删除</button>
              </td>
            </tr>
            <tr v-if="!loading && classes.length === 0">
              <td colspan="5" class="admin-muted">还没有班级。先在上面建一个。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section v-if="current" class="admin-panel cls">
      <header class="admin-section-head">
        <h2>{{ current.name }} 的名单</h2>
        <p class="admin-muted">共 {{ members.length }} 人</p>
      </header>

      <div class="admin-toolbar">
        <label class="admin-field">
          <span>邮箱或用户名</span>
          <input v-model="memberIdentifier" placeholder="learner@example.com" @keyup.enter="addMember" />
        </label>
        <label class="admin-field">
          <span>班内角色</span>
          <select v-model="memberRole">
            <option value="STUDENT">学生</option>
            <option value="TEACHER">教师</option>
          </select>
        </label>
        <button type="button" class="admin-primary" data-testid="add-class-member" :disabled="busy" @click="addMember">添加</button>
      </div>

      <div class="admin-data-surface">
        <table class="cls__table">
          <thead>
            <tr>
              <th>账号</th>
              <th>邮箱</th>
              <th>班内角色</th>
              <th>加入时间</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="member in members" :key="member.userId">
              <td>{{ member.username ?? "未命名" }}</td>
              <td class="admin-muted">{{ member.email }}</td>
              <td>{{ member.roleInClass === "TEACHER" ? "教师" : "学生" }}</td>
              <td class="admin-nowrap">{{ formatDate(member.joinedAt) }}</td>
              <td>
                <button type="button" class="cls__link" :disabled="busy" @click="removeMember(member)">移出</button>
              </td>
            </tr>
            <tr v-if="!loadingMembers && members.length === 0">
              <td colspan="5" class="admin-muted">这个班还没有人。用上面的输入框按邮箱或用户名添加。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </AdminPageFrame>
</template>

<style scoped>
.cls + .cls {
  margin-top: 18px;
}

.cls__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.cls__table th,
.cls__table td {
  padding: 9px 10px;
  text-align: left;
  border-bottom: 1px solid rgba(12, 44, 52, 0.1);
}

.cls__table th {
  font-weight: 600;
  color: var(--admin-muted);
  white-space: nowrap;
}

.cls__table tbody tr {
  cursor: pointer;
}

.cls__row--active {
  background: rgba(8, 118, 111, 0.06);
}

.cls__link {
  border: 1px solid rgba(8, 118, 111, 0.4);
  background: transparent;
  color: inherit;
  border-radius: 8px;
  padding: 4px 10px;
  cursor: pointer;
  font-size: 13px;
}

.cls__link:hover:not(:disabled) {
  background: rgba(8, 118, 111, 0.1);
}

.cls__link:disabled {
  cursor: default;
  opacity: 0.4;
}

.cls__notice {
  margin: 0 0 8px;
}
</style>
