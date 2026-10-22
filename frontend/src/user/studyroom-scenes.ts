import type { StudyroomOrchestration,StudyroomStep } from '../shared/types/studyroom';
export function scenesFor(step:StudyroomStep):StudyroomOrchestration {
  if(step.orchestration?.schemaVersion!==2)throw new Error('片段缺少展示协议，请重新准备。');
  return step.orchestration;
}
