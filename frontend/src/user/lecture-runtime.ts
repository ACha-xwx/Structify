import {api} from '../app/providers/runtime';
import type {LecturePlan,LectureRegion} from './lecture-types';
const base=(run:string,fragment:string)=>`/studyroom-runs/${encodeURIComponent(run)}/fragments/${encodeURIComponent(fragment)}/lecture`;
export const lectureApi={
 async get(run:string,fragment:string,revision:number,signal?:AbortSignal){const response=await api.request<LecturePlan>(`${base(run,fragment)}?contentRevision=${revision}`,{signal});if(response.kind!=='json')throw new Error('讲述脚本暂不可用');return response.data;},
 async save(run:string,fragment:string,revision:number,region:LectureRegion){await api.request(`${base(run,fragment)}/regions`,{method:'POST',body:{contentRevision:revision,region}});},
 async remove(run:string,fragment:string,revision:number,id:string){await api.request(`${base(run,fragment)}/regions/${encodeURIComponent(id)}?contentRevision=${revision}`,{method:'DELETE'});},
};
