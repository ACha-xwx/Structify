import {expect,it,vi} from 'vitest';
import {flushPromises,mount} from '@vue/test-utils';
import StudyroomLectureControls from './StudyroomLectureControls.vue';
const {get}=vi.hoisted(()=>({get:vi.fn()}));
vi.mock('../../lecture-runtime',()=>({lectureApi:{get}}));

it('reserves the same narration region while the script loads and after it becomes available',async()=>{
 let resolve!:(value:unknown)=>void;
 get.mockImplementationOnce(()=>new Promise(done=>{resolve=done;}));
 const wrapper=mount(StudyroomLectureControls,{props:{runId:'run',fragmentId:'fragment',revision:1,active:true,applyCue:async()=>true}});
 const dock=wrapper.get('[aria-label="当前讲述字幕"]').element;
 expect(wrapper.get('[aria-label="当前讲述字幕"]').text()).toContain('正在准备讲述');
 resolve({fingerprint:'script',cues:[{id:'text',kind:'TEXT',text:'栈遵循后进先出'}]});await flushPromises();
 expect(wrapper.get('[aria-label="当前讲述字幕"]').element).toBe(dock);
 expect(wrapper.get('[aria-label="当前讲述字幕"]').text()).toContain('栈遵循后进先出');
 wrapper.unmount();
});
