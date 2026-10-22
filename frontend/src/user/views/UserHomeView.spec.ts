import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {flushPromises,mount,RouterLinkStub} from '@vue/test-utils';
import UserHomeView from './UserHomeView.vue';
import {setLocale} from '../../shared/i18n/locale';
const library=vi.hoisted(()=>({list:vi.fn(),rename:vi.fn(),remove:vi.fn()}));
vi.mock('../course-library',async original=>({...await original<typeof import('../course-library')>(),courseLibrary:library}));
vi.mock('../../app/providers/runtime',()=>({api:{},auth:{state:{user:null}}}));
const sample={id:'one',title:'栈与队列',status:'LEARNING',roomId:'room',runId:'run',totalPoints:5,completedPoints:2,readableFragments:3,lastPointTitle:'栈的操作',lastLearningAt:'2026-10-19T08:00:00Z',activityAt:'2026-10-19T08:00:00Z'};
const render=()=>mount(UserHomeView,{global:{stubs:{RouterLink:RouterLinkStub}}});
beforeEach(()=>{vi.resetAllMocks();setLocale('zh-CN');library.list.mockResolvedValue([sample,{...sample,id:'two',title:'查找算法',status:'DRAFT',roomId:null,lastLearningAt:null,activityAt:'2026-10-20T08:00:00Z'}]);});
afterEach(()=>vi.useRealTimers());
describe('learning center',()=>{
 it('ignores a list refresh started before successful deletion',async()=>{
  vi.useFakeTimers();const w=render();await flushPromises();
  let finish!:(value:unknown)=>void;library.list.mockImplementationOnce(()=>new Promise(resolve=>{finish=resolve;}));
  await vi.advanceTimersByTimeAsync(15000);expect(finish).toBeDefined();
  await w.findAll('[data-action="delete"]')[0].trigger('click');
  await w.get('.course-dialog form').trigger('submit');await flushPromises();
  expect(w.findAll('[data-testid="course-card"]')).toHaveLength(1);
  finish([sample,{...sample,id:'two',title:'查找算法'}]);await flushPromises();
  expect(w.findAll('[data-testid="course-card"]')).toHaveLength(1);w.unmount();
 });
 it('resumes actual learning and searches without altering the resume panel',async()=>{
  const w=render();await flushPromises();
  expect(w.get('[data-testid="resume"]').text()).toContain('栈的操作');
  expect(w.findAll('[data-testid="course-card"]')).toHaveLength(2);
  await w.get('input[type="search"]').setValue('查找');
  expect(w.findAll('[data-testid="course-card"]')).toHaveLength(1);
  expect(w.get('[data-testid="resume"]').text()).toContain('栈与队列');w.unmount();
 });
 it('has separate empty and failed states and a retry',async()=>{
  library.list.mockRejectedValueOnce(new Error('offline')).mockResolvedValue([]);
  const w=render();await flushPromises();expect(w.get('[role="alert"]').text()).toContain('课程');
  await w.get('[data-action="reload"]').trigger('click');await flushPromises();
  expect(w.text()).toContain('还没有课程');expect(w.find('[role="alert"]').exists()).toBe(false);w.unmount();
 });
 it('confirms rename and deletion and retains cards when requests fail',async()=>{
  const w=render();await flushPromises();
  await w.findAll('[data-action="rename"]')[0].trigger('click');
  await w.get('[name="course-title"]').setValue('我的查找课');
  await w.get('.course-dialog form').trigger('submit');await flushPromises();
  expect(library.rename).toHaveBeenCalledWith('two','我的查找课');expect(w.text()).toContain('我的查找课');
  await w.findAll('[data-action="delete"]')[0].trigger('click');
  expect(library.remove).not.toHaveBeenCalled();library.remove.mockRejectedValueOnce(new Error('offline'));
  await w.get('.course-dialog form').trigger('submit');await flushPromises();
  expect(w.findAll('[data-testid="course-card"]')).toHaveLength(2);expect(w.get('[role="alert"]').text()).toContain('删除');
  await w.get('.course-dialog form').trigger('submit');await flushPromises();
  expect(w.findAll('[data-testid="course-card"]')).toHaveLength(1);w.unmount();
 });
});
