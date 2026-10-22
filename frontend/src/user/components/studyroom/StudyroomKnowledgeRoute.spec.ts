import { mount } from '@vue/test-utils';
import { expect, it } from 'vitest';
import StudyroomKnowledgeRoute from './StudyroomKnowledgeRoute.vue';

it('exposes one navigation and selecting failed content only changes focus', async()=>{
  const wrapper=mount(StudyroomKnowledgeRoute,{props:{activeId:'u',points:[{
    id:'u',title:'栈是什么',order:0,status:'NEEDS_ATTENTION',fragments:[],
    readableFragmentCount:0,failedFragmentCount:1,hasPendingContent:false,completed:false,
  }]}});
  expect(wrapper.findAll('nav[aria-label="知识点路线"]')).toHaveLength(1);
  await wrapper.get('button').trigger('click');
  expect(wrapper.emitted('select')).toEqual([['u']]);
  expect(wrapper.emitted('retry')).toBeUndefined();
});
