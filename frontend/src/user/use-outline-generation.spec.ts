import {effectScope,nextTick,ref} from 'vue';
import {beforeEach,expect,it,vi} from 'vitest';
import {useOutlineGeneration} from './use-outline-generation';
import type {OutlineDraft} from '../shared/types/outline';

const {start,stop}=vi.hoisted(()=>({start:vi.fn(),stop:vi.fn()}));
vi.mock('./use-classroom-events',()=>({useClassroomEvents:()=>({start,stop,error:ref('')})}));
beforeEach(()=>vi.clearAllMocks());
const pending=(job='job-1')=>({id:'draft-1',pendingJobId:job,status:'GENERATING'} as OutlineDraft);

it('preserves a live preview and subscription when polling replaces the same draft/job',async()=>{
 const draft=ref<OutlineDraft|null>(null);const scope=effectScope();
 const generation=scope.run(()=>useOutlineGeneration(draft,vi.fn()))!;
 draft.value=pending();await nextTick();
 start.mock.calls[0]![0].onSnapshot({state:{phase:'PLANNING',units:[{title:'栈',objective:'理解 LIFO'}]}});
 draft.value=pending();await nextTick();
 expect(start).toHaveBeenCalledTimes(1);
 expect(generation.units.value).toEqual([{title:'栈',objective:'理解 LIFO'}]);
 expect(generation.phase.value).toBe('PLANNING');scope.stop();
});

it('resets on a new job and rejects callbacks from the replaced job',async()=>{
 const draft=ref<OutlineDraft|null>(null);const refresh=vi.fn();const scope=effectScope();
 const generation=scope.run(()=>useOutlineGeneration(draft,refresh))!;
 draft.value=pending();await nextTick();const old=start.mock.calls[0]![0];
 draft.value=pending('job-2');await nextTick();
 old.onEvent({kind:'outline_item',payload:{title:'旧知识点',objective:'旧目标'}});
 old.onEvent({kind:'outline_validated',payload:{}});
 expect(generation.units.value).toEqual([]);expect(refresh).not.toHaveBeenCalled();
 const current=start.mock.calls[1]![0];
 current.onEvent({kind:'outline_item',payload:{title:'新知识点',objective:'新目标'}});
 expect(generation.units.value[0]?.title).toBe('新知识点');scope.stop();
});
