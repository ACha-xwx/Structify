export interface StudyroomResource {
  type: "PPT" | "ANIMATION" | "C_CODE" | string;
  id: string;
  title: string;
  description: string;
  parameters: unknown;
  available: boolean;
}

export interface StudyroomStep {
  id: string;
  stepKey: string;
  type: string;
  title: string;
  explanation: string;
  evidenceIds: string[];
  resources: StudyroomResource[];
  orchestration?: StudyroomOrchestration | null;
  order: number;
  supplemental: boolean;
  parentStepId: string | null;
  createdAt: string;
}

export interface StudyroomMessage {
  id: string;
  role: string;
  content: string;
  sourceIds: string[];
  stepId: string | null;
  createdAt: string;
}

export interface StudyroomView {
  id: string;
  chapterId: string | null;
  title: string;
  initialQuestion: string;
  summary: string;
  currentStepId: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
  steps: StudyroomStep[];
  messages: StudyroomMessage[];
}

export interface StudyroomProgress {
  currentStepId: string | null;
  version: number;
  updatedAt: string;
}

export interface StudyroomScene {
  id: string;
  kind: 'TEXT' | 'PPT' | 'ANIMATION' | 'C_CODE';
  purpose: string;
  items: Array<{ resourceId: string; explanation: string; startLine?: number | null; endLine?: number | null }>;
  codeMode?: 'READ' | 'EXPERIMENT' | null;
  task?: string | null;
  stdin?: string | null;
  expectedObservation?: string | null;
}
export interface StudyroomOrchestration {
  schemaVersion: 2;
  objective: string;
  intent: 'CONCEPT' | 'PROCESS' | 'IMPLEMENTATION' | 'EXPERIMENT' | 'CHECK';
  primary: StudyroomScene;
  supplementary: StudyroomScene[];
}
export interface StudyroomFollowUpContext {
  stepId: string;
  sceneId: string;
  resourceId?: string;
  codeMode?: 'READ' | 'EXPERIMENT';
  runResult?: { stdout: string; stderr: string };
}
