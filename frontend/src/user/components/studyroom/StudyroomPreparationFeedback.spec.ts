import {describe,it,expect} from 'vitest';
import {mount} from '@vue/test-utils';
import StudyroomPreparationFeedback from './StudyroomPreparationFeedback.vue';
describe('classroom preparation feedback',()=>{
 it('uses real phases without invented completion',async()=>{const wrapper=mount(StudyroomPreparationFeedback,{props:{phase:'PREPARING_RESOURCES',title:'栈'}});expect(wrapper.get('[role="status"]').text()).toBe('整理课程材料');expect(wrapper.attributes('aria-busy')).toBe('true');await wrapper.setProps({phase:'GENERATING_CONTENT'});expect(wrapper.get('[role="status"]').text()).toBe('编写课堂讲解');await wrapper.setProps({phase:'PUBLISHING'});expect(wrapper.get('[role="status"]').text()).toBe('校验并发布');expect(wrapper.text()).not.toMatch(/%|语音|Agent/);wrapper.unmount();});
 it('keeps failure visible and offers the caller action slot',()=>{const wrapper=mount(StudyroomPreparationFeedback,{props:{phase:'PUBLISHING',error:'发布失败，请重试'},slots:{default:'<button>重新准备</button>'}});expect(wrapper.get('[role="alert"]').text()).toBe('发布失败，请重试');expect(wrapper.get('button').text()).toBe('重新准备');expect(wrapper.attributes('aria-busy')).toBe('false');wrapper.unmount();});
 it('announces readiness only when told by the caller',async()=>{const wrapper=mount(StudyroomPreparationFeedback,{props:{phase:'PUBLISHING'}});expect(wrapper.text()).not.toContain('课堂已准备好');await wrapper.setProps({ready:true});expect(wrapper.get('[role="status"]').text()).toBe('课堂已准备好');expect(wrapper.attributes('aria-busy')).toBe('false');wrapper.unmount();});
});
