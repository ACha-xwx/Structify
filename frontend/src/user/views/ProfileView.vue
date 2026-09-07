<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { auth } from "../../app/providers/runtime";
import { useLocale } from "../../shared/i18n/locale";
import UserFrame from "../components/UserFrame.vue";
import UserState from "../components/UserState.vue";
import { createLoginTarget } from "../login-target";

const router = useRouter();
const route = useRoute();
const { isEnglish } = useLocale();
const loginTarget = computed(() => createLoginTarget(route.fullPath));
const copy = computed(() => isEnglish.value ? {
  currentAccount: "Current account",
  accountAccess: "Account & access",
  profile: "Profile",
  signedInIntro: "Only non-sensitive account information returned by the current session is shown here.",
  guestIntro: "Guests can keep using public learning tools. Sign in only when you save progress or use a model.",
  noActiveSession: "No active session",
  noActiveSessionDetail: "Sign in to save learning records and restore your personal learning workbench.",
  signIn: "Sign in",
  guestsCanLearn: "Guests can keep learning",
  guestDetail: "Courses, algorithm demonstrations, courseware, and code experiments remain available. Actions that save records or use a model will ask you to sign in here.",
  openWorkbench: "Open workbench",
  browseCourses: "Browse courses",
  email: "Email",
  accountNumber: "Account ID",
  roles: "Roles",
  sessionStatus: "Session status",
  offlineNotice: "This is the most recently verified account information. The session will be checked again when the connection returns.",
  signOut: "Sign out",
  profileUpdates: "Profile updates",
  profileUpdatesDetail: "Account information is view-only for now; profile editing is not available yet.",
  accountStatus: "Account status",
  verifiedAccount: "The account information shown here has been verified.",
  credentialProtection: "Credential protection",
  credentialDetail: "Tokens, cookies, verification codes, and passwords are never shown.",
  availableAfterSignIn: "Available after sign-in",
  availableAfterSignInDetail: "Save learning records, restore classroom sessions, and use model services.",
  guestAccess: "Guest access",
  guestAccessDetail: "Browse courses, algorithm demonstrations, courseware, and code experiments.",
  unconfirmed: "Unconfirmed",
  unknownSession: "Unknown",
  roleLabels: { STUDENT: "Student", TEACHER: "Teacher", ADMIN: "Administrator" },
  sessionLabels: { authenticated: "Active", restoring: "Restoring", disabled: "Disabled", forbidden: "No permission", offline: "Offline copy", error: "Verification failed", anonymous: "Not signed in", idle: "Waiting to restore" },
} : {
  currentAccount: "当前账户",
  accountAccess: "账户与权限",
  profile: "个人资料",
  signedInIntro: "只展示当前会话返回的非敏感账户信息。",
  guestIntro: "游客可继续使用公开学习工具；保存记录或调用模型时再登录。",
  noActiveSession: "当前没有可用会话",
  noActiveSessionDetail: "登录后可以保存学习记录，并恢复个人学习工作台。",
  signIn: "前往登录",
  guestsCanLearn: "游客可以继续学习",
  guestDetail: "课程、算法演示、课件和代码实验保持可访问；需要保存记录或调用模型时会在原位提示登录。",
  openWorkbench: "进入学习台",
  browseCourses: "浏览课程",
  email: "邮箱",
  accountNumber: "账号编号",
  roles: "角色",
  sessionStatus: "会话状态",
  offlineNotice: "当前显示的是最近一次验证成功的账户信息，网络恢复后会重新确认会话。",
  signOut: "退出登录",
  profileUpdates: "资料修改",
  profileUpdatesDetail: "当前仅支持查看账户信息，资料编辑暂未开放。",
  accountStatus: "账户状态",
  verifiedAccount: "当前显示已验证的账户信息。",
  credentialProtection: "凭据保护",
  credentialDetail: "不会显示令牌、Cookie、验证码或密码。",
  availableAfterSignIn: "登录后可用",
  availableAfterSignInDetail: "保存学习记录、恢复课堂会话和使用模型服务。",
  guestAccess: "游客可用",
  guestAccessDetail: "浏览课程、算法演示、课件和代码实验。",
  unconfirmed: "未确认",
  unknownSession: "未知状态",
  roleLabels: { STUDENT: "学生", TEACHER: "教师", ADMIN: "管理员" },
  sessionLabels: { authenticated: "有效", restoring: "恢复中", disabled: "已停用", forbidden: "无权限", offline: "离线保留", error: "验证失败", anonymous: "未登录", idle: "待恢复" },
});
const roles = computed(() => auth.state.user?.roles.map((role) => copy.value.roleLabels[role]).join("、") || copy.value.unconfirmed);
const sessionLabel = computed(() => copy.value.sessionLabels[auth.state.status] || copy.value.unknownSession);
const profileEyebrow = computed(() => auth.state.user ? copy.value.currentAccount : copy.value.accountAccess);
const profileIntro = computed(() => auth.state.user
  ? copy.value.signedInIntro
  : copy.value.guestIntro);

async function signOut() {
  await auth.logout();
  await router.replace("/login");
}
</script>

<template>
  <UserFrame shell="course" :compact="!auth.state.user">
    <section class="user-page" aria-labelledby="profile-title">
      <header class="user-page__heading"><div><p class="user-page__eyebrow">{{ profileEyebrow }}</p><h1 id="profile-title">{{ auth.state.user ? copy.profile : copy.accountAccess }}</h1><p class="user-page__intro">{{ profileIntro }}</p></div></header>
      <template v-if="!auth.state.user">
        <UserState mode="permission" :title="copy.noActiveSession" :message="copy.noActiveSessionDetail"><RouterLink class="user-action user-action--primary" :to="loginTarget">{{ copy.signIn }}</RouterLink></UserState>
        <section class="user-panel">
          <h2>{{ copy.guestsCanLearn }}</h2>
          <p>{{ copy.guestDetail }}</p>
          <div class="user-page__actions"><RouterLink class="user-action user-action--primary" to="/user/home">{{ copy.openWorkbench }}</RouterLink><RouterLink class="user-action" to="/user/chapters">{{ copy.browseCourses }}</RouterLink></div>
        </section>
      </template>
      <template v-else>
        <dl class="user-kv user-panel"><dt>{{ copy.email }}</dt><dd>{{ auth.state.user.email }}</dd><dt>{{ copy.accountNumber }}</dt><dd>{{ auth.state.user.id }}</dd><dt>{{ copy.roles }}</dt><dd>{{ roles }}</dd><dt>{{ copy.sessionStatus }}</dt><dd>{{ sessionLabel }}</dd></dl>
        <p v-if="auth.state.status === 'offline' || auth.state.status === 'error'" class="inline-notice inline-notice--warning">{{ copy.offlineNotice }}</p>
        <div class="user-page__actions"><button class="user-action user-action--danger" type="button" @click="signOut">{{ copy.signOut }}</button></div>
        <section class="user-panel"><h2>{{ copy.profileUpdates }}</h2><p>{{ copy.profileUpdatesDetail }}</p></section>
      </template>
    </section>
    <template #rail><div class="user-rail-list"><template v-if="auth.state.user"><strong>{{ copy.accountStatus }}</strong><p>{{ copy.verifiedAccount }}</p><strong>{{ copy.credentialProtection }}</strong><p>{{ copy.credentialDetail }}</p></template><template v-else><strong>{{ copy.availableAfterSignIn }}</strong><p>{{ copy.availableAfterSignInDetail }}</p><strong>{{ copy.guestAccess }}</strong><p>{{ copy.guestAccessDetail }}</p></template></div></template>
  </UserFrame>
</template>
