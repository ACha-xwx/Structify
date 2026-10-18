<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import type { StudyroomStep, StudyroomView as Studyroom, StudyroomFollowUpContext } from "../../shared/types/studyroom";
import StudyroomSceneStage from "../components/studyroom/StudyroomSceneStage.vue";
import { outlineApi } from "../outline-runtime";
import type { StudyroomRun } from "../../shared/types/outline";
import { userApi } from "../runtime";
const props = withDefaults(defineProps<{ mock?: boolean }>(), { mock: false });
const route = useRoute();
const router = useRouter();
const room = ref<Studyroom | null>(null);
const run = ref<StudyroomRun | null>(null);
const activeUnitId=ref("");
let pollTimer:ReturnType<typeof setTimeout>|undefined;
const visibleSteps=computed(()=>!run.value?.id?room.value?.steps??[]:room.value?.steps.filter(s=>run.value?.stepUnits.some(u=>u.id===s.id&&u.unit_id===activeUnitId.value))??[]);
const activeStepId = ref("");
const visited = ref(new Set<string>());
const lessonContext = ref<StudyroomFollowUpContext>();
const question = ref("");
const pending = ref(false);
const error = ref("");
let roomLoadVersion = 0;
const roomId = computed(() => typeof route.params.id === "string" ? route.params.id : "");
const activeStep = computed(() => room.value?.steps.find((step) => step.id === activeStepId.value) ?? null);

const messages = computed(() => room.value?.messages ?? []);
const isInitial = computed(() => !room.value);

function setContext(value: StudyroomFollowUpContext) { if (value.stepId === activeStepId.value) lessonContext.value = value; }
watch(activeStepId, id => { if (id) visited.value.add(id); lessonContext.value = undefined; }, { flush: 'sync' });

function selectStep(step: StudyroomStep) {
  activeStepId.value = step.id;
  if (room.value) void userApi.updateStudyroomProgress(room.value.id, step.id).catch(() => { error.value = "进度暂未保存，稍后可重试。"; });
}

async function loadRoom(id: string) {
  const loadVersion = ++roomLoadVersion;
  pending.value = true;
  error.value = "";
  try {
    const loadedRoom = await userApi.getStudyroom(id);
    if (loadVersion !== roomLoadVersion) return;
    room.value = loadedRoom;
    const stepId = room.value.steps.some(s=>s.id===activeStepId.value)?activeStepId.value:room.value.currentStepId ?? room.value.steps[0]?.id ?? "";
    activeStepId.value = stepId;
    if(outlineApi.current) {
      const loadedRun=await outlineApi.current(id);if(loadVersion!==roomLoadVersion)return;run.value=loadedRun;
      if(loadedRun.id&&!loadedRun.units.some(u=>u.id===activeUnitId.value))activeUnitId.value=loadedRun.stepUnits.find(s=>s.id===stepId)?.unit_id??loadedRun.units[0]?.id??"";
      if(loadedRun.id){const selected=loadedRun.stepUnits.filter(s=>s.unit_id===activeUnitId.value);if(!selected.some(s=>s.id===activeStepId.value))activeStepId.value=selected[0]?.id??"";}
      clearTimeout(pollTimer);
      if(loadedRun.id&&loadedRun.units.some(u=>["QUEUED","GENERATING"].includes(u.status)))pollTimer=setTimeout(()=>void loadRoom(id),document.visibilityState==="hidden"?10000:2000);
    }
  } catch (cause) {
    if (loadVersion !== roomLoadVersion) return;
    error.value = cause instanceof Error ? cause.message : "课堂加载失败，请重试。";
  } finally {
    if (loadVersion === roomLoadVersion) pending.value = false;
  }
}

async function submitQuestion() {
  const value = question.value.trim();
  if (!value || pending.value) return;
  const loadVersion = ++roomLoadVersion;
  pending.value = true;
  error.value = "";
  try {
    if (room.value) {
      const updatedRoom = await userApi.followUpStudyroom(room.value.id, { question: value, ...(lessonContext.value ? { context: lessonContext.value } : {}) });
      if (loadVersion !== roomLoadVersion) return;
      room.value = updatedRoom;
    } else {
      await router.push({path:"/studyroom",query:{question:value}});
      return;
    }
    activeStepId.value = room.value.currentStepId ?? room.value.steps[0]?.id ?? "";
    question.value = "";
  } catch (cause) {
    if (loadVersion !== roomLoadVersion) return;
    error.value = cause instanceof Error ? cause.message : "暂时无法生成课堂，请稍后重试。";
  } finally {
    if (loadVersion === roomLoadVersion) pending.value = false;
  }
}

