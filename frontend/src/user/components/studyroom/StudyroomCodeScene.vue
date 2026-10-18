<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { userApi } from '../../runtime';
import type { StudyroomResource, StudyroomScene } from '../../../shared/types/studyroom';
const props = defineProps<{ scene: StudyroomScene; resources: StudyroomResource[]; chapterId?: string }>();
const emit = defineEmits<{ context: [value: { resourceId?: string; codeMode: 'READ' | 'EXPERIMENT'; runResult?: { stdout: string; stderr: string } }] }>();
const source = ref(''); const code = ref(''); const stdin = ref(''); const output = ref('');
const error = ref(''); const loading = ref(false); const running = ref(false); const editing = ref(props.scene.codeMode === 'EXPERIMENT'); const expanded = ref(false);
let loadVersion = 0; let runVersion = 0;
const item = computed(() => props.scene.items[0]);
const resource = computed(() => props.resources.find(r => r.id === item.value?.resourceId && r.type.toUpperCase() === 'C_CODE'));
const lines = computed(() => source.value.split('\n').map((text, index) => ({ text, number: index + 1 })).filter(l => expanded.value || !item.value?.startLine || (l.number >= item.value.startLine && l.number <= (item.value.endLine ?? Infinity))));
function edited() { ++runVersion; running.value = false; output.value = ''; error.value = ''; report(); }
function report(runResult?: { stdout: string; stderr: string }) { emit('context', { resourceId: item.value?.resourceId, codeMode: editing.value ? 'EXPERIMENT' : 'READ', ...(runResult ? { runResult } : {}) }); }
async function load() {
  const version = ++loadVersion; ++runVersion; loading.value = true; error.value = ''; source.value = ''; code.value = ''; output.value = ''; running.value = false;
  try {
    if (!resource.value?.available) throw new Error('本步代码暂不可用。');
    const response = await userApi.listCodeSamples();
    if (version !== loadVersion) return;
    const sample = response.lessons.flatMap(l => l.samples).find(s => s.id === item.value?.resourceId);
    if (!sample) throw new Error('未找到已授权的代码示例。');
    source.value = sample.code; code.value = sample.code; stdin.value = props.scene.stdin ?? sample.stdin ?? '';
    report();
  } catch (cause) { if (version === loadVersion) error.value = cause instanceof Error ? cause.message : '代码加载失败。'; }
  finally { if (version === loadVersion) loading.value = false; }
}
async function run() {
  if (running.value || !code.value.trim()) return;
  const version = ++runVersion; running.value = true; output.value = ''; error.value = '';
  try {
    const result = await userApi.runCode({ language: 'c', code: code.value, stdin: stdin.value, chapterId: props.chapterId });
    if (version !== runVersion) return;
    output.value = [result.stdout, result.stderr].filter(Boolean).join('\n') || (result.status === 'success' ? '程序运行完成（无输出）' : result.status);
    report({ stdout: (result.stdout ?? '').slice(0, 4000), stderr: (result.stderr ?? '').slice(0, 4000) });
  } catch (cause) { if (version === runVersion) error.value = cause instanceof Error ? cause.message : '代码运行失败。'; }
  finally { if (version === runVersion) running.value = false; }
}
watch(() => item.value?.resourceId, () => { editing.value = props.scene.codeMode === 'EXPERIMENT'; void load(); }, { immediate: true });
watch(editing, () => report());
onBeforeUnmount(() => { loadVersion++; runVersion++; });
</script>
<template>
  <section class="code-scene" aria-label="本步代码">
    <header><strong>{{ resource?.title || scene.purpose }}</strong><span>{{ editing ? '代码实验' : '代码讲解' }}</span></header>
    <p v-if="scene.task" class="code-task">任务：{{ scene.task }}</p>
    <p v-if="item?.explanation" class="code-explanation">{{ item.explanation }}</p>
    <p v-if="loading" role="status">正在加载代码…</p>
    <template v-else-if="source">
      <template v-if="!editing"><pre class="code-reading" aria-label="C 代码讲解"><code><span v-for="line in lines" :key="line.number"><span class="line-number">{{ line.number }}</span>{{ line.text }}{{ '\n' }}</span></code></pre><div class="code-controls"><button v-if="item?.startLine" type="button" @click="expanded = !expanded">{{ expanded ? '只看相关片段' : '展开完整源码' }}</button><button type="button" aria-label="打开代码实验" @click="editing = true">打开代码实验</button></div></template>
      <template v-else><textarea v-model="code" aria-label="C 代码编辑器" class="studyroom__code" spellcheck="false" @input="edited()"></textarea><label>标准输入<textarea v-model="stdin" aria-label="标准输入" class="code-stdin" @input="edited()"></textarea></label><div class="code-controls"><button type="button" class="studyroom__run" aria-label="运行 C 程序" :disabled="running || !code.trim()" @click="run">{{ running ? '运行中…' : '运行 C 程序' }}</button><button type="button" @click="editing = false">返回代码讲解</button></div></template>
    </template>
    <p v-if="scene.expectedObservation" class="code-observation">观察目标：{{ scene.expectedObservation }}</p>
    <p v-if="error" role="alert">{{ error }} <button v-if="!source" type="button" @click="load">重新加载</button></p>
    <pre v-if="output" class="code-output" aria-live="polite">{{ output }}</pre>
  </section>
</template>
<style scoped>
.code-scene { display:grid; gap:14px; min-width:0; }header { display:flex; gap:12px; justify-content:space-between; font-size:13px; }header span { color:var(--text-muted); }.code-task,.code-explanation,.code-observation { margin:0; line-height:1.8; max-width:75ch; white-space:pre-wrap; }
button { min-height:36px; padding:7px 12px; border:1px solid var(--line); border-radius:6px; color:var(--text); background:var(--bg); cursor:pointer; }button:disabled { opacity:.5; cursor:default; }button:focus-visible,textarea:focus-visible { outline:2px solid var(--text); outline-offset:2px; }.code-controls { display:flex; gap:10px; flex-wrap:wrap; }
.code-reading,.code-output,.studyroom__code { margin:0; padding:16px; border:1px solid var(--line); border-radius:6px; background:var(--bg); color:var(--text); font:13px/1.8 var(--font-mono,monospace); overflow:auto; }.code-reading { max-height:60vh; }.line-number { display:inline-block; width:3em; user-select:none; color:var(--text-muted); }.studyroom__code { width:100%; min-height:320px; resize:vertical; box-sizing:border-box; }label { display:grid; gap:6px; font-size:12px; }.code-stdin { min-height:65px; background:var(--bg); color:var(--text); border:1px solid var(--line); border-radius:6px; padding:10px; }.code-output { white-space:pre-wrap; overflow-wrap:anywhere; }
</style>
