import {expect,it,vi} from 'vitest';
import {mount} from '@vue/test-utils';
import StudyroomFragmentReader from './StudyroomFragmentReader.vue';
const fragment={id:'f',key:'f',order:0,status:'READY' as const,stepId:'s',contentRevision:1,error:null};
const text={id:'text',kind:'TEXT' as const,purpose:'讲解',items:[]};
const step={id:'s',stepKey:'s',type:'CONCEPT',evidenceIds:[],order:0,supplemental:false,parentStepId:null,createdAt:'2026-10-21',title:'栈',explanation:'完整讲解内容',resources:[],orchestration:{schemaVersion:2 as const,intent:'CONCEPT' as const,objective:'理解栈',primary:text,supplementary:[{id:'ppt',kind:'PPT' as const,purpose:'课件',items:[{resourceId:'slide',explanation:''}]}]}};
it('prioritizes material and keeps it mounted when reading the complete explanation',async()=>{
 const wrapper=mount(StudyroomFragmentReader,{attachTo:document.body,props:{fragment,step,active:true,busy:false}});
 const scene=wrapper.get('.slide-scene').element;
 expect(wrapper.get('.explanation').isVisible()).toBe(false);
 await wrapper.get('[data-action="read-explanation"]').trigger('click');
 expect(wrapper.get('.explanation').isVisible()).toBe(true);
 expect(wrapper.get('.slide-scene').isVisible()).toBe(false);
 await wrapper.get('[data-action="view-material"]').trigger('click');
 expect(wrapper.get('.slide-scene').element).toBe(scene);
 expect(wrapper.get('.slide-scene').isVisible()).toBe(true);wrapper.unmount();
});
it('opens the explanation directly for a text-only fragment',()=>{
 const wrapper=mount(StudyroomFragmentReader,{attachTo:document.body,props:{fragment,step:{...step,orchestration:{...step.orchestration,supplementary:[]}},active:true,busy:false}});
 expect(wrapper.get('.explanation').isVisible()).toBe(true);wrapper.unmount();
});
it('captures and restores explanation scroll within the reader',async()=>{
 const wrapper=mount(StudyroomFragmentReader,{attachTo:document.body,props:{fragment,step,active:true,busy:false}});
 await wrapper.get('[data-action="read-explanation"]').trigger('click');
 const scroller=wrapper.get<HTMLElement>('.explanation-scroll').element;scroller.scrollTop=240;
 const adapter=wrapper.vm as unknown as import('../../classroom-materials').ClassroomMaterialAdapter;
 expect(adapter.capture()).toEqual({kind:'TEXT',scrollTop:240});
 await wrapper.get('[data-action="view-material"]').trigger('click');scroller.scrollTop=0;
 await adapter.restore({kind:'TEXT',scrollTop:240});
 expect(scroller.scrollTop).toBe(240);expect(wrapper.get('.explanation').isVisible()).toBe(true);wrapper.unmount();
});
