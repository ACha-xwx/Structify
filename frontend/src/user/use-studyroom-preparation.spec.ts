import { effectScope } from 'vue';
import { flushPromises } from '@vue/test-utils';
import { afterEach, expect, it, vi } from 'vitest';
import { useStudyroomPreparation } from './use-studyroom-preparation';

afterEach(() => vi.useRealTimers());
const identity = { draftId: 'draft', revision: 1, runId: 'run', roomId: 'room' };
const ready = { stateVersion: 2, firstReadableFragmentId: 'f', knowledgePoints: [] };

it('enters exactly once after the first fragment becomes ready', async () => {
  vi.useFakeTimers();
  const read = vi.fn().mockResolvedValueOnce({ ...ready, firstReadableFragmentId: null }).mockResolvedValue(ready);
  const enter = vi.fn(); const scope = effectScope();
  const preparation = scope.run(() => useStudyroomPreparation(read, enter))!;
  preparation.start(identity); await flushPromises();
  expect(enter).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(2000);
  expect(enter).toHaveBeenCalledExactlyOnceWith(identity);
  await vi.advanceTimersByTimeAsync(10000);
  expect(enter).toHaveBeenCalledTimes(1); scope.stop();
});

it('leaving the page aborts waiting and ignores late responses', async () => {
  let resolve!: (value: typeof ready) => void;
  const read = vi.fn((_run:string,_signal:AbortSignal) => new Promise<typeof ready>(done => { resolve = done; }));
  const enter = vi.fn(); const scope = effectScope();
  const preparation = scope.run(() => useStudyroomPreparation(read, enter))!;
  preparation.start(identity); scope.stop(); resolve(ready); await flushPromises();
  expect(enter).not.toHaveBeenCalled();
  expect(read.mock.calls[0]?.[1]?.aborted).toBe(true);
});

it('replacing a run prevents a superseded result from triggering navigation', async () => {
  const pending: Array<(value: typeof ready) => void> = [];
  const read = vi.fn(() => new Promise<typeof ready>(done => pending.push(done)));
  const enter = vi.fn(); const scope = effectScope();
  const preparation = scope.run(() => useStudyroomPreparation(read, enter))!;
  preparation.start(identity);
  preparation.start({ ...identity, runId: 'new-run', revision: 2 });
  pending[0]!(ready); await flushPromises(); expect(enter).not.toHaveBeenCalled();
  pending[1]!(ready); await flushPromises(); expect(enter.mock.calls[0]?.[0].runId).toBe('new-run');
  scope.stop();
});
