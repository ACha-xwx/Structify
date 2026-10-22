export type LearningDepth="QUICK"|"STANDARD"|"DEEP";
export interface OutlineUnit { id:string; title:string; objective:string; coverageKeys:string[]; expansion:string; activities:string[]; materials:Array<{kind:string; requirement:string; purpose:string; candidateIds:string[]; availability:string}> }
export interface ReviewedOutline { schemaVersion:1; depth:LearningDepth; title:string; objectives:string[]; prerequisites:string[]; units:OutlineUnit[]; exclusions:string[]; excludedCoverageKeys:string[]; evidenceIds:string[]; curriculumVersion:string|null; coverageAssessment:string }
export interface OutlineDraft { planHash?:string|null; fragmentPreview?:Record<string,Array<{key:string;objective:string;activity:string}>>; id:string; question:string; depth:LearningDepth; status:string; latestRevision:number; pendingJobId:string|null; confirmedRunId:string|null; confirmedRoomId?:string|null; sourceRoomId?:string|null; outline:ReviewedOutline|null; issues:Array<{code:string;unitId:string|null;severity:string;message:string}>; changes:Array<{unitId:string|null;kind:string;summary:string}>; messages:Array<{role:string;content:string;revision:number}>; generation?:{status:string;failure_code:string|null;failure_message:string|null}; revisions:Array<{revision:number;feedback:string}> }
export type FragmentStatus = 'PENDING' | 'QUEUED' | 'GENERATING' | 'READY' | 'FAILED';
export interface LearningFragment {
  id:string; key:string; order:number; status:FragmentStatus; stepId:string|null; contentRevision:number;
  error:{code:string;message:string;retryable:boolean}|null;
}
export interface KnowledgePointState {
  id:string; title:string; order:number; status:'PREPARING'|'PARTIAL'|'READY'|'NEEDS_ATTENTION';
  fragments:LearningFragment[]; readableFragmentCount:number; failedFragmentCount:number;
  hasPendingContent:boolean; completed:boolean;
}
export interface StudyroomRun {
  id:string; roomId:string; status:string; outline:ReviewedOutline; codeExecutionAvailable:boolean;
  stateVersion:number; firstReadableFragmentId:string|null; knowledgePoints:KnowledgePointState[];
  progress:{unitId:string;fragmentId:string|null;completed:boolean;focusSequence:number}|null;
}
