import { api } from "../app/providers/runtime";
import type { LearningDepth,OutlineDraft,StudyroomRun } from "../shared/types/outline";
async function read<T>(path:string,body?:unknown,key?:string,signal?:AbortSignal):Promise<T>{
 const response=await api.request<T>(path,body===undefined?{signal}:{method:"POST",body,headers:{"Idempotency-Key":key!},signal});
 if(response.kind!=="json")throw new Error("服务返回格式不正确");
 return response.data;
}
export const outlineApi={
 manualRevise:(id:string,body:{baseRevision:number;units:Array<{id:string;title:string;objective:string}>},key:string)=>read<{draftId:string;revision:number}>(`/study-outlines/${encodeURIComponent(id)}/manual-revisions`,body,key),
 create:(body:{question:string;depth:LearningDepth;chapterId?:string;sourceRoomId?:string},key:string)=>read<{draftId:string}>("/study-outlines",body,key),
 retry:(id:string,key:string)=>read<{jobId:string}>(`/study-outlines/${encodeURIComponent(id)}/retry`,{},key),
 restore:(id:string,baseRevision:number,targetRevision:number,key:string)=>read<{revision:number}>(`/study-outlines/${encodeURIComponent(id)}/restore`,{baseRevision,targetRevision},key),
 get:(id:string)=>read<OutlineDraft>(`/study-outlines/${encodeURIComponent(id)}`),
 revise:(id:string,body:{baseRevision:number;feedback:string;depth?:LearningDepth},key:string)=>read<{jobId:string}>(`/study-outlines/${encodeURIComponent(id)}/revisions`,body,key),
 confirm:(id:string,revision:number,key:string,planHash:string)=>read<{roomId:string;runId:string}>(`/study-outlines/${encodeURIComponent(id)}/confirm`,{revision,planHash},key),
 current:(room:string)=>read<StudyroomRun>(`/study-rooms/${encodeURIComponent(room)}/run`),
 run:(id:string,signal?:AbortSignal)=>read<StudyroomRun>(`/studyroom-runs/${encodeURIComponent(id)}`,undefined,undefined,signal),
};
