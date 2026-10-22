import { afterEach, expect, it, vi } from 'vitest';
import { effectScope } from 'vue';
import { classroomQuestionApi } from './classroom-question-runtime';
import { useClassroomQuestion } from './use-classroom-question';

vi.mock('./classroom-question-runtime', () => ({
  classroomQuestionApi: {
    get: vi.fn(),
    cancel: vi.fn(),
    create: vi.fn(),
  },
}));

vi.mock('./use-classroom-events', () => ({
  useClassroomEvents: () => ({ start: vi.fn(), stop: vi.fn(), error: { value: '' } }),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

afterEach(() => vi.clearAllMocks());

it('does not reattach a cancelled question after the classroom changes', async () => {
  const scope = effectScope();
  const runtime = scope.run(() => useClassroomQuestion(vi.fn()))!;
  const question = {
    questionId: 'question-1',
    streamId: 'stream-1',
    status: 'RUNNING',
    question: 'old room question',
    answer: '',
  };
  vi.mocked(classroomQuestionApi.get).mockResolvedValue(question as never);
  await runtime.attach('old-room', question.questionId);

  const cancellation = deferred<never>();
  vi.mocked(classroomQuestionApi.cancel).mockReturnValue(cancellation.promise);
  const pendingCancel = runtime.cancel('old-room');
  runtime.stop();
  cancellation.resolve(undefined as never);
  await pendingCancel;

  expect(classroomQuestionApi.get).toHaveBeenCalledTimes(1);
  expect(runtime.question.value).toBeNull();
  scope.stop();
});