onMounted(() => { if (roomId.value) void loadRoom(roomId.value); });
watch(roomId, (id) => {
  clearTimeout(pollTimer);
  if (id) void loadRoom(id);
  else { roomLoadVersion++; room.value = null; pending.value = false; }
});
onBeforeUnmount(() => { roomLoadVersion++;clearTimeout(pollTimer); });
async function selectUnit(id:string){
 activeUnitId.value=id;const unit=run.value?.units.find(u=>u.id===id);
 if(!unit||!run.value?.id||!room.value)return;
 const step=room.value.steps.find(s=>run.value?.stepUnits.some(m=>m.id===s.id&&m.unit_id===id));if(step)selectStep(step);
 if(unit.status==="PENDING"||unit.status==="FAILED"){try{await outlineApi.prepare(run.value.id,id,crypto.randomUUID());await loadRoom(room.value.id);}catch(cause){error.value=cause instanceof Error?cause.message:"准备失败，请重试";}}
}

</script>

<template>
  <main class="studyroom">
    <header class="studyroom__header">
      <div><p class="studyroom__eyebrow">STRUCTIFY · STUDYROOM <span v-if="mock" class="studyroom__mock-badge">开发资源目录</span></p><h1>{{ room?.title || "课程问答" }}</h1><p class="studyroom__summary">{{ room?.summary || "提出一个问题，AI 将组织课件、动画与 C 代码，生成一条可追问的学习路径。" }}</p></div>
      <RouterLink class="studyroom__back" to="/">返回学习入口</RouterLink>
    </header>

    <section v-if="run?.id" class="studyroom__lesson" aria-label="学习单元"><div class="studyroom__lesson-head"><span>已确认路线 · {{run.outline.units.length}} 个单元</span><RouterLink :to="{path:'/studyroom',query:{question:room?.initialQuestion,sourceRoomId:room?.id}}">调整学习大纲</RouterLink></div><nav class="studyroom__steps"><button v-for="unit in run.units" :key="unit.id" type="button" :class="['studyroom__step','studyroom__unit',{'is-active':activeUnitId===unit.id}]" @click="selectUnit(unit.id)"><span>{{run.outline.units[unit.unit_order]?.title}}</span><small>{{({PENDING:'点击准备',QUEUED:'等待准备',GENERATING:'正在准备',READY:'已就绪',FAILED:'重试'} as Record<string,string>)[unit.status]}}</small></button></nav><details><summary>查看已确认的大纲与范围</summary><ol><li v-for="unit in run.outline.units" :key="unit.id"><strong>{{unit.title}}</strong>：{{unit.objective}}</li></ol><p v-if="run.outline.exclusions.length">本次不覆盖：{{run.outline.exclusions.join('；')}}</p></details><p v-if="run.codeExecutionAvailable===false">当前可阅读、编辑 C 代码；代码运行服务尚未配置。</p><p v-if="!visibleSteps.length" role="status">{{run.units.find(u=>u.id===activeUnitId)?.error_message||'单元准备完成后，将在这里展示讲解与材料。'}}</p></section>
    <section v-if="room && visibleSteps.length" class="studyroom__lesson" aria-label="课堂学习步骤">
      <div class="studyroom__lesson-head"><span>{{run?.id?'本单元步骤':'学习路径'}}</span><span>{{ Math.max(0, visibleSteps.findIndex((step) => step.id === activeStepId) + 1) }} / {{ visibleSteps.length }} 步</span></div>
      <nav class="studyroom__steps">
        <button v-for="(step, index) in visibleSteps" :key="step.id" type="button" :class="['studyroom__step', { 'is-active': step.id === activeStepId }]" @click="selectStep(step)">
          <span class="studyroom__step-index">{{ String(index + 1).padStart(2, "0") }}</span><span class="studyroom__step-title">{{ step.title }}</span><span v-if="step.supplemental" class="studyroom__supplement">补充</span>
        </button>
      </nav>
    </section>

    <section v-if="isInitial" class="studyroom__start">
      <span class="studyroom__start-mark" aria-hidden="true">问</span><h2>今天想弄懂什么？</h2><p>从问题开始，课堂会为你挑选相关的静态课件、演示动画或 C 示例。</p>
    </section>

    <template v-if="room">
      <section v-for="step in room.steps.filter(item => visited.has(item.id))" v-show="step.id === activeStepId && visibleSteps.some(item=>item.id===step.id)" :key="`${room.id}:${step.id}`" class="studyroom__workspace" aria-label="学习资源">
        <div class="studyroom__step-explanation"><span>本步讲解</span><p>{{ step.explanation }}</p></div>
        <StudyroomSceneStage :step="step" :chapter-id="room.chapterId ?? undefined" :active="step.id === activeStepId" @context="setContext" />
      </section>
    </template>

    <section v-if="room" class="studyroom__conversation" aria-label="课堂对话">
      <header class="studyroom__conversation-head"><div><h2>继续对话</h2><p>追问会沿用当前材料；调整学习范围时，请先修改大纲。</p></div><span>{{ messages.length }} 条消息</span></header>
      <div class="studyroom__messages" aria-live="polite">
        <article v-for="message in messages" :key="message.id" :class="['studyroom__message', `is-${message.role}`]"><span>{{ message.role === "assistant" ? "AI 导师" : "你" }}</span><p>{{ message.content }}</p></article>
      </div>
    </section>

    <form class="studyroom__composer" @submit.prevent="submitQuestion">
      <textarea v-model="question" :maxlength="4000" :placeholder="room ? '围绕当前课堂继续追问…' : '例如：栈和队列有什么区别？'" aria-label="课程问题" @keydown.enter.exact.prevent="submitQuestion"></textarea>
      <div class="studyroom__composer-foot"><span>{{ error || (pending ? (room ? "正在组织追问内容…" : "正在生成学习大纲…") : "讲解将结合文字与课堂资源") }}</span><button type="submit" :disabled="pending || !question.trim()">{{ pending ? "请稍候" : room ? "发送追问" : "生成课堂" }}</button></div>
    </form>
  </main>
