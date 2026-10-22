import { api } from '../app/providers/runtime';
async function write(path:string,body:unknown,key=crypto.randomUUID()){
  const response=await api.request<Record<string,unknown>>(path,{method:'POST',body,headers:{'Idempotency-Key':key}});
  if(response.kind!=='json')throw new Error('服务返回格式不正确');
  return response.data;
}
const base=(run:string)=>`/studyroom-runs/${encodeURIComponent(run)}`;
export const fragmentApi={
  focus:(run:string,unitId:string,sequence:number)=>write(`${base(run)}/focus`,{unitId,sequence}),
  progress:(run:string,unitId:string,fragmentId:string,completed=false)=>write(`${base(run)}/progress`,{unitId,fragmentId,completed}),
  retry:(run:string,fragment:string)=>write(`${base(run)}/fragments/${encodeURIComponent(fragment)}/retry`,{}),
  regenerate:(run:string,fragment:string,baseContentRevision:number)=>write(`${base(run)}/fragments/${encodeURIComponent(fragment)}/regenerate`,{baseContentRevision}),
};
