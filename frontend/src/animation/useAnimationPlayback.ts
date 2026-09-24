import { computed, onBeforeUnmount, ref, watch, type Ref } from "vue";

/**
 * Transport shared by the animation lab and the in-classroom demo.
 *
 * `index === -1` is the initial state the engine reports before any step has run, which is why the
 * position is not clamped to zero: a learner needs to see the input before the first frame.
 */
export const PLAYBACK_SPEEDS = [0.5, 1, 2, 4] as const;

export interface AnimationPlayback {
  index: Ref<number>;
  playing: Ref<boolean>;
  speed: Ref<number>;
  /** -1 while on the initial state; otherwise the zero-based step index. */
  atStart: Ref<boolean>;
  atEnd: Ref<boolean>;
  /** 0..1 across initial state plus every step. */
  progress: Ref<number>;
  positionLabel: Ref<string>;
  next: () => void;
  previous: () => void;
  goTo: (index: number) => void;
  reset: () => void;
  play: () => void;
  pause: () => void;
  toggle: () => void;
}

export function useAnimationPlayback(
  stepCount: Ref<number>,
  options: { baseIntervalMs?: number; identity?: Ref<string> } = {},
): AnimationPlayback {
  const baseIntervalMs = options.baseIntervalMs ?? 1100;
  const index = ref(-1);
  const playing = ref(false);
  const speed = ref(1);
  // `number` rather than `ReturnType<typeof setInterval>`: the DOM timer id is what `window.clearInterval`
  // takes, and Node's `Timeout` type leaks in through the test globals otherwise.
  let timer: number | null = null;

  const lastIndex = computed(() => Math.max(0, stepCount.value) - 1);
  const atStart = computed(() => index.value <= -1);
  const atEnd = computed(() => stepCount.value === 0 || index.value >= lastIndex.value);
  const progress = computed(() => (stepCount.value === 0 ? 0 : (index.value + 1) / stepCount.value));
  const positionLabel = computed(() => (atStart.value ? `起点 / 共 ${stepCount.value} 步` : `${index.value + 1} / ${stepCount.value}`));

  function stopTimer() {
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  function startTimer() {
    stopTimer();
    timer = window.setInterval(() => {
      if (atEnd.value) {
        pause();
        return;
      }
      index.value = Math.min(lastIndex.value, index.value + 1);
      // Stop on the frame the interval reached the end on, not one interval later: leaving "暂停" on
      // screen after the last frame makes the control lie about what it will do.
      if (atEnd.value) pause();
    }, Math.max(120, baseIntervalMs / speed.value));
  }

  function pause() {
    playing.value = false;
    stopTimer();
  }

  function play() {
    if (stepCount.value === 0) return;
    // Replaying from the end restarts instead of doing nothing, which is what the button promises.
    if (atEnd.value) index.value = -1;
    playing.value = true;
    startTimer();
  }

  function toggle() {
    if (playing.value) pause();
    else play();
  }

  function next() {
    pause();
    index.value = Math.min(lastIndex.value, index.value + 1);
  }

  function previous() {
    pause();
    index.value = Math.max(-1, index.value - 1);
  }

  function goTo(target: number) {
    pause();
    index.value = Math.max(-1, Math.min(lastIndex.value, Math.trunc(target)));
  }

  function reset() {
    pause();
    index.value = -1;
  }

  watch(speed, () => {
    if (playing.value) startTimer();
  });

  // A new trace invalidates the old position and stops any running playback.
  watch(stepCount, () => {
    pause();
    index.value = -1;
  });

  // 只盯步数是不够的：连着看两条**步数相同**的动画时（"链栈进栈"→"链栈出栈"都是 2 步），
  // 第二条会停在上一条的帧号上——如果上一条正好停在末帧，「下一步」就是灰的，看上去就是
  // "卡住、动画根本没成功"（2026-09-24 用户反馈）。identity 由调用方给出，任何新动画都回到起点。
  if (options.identity) {
    watch(options.identity, () => {
      pause();
      index.value = -1;
    });
  }

  onBeforeUnmount(stopTimer);

  return { index, playing, speed, atStart, atEnd, progress, positionLabel, next, previous, goTo, reset, play, pause, toggle };
}