</template>

<style scoped>
.studyroom { width: min(1480px, 100%); margin: 0 auto; display: grid; gap: 18px; color: var(--text); }
.studyroom > * { min-width: 0; }
.studyroom__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; padding: 4px 2px 12px; }
.studyroom__eyebrow { margin: 0 0 5px; color: var(--text-muted); font-family: var(--font-mono); font-size: 11px; letter-spacing: .1em; }
.studyroom__mock-badge { display: inline-flex; margin-left: 8px; padding: 3px 7px; border: 1px solid var(--line); border-radius: 999px; color: var(--text-muted); font-family: var(--font-ui); font-size: 10px; letter-spacing: 0; vertical-align: 1px; }
.studyroom__header h1 { margin: 0; font-size: clamp(24px, 3vw, 34px); line-height: 1.2; }
.studyroom__summary { max-width: 760px; margin: 8px 0 0; color: var(--text-muted); font-size: 14px; }
.studyroom__back { color: var(--text-muted); font-size: 13px; text-decoration: none; white-space: nowrap; }
.studyroom__lesson, .studyroom__workspace, .studyroom__conversation, .studyroom__composer, .studyroom__start { border: 1px solid var(--line); border-radius: var(--radius-lg); background: var(--surface); box-shadow: var(--shadow-sm); }
.studyroom__lesson { padding: 14px 16px 16px; }
.studyroom__lesson-head, .studyroom__conversation-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; color: var(--text-muted); font-size: 12px; }
.studyroom__steps { display: flex; gap: 9px; margin-top: 12px; overflow-x: auto; }
.studyroom__step { display: flex; flex: 1 0 160px; min-width: 0; min-height: 52px; align-items: center; gap: 10px; padding: 8px 11px; border: 1px solid var(--line); border-radius: var(--radius-md); background: var(--bg); text-align: left; cursor: pointer; }
.studyroom__unit { flex-direction: column; align-items: flex-start; justify-content: space-between; gap: 8px; }
.studyroom__unit > span { line-height: 1.5; }
.studyroom__unit small { flex-shrink: 0; white-space: nowrap; color: var(--text-muted); font-size: 11px; }
.studyroom__step.is-active { border-color: var(--text); background: var(--surface-subtle); }
.studyroom__step-index { color: var(--text-muted); font-family: var(--font-mono); font-size: 11px; }
.studyroom__step-title { overflow: hidden; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.studyroom__supplement { margin-left: auto; color: var(--text-muted); font-size: 10px; }
.studyroom__workspace { padding: 15px; }
.studyroom__step-explanation { display: flex; align-items: baseline; gap: 14px; padding: 0 2px 12px; }
.studyroom__step-explanation span { flex: none; color: var(--text-muted); font-size: 12px; }
.studyroom__step-explanation p { margin: 0; font-size: 14px; }
.studyroom__resource-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; }
.studyroom__resource-tab { display: flex; min-height: 54px; align-items: center; gap: 10px; padding: 8px 13px; border: 1px solid var(--line); border-radius: var(--radius-md) var(--radius-md) 0 0; background: var(--bg); color: var(--text-muted); cursor: pointer; text-align: left; }
.studyroom__resource-tab.is-active { border-color: var(--line-strong); border-bottom-color: var(--surface); background: var(--surface); color: var(--text); }
.studyroom__resource-icon { display: grid; width: 30px; height: 30px; place-items: center; border: 1px solid var(--line); border-radius: 8px; font-family: var(--font-mono); font-size: 11px; }
.studyroom__resource-count { margin-left: auto; color: var(--text-muted); font-family: var(--font-mono); font-size: 11px; }
.studyroom__resource-stage { min-height: 260px; padding: 15px; overflow-x: auto; border: 1px solid var(--line-strong); border-radius: 0 0 var(--radius-md) var(--radius-md); background: var(--bg); }
.studyroom__slide { display: block; width: min(100%, 1060px); max-height: min(64vh, 720px); margin: 0 auto; object-fit: contain; }
.studyroom__resource-caption { display: flex; justify-content: space-between; gap: 12px; margin-top: 10px; color: var(--text-muted); font-size: 12px; }
.studyroom__resource-caption strong { color: var(--text); }
.studyroom__resource-empty { display: grid; min-height: 250px; place-items: center; color: var(--text-muted); text-align: center; }
.studyroom__code-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 10px; font-size: 13px; }
.studyroom__run, .studyroom__composer-foot button { min-height: 38px; padding: 0 15px; border: 1px solid var(--text); border-radius: var(--radius-sm); background: var(--text); color: var(--surface); cursor: pointer; }
.studyroom__run:disabled, .studyroom__composer-foot button:disabled { opacity: .5; cursor: not-allowed; }
.studyroom__code { display: block; width: 100%; min-height: 300px; padding: 14px; resize: vertical; border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--surface); font: 13px/1.6 var(--font-mono); tab-size: 2; }
.studyroom__code-footer { display: grid; gap: 10px; margin-top: 10px; }
.studyroom__code-footer label { display: flex; align-items: center; gap: 10px; color: var(--text-muted); font-size: 12px; }
.studyroom__code-footer input { flex: 1; min-height: 34px; padding: 5px 9px; border: 1px solid var(--line); border-radius: var(--radius-sm); background: var(--surface); }
.studyroom__output { margin: 0; padding: 10px 12px; border-radius: var(--radius-sm); background: var(--surface-subtle); font: 12px/1.5 var(--font-mono); white-space: pre-wrap; }
.studyroom__start { display: grid; min-height: 240px; align-content: center; justify-items: center; padding: 28px; text-align: center; }
.studyroom__start-mark { display: grid; width: 48px; height: 48px; place-items: center; border: 1px solid var(--line-strong); border-radius: 50%; font-size: 20px; }
.studyroom__start h2 { margin: 13px 0 0; font-size: 22px; }
.studyroom__start p { max-width: 520px; margin: 8px 0 0; color: var(--text-muted); font-size: 13px; }
.studyroom__conversation { padding: 17px; }
.studyroom__conversation-head h2 { margin: 0; font-size: 17px; }
.studyroom__conversation-head p { margin: 4px 0 0; color: var(--text-muted); font-size: 12px; }
.studyroom__messages { display: grid; gap: 10px; max-height: 360px; margin-top: 14px; overflow: auto; }
.studyroom__message { width: fit-content; max-width: min(85%, 850px); padding: 11px 14px; border: 1px solid var(--line); border-radius: 4px 14px 14px; background: var(--bg); }
.studyroom__message.is-user { justify-self: end; border-radius: 14px 4px 14px 14px; background: var(--surface-subtle); }
.studyroom__message > span { color: var(--text-muted); font-size: 11px; }
.studyroom__message p { margin: 4px 0 0; font-size: 14px; white-space: pre-wrap; }
.studyroom__composer { padding: 13px; }
.studyroom__composer textarea { display: block; width: 100%; min-height: 76px; padding: 7px; resize: vertical; border: 0; outline: 0; background: transparent; font-size: 14px; }
.studyroom__composer-foot { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding-top: 9px; border-top: 1px solid var(--line); }
.studyroom__composer-foot span { color: var(--text-muted); font-size: 11px; }
@media (max-width: 680px) { .studyroom { gap: 12px; } .studyroom__header { display: block; } .studyroom__back { display: inline-block; margin-top: 8px; } .studyroom__workspace { padding: 10px; } .studyroom__step-explanation { display: block; } .studyroom__step-explanation p { margin-top: 5px; } .studyroom__resource-tab { justify-content: center; gap: 6px; padding: 7px 4px; font-size: 12px; } .studyroom__resource-icon { display: none; } .studyroom__resource-count { margin-left: 0; } .studyroom__resource-stage { padding: 9px; } .studyroom__resource-caption { display: grid; } .studyroom__message { max-width: 96%; } }
</style>
