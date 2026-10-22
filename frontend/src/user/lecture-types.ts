export interface LectureRegion {id:string;sceneId:string;resourceId:string;x:number;y:number;width:number;height:number;narration:string}
export interface LectureCue {id:string;text:string;kind:'TEXT'|'PPT'|'ANIMATION'|'C_CODE'|'CHECK';sceneId?:string;resourceId?:string;action?:'MATERIAL_FOCUS'|'PPT_REGION'|'CODE_HIGHLIGHT'|'ANIMATION_SEEK';regionId?:string;startLine?:number;endLine?:number;stepIndex?:number}
export interface LecturePlan {runId:string;fragmentId:string;contentRevision:number;fingerprint:string;cues:LectureCue[];regions:LectureRegion[]}
export interface LectureStageAdapter {lecture:(cue:LectureCue,signal:AbortSignal)=>Promise<boolean>}
