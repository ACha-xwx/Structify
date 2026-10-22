import {api} from '../app/providers/runtime';
import type {ClassroomQuestion,QuestionReceipt} from '../shared/types/classroom-experience';
import type {StudyroomFollowUpContext} from '../shared/types/studyroom';
export interface ClassroomQuestionRequest {question:string;runId:string;fragmentId:string;contentRevision:number;context?:StudyroomFollowUpContext}
async function read<T>(path:string,body?:unknown,key?:string):Promise<T> {
 const response=await api.request<T>(path,body===undefined?{}:{method:'POST',body,headers:key?{'Idempotency-Key':key}:{}});
 if(response.kind!=='json')throw new Error('课堂服务返回格式不正确');return response.data;
}
const base=(room:string)=>`/study-rooms/${encodeURIComponent(room)}/messages`;
export const classroomQuestionApi={
 create:(room:string,body:ClassroomQuestionRequest,key:string)=>read<QuestionReceipt>(base(room),body,key),
 get:(room:string,id:string)=>read<ClassroomQuestion>(`${base(room)}/${encodeURIComponent(id)}`),
 cancel:(room:string,id:string)=>read<QuestionReceipt>(`${base(room)}/${encodeURIComponent(id)}/cancel`,{}),
 events:(room:string,id:string)=>`${base(room)}/${encodeURIComponent(id)}/events`,
};
