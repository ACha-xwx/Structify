import {expect,it,vi} from 'vitest';
import {mount} from '@vue/test-utils';
import StudyroomFragmentReader from './StudyroomFragmentReader.vue';
const fragment={id:'f',key:'f',order:0,status:'READY' as const,stepId:'s',contentRevision:1,error:null};
const text={id:'text',kind:'TEXT' as const,purpose:'讲解',items:[]};
const step={id:'s',stepKey:'s',type:'CONCEPT',evidenceIds:[],order:0,supplemental:false,parentStepId:null,createdAt:'2026-10-21',title:'栈',explanation:'完整讲解内容',resources:[],orchestration:{schemaVersion:2 as const,intent:'CONCEPT' as const,objective:'理解栈',primary:text,supplementary:[{id:'ppt',kind:'PPT' as const,purpose:'课件',items:[{resourceId:'slide',explanation:''}]}]}};
it('shows material without rendering explanation text or text slides',()=>{
 const wrapper=mount(StudyroomFragmentReader,{props:{fragment,step,active:true,busy:false}});
 expect(wrapper.find('.slide-scene').exists()).toBe(true);
 expect(wrapper.find('.explanation').exists()).toBe(false);
 expect(wrapper.find('.text-slides').exists()).toBe(false);
 wrapper.unmount();
});
it('keeps text-only teaching in the notes rather than generating slides',()=>{
 const wrapper=mount(StudyroomFragmentReader,{props:{fragment,step:{...step,orchestration:{...step.orchestration,supplementary:[]}},active:true,busy:false}});
 expect(wrapper.text()).not.toContain(step.explanation);
 expect(wrapper.find('.text-slide').exists()).toBe(false);
 wrapper.unmount();
});
