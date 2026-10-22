export type ClassroomEventKind = 'phase'|'outline_item'|'outline_validated'|'content_published'|'answer_delta'|'answer_completed'|'failed'|'cancelled'|'reset';
export interface ClassroomIdentity {streamId:string;operationId:string;attempt:number;runId:string|null;revision:number|null}
export interface ClassroomEvent extends ClassroomIdentity {sequence:number;stateVersion:number;kind:ClassroomEventKind;payload:Record<string,unknown>}
export interface ClassroomStreamSnapshot extends ClassroomIdentity {cursor:number;floor:number;terminal:boolean;stateVersion:number;state:Record<string,unknown>}
export type QuestionStatus='QUEUED'|'RUNNING'|'COMPLETED'|'FAILED'|'CANCELLED';
export type ClassroomMode='READING'|'ASKING'|'ANSWERING'|'AWAITING_RESUME';
export interface QuestionReceipt {questionId:string;status:QuestionStatus;streamId:string;cursor:number}
export interface ClassroomQuestion extends QuestionReceipt {question:string;answer:string;context:Record<string,unknown>;runId:string;fragmentId:string;contentRevision:number;error?:{code:string;message:string}|null;actions?:TeachingAction[]}
export interface MaterialIdentity {roomId:string;runId:string;unitId:string;fragmentId:string;contentRevision:number;sceneId:string;resourceId:string|null}
export type MaterialPosition = {kind:'TEXT';scrollTop:number}|{kind:'PPT';pageId:string;zoom:number;scrollLeft:number;scrollTop:number;focusRegionId?:string}|{kind:'ANIMATION';traceId:string;index:number;speed:number;playing:boolean;detail:'debug'|'brief'}|{kind:'C_CODE';code:string;stdin:string;editing:boolean;scrollTop:number};
export interface LearningSnapshot {identity:MaterialIdentity;navigationVersion:number;position:MaterialPosition}
export interface TeachingAction {id:string;sequence:number;runId:string;fragmentId:string;contentRevision:number;sceneId:string;resourceId:string;kind:'CODE_HIGHLIGHT'|'MATERIAL_FOCUS'|'PPT_REGION'|'ANIMATION_SEEK';targetId?:string;regionId?:string;startLine?:number;endLine?:number;stepIndex?:number}
export interface CheckQuestion {id:string;unitId:string;contentRevision:number;prompt:string;options:Array<{id:string;text:string}>}
export interface CheckAttempt {id:string;checkId:string;selectedOption:string;correct:boolean;answerOption:string;analysis:string}
export interface CheckState {status:'PENDING'|'READY'|'FAILED';questions:CheckQuestion[];attempts:CheckAttempt[]}
