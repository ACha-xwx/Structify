import { api } from "../app/providers/runtime";
import type { LearningDepth,OutlineDraft,StudyroomRun } from "../shared/types/outline";
async function read<T>(path:string,body?:unknown,key?:string):Promise<T>{
 const response=await api.request<T>(path,body===undefined?{}:{method:"POST",body,headers:{"Idempotency-Key":key!}});
 if(response.kind!=="json")throw new Error("服务返回格式不正确");
 return response.data;
}
export const outlineApi={
 create:(body:{question:string;depth:LearningDepth;chapterId?:string;sourceRoomId?:string},key:string)=>read<{draftId:string}>("/study-outlines",body,key),
 retry:(id:string,key:string)=>read<{jobId:string}>(`/study-outlines/${encodeURIComponent(id)}/retry`,{},key),
 restore:(id:string,baseRevision:number,targetRevision:number,key:string)=>read<{revision:number}>(`/study-outlines/${encodeURIComponent(id)}/restore`,{baseRevision,targetRevision},key),
 get:(id:string)=>read<OutlineDraft>(`/study-outlines/${encodeURIComponent(id)}`),
 revise:(id:string,body:{baseRevision:number;feedback:string;depth?:LearningDepth},key:string)=>read<{jobId:string}>(`/study-outlines/${encodeURIComponent(id)}/revisions`,body,key),
 confirm:(id:string,revision:number,key:string)=>read<{roomId:string;runId:string}>(`/study-outlines/${encodeURIComponent(id)}/confirm`,{revision},key),
 current:(room:string)=>read<StudyroomRun>(`/study-rooms/${encodeURIComponent(room)}/run`),
 prepare:(run:string,unit:string,key:string)=>read<{jobId:string}>(`/studyroom-runs/${encodeURIComponent(run)}/units/${encodeURIComponent(unit)}/prepare`,{},key),
};
