import {expect,it} from 'vitest';
import {mount} from '@vue/test-utils';
import StudyroomClassroomLayout from './StudyroomClassroomLayout.vue';

it('keeps the brand inside the collapsible rail while the content header stays available',async()=>{
 const wrapper=mount(StudyroomClassroomLayout,{props:{navigationOpen:true,questionOpen:false},slots:{brand:'<a href="/">Structify</a>',navigation:'<button>课程目录</button>',header:'<button>返回学习入口</button>',default:'<p>已发布内容</p>'}});
 const rail=wrapper.get('.classroom-navigation');
 expect(rail.text()).toContain('Structify');
 expect(wrapper.get('.classroom-workspace-header').text()).toContain('返回学习入口');
 const material=wrapper.get('.classroom-material').element;
 await wrapper.setProps({navigationOpen:false});
 expect(rail.attributes('inert')).toBeDefined();
 expect(rail.attributes('aria-hidden')).toBe('true');
 expect(wrapper.get('.classroom-material').element).toBe(material);
 expect(wrapper.get('.classroom-workspace-header').isVisible()).toBe(true);
 wrapper.unmount();
});
