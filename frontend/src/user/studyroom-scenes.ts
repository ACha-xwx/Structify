import type { StudyroomOrchestration, StudyroomScene, StudyroomStep } from '../shared/types/studyroom';

export function scenesFor(step: StudyroomStep): StudyroomOrchestration {
  if (step.orchestration?.schemaVersion === 2) return step.orchestration;
  const groups = new Map<string, StudyroomScene>();
  for (const resource of step.resources) {
    const kind = resource.type.toUpperCase();
    if (!['PPT', 'ANIMATION', 'C_CODE'].includes(kind)) continue;
    const key = kind === 'PPT' ? kind : `${kind}:${resource.id}`;
    let scene = groups.get(key);
    if (!scene) {
      scene = { id: `legacy-${groups.size}`, kind: kind as StudyroomScene['kind'], purpose: resource.title || step.title, items: [], ...(kind === 'C_CODE' ? { codeMode: 'READ' as const } : {}) };
      groups.set(key, scene);
    }
    if (!scene.items.some(item => item.resourceId === resource.id)) scene.items.push({ resourceId: resource.id, explanation: '' });
  }
  const scenes = [...groups.values()];
  return { schemaVersion: 2, objective: step.title, intent: 'CONCEPT', primary: scenes[0] ?? { id: 'text', kind: 'TEXT', purpose: step.title, items: [] }, supplementary: scenes.slice(1) };
}
