import {api} from '../app/providers/runtime';
import type {CheckState,CheckAttempt} from '../shared/types/classroom-experience';
async function read<T>(path:string,body?:unknown,key?:string,signal?:AbortSignal):Promise<T> {
 const response=await api.request<T>(path,body===undefined?{signal}:{method:'POST',body,headers:{'Idempotency-Key':key!},signal});
 if(response.kind!=='json')throw new Error('自检服务返回格式不正确');return response.data;
}
const base=(run:string)=>`/studyroom-runs/${encodeURIComponent(run)}`;
export const classroomCheckApi={
 get:(run:string,unit:string,signal?:AbortSignal)=>read<CheckState>(`${base(run)}/knowledge-points/${encodeURIComponent(unit)}/checks`,undefined,undefined,signal),
 prepare:(run:string,unit:string,key:string)=>read<{jobId:string}>(`${base(run)}/knowledge-points/${encodeURIComponent(unit)}/checks`,{},key),
 attempt:(run:string,id:string,selectedOption:string,key:string)=>read<CheckAttempt>(`${base(run)}/checks/${encodeURIComponent(id)}/attempts`,{selectedOption},key),
};
