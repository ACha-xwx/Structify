import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {flushPromises,mount,type VueWrapper} from '@vue/test-utils';
import StudyroomCheckPanel from './StudyroomCheckPanel.vue';
import {classroomCheckApi} from '../../classroom-check-runtime';
import type {CheckState,CheckAttempt} from '../../../shared/types/classroom-experience';

vi.mock('../../classroom-check-runtime',()=>({classroomCheckApi:{get:vi.fn(),attempt:vi.fn(),prepare:vi.fn()}}));
const questions=Array.from({length:3},(_,i)=>({id:`q${i+1}`,unitId:'unit',contentRevision:1,prompt:`Question ${i+1}`,options:[{id:'a',text:'First option'},{id:'b',text:'Second option'}]}));
const ready=():CheckState=>({status:'READY',questions:structuredClone(questions),attempts:[]});
const attempt:CheckAttempt={id:'attempt1',checkId:'q1',selectedOption:'b',correct:false,answerOption:'a',analysis:'Server explanation'};
let wrapper:VueWrapper;
const button=(text:string)=>wrapper.findAll('button').find(node=>node.text()===text)!;
async function open(){wrapper=mount(StudyroomCheckPanel,{props:{runId:'run',unitId:'unit',canPrepare:true,active:true}});await flushPromises();}
beforeEach(()=>{vi.resetAllMocks();vi.mocked(classroomCheckApi.get).mockResolvedValue(ready());vi.mocked(classroomCheckApi.attempt).mockResolvedValue({...attempt});});
afterEach(()=>{wrapper?.unmount();vi.useRealTimers();});

describe('one-question quiz',()=>{
 it('pages through three questions while retaining unsaved choices and server results',async()=>{
  await open();
  expect(wrapper.findAll('form')).toHaveLength(1);
  expect(wrapper.text()).toContain('第 1 / 3 题');
  expect(wrapper.get('header').text()).toBe('知识点小测第 1 / 3 题');
  expect(wrapper.text()).not.toContain('CHECK YOUR UNDERSTANDING');
  expect(wrapper.text()).not.toContain('小测只记录本次作答');
  expect(wrapper.get('form').element.nextElementSibling).toBe(wrapper.get('nav').element);
  expect(wrapper.text()).not.toContain('Question 2');
  expect(button('上一题').attributes('disabled')).toBeDefined();
  await wrapper.get('input[value="b"]').setValue();
  await button('下一题').trigger('click');
  expect(wrapper.get('legend').text()).toContain('Question 2');
  await wrapper.get('input[value="a"]').setValue();
  await button('上一题').trigger('click');
  expect((wrapper.get('input[value="b"]').element as HTMLInputElement).checked).toBe(true);
  await wrapper.get('form').trigger('submit');await flushPromises();
  expect(classroomCheckApi.attempt).toHaveBeenCalledWith('run','q1','b',expect.any(String));
  expect(wrapper.text()).toContain('Server explanation');
  await button('下一题').trigger('click');
  expect((wrapper.get('input[value="a"]').element as HTMLInputElement).checked).toBe(true);
  await button('下一题').trigger('click');
  expect(wrapper.text()).toContain('第 3 / 3 题');
  expect(button('下一题').attributes('disabled')).toBeDefined();
  await button('上一题').trigger('click');await button('上一题').trigger('click');
  expect(wrapper.text()).toContain('Server explanation');
  await button('再做一次').trigger('click');
  expect(wrapper.find('.check-result').exists()).toBe(false);
  expect(wrapper.findAll('input').some(node=>(node.element as HTMLInputElement).checked)).toBe(false);
 });

 it('clamps a refreshed question count and resets pagination on identity change',async()=>{
  await open();await button('下一题').trigger('click');await button('下一题').trigger('click');
  vi.mocked(classroomCheckApi.get).mockResolvedValue({...ready(),questions:[questions[0]!]});
  await wrapper.setProps({active:false});await wrapper.setProps({active:true});await flushPromises();
  expect(wrapper.text()).toContain('第 1 / 1 题');
  vi.mocked(classroomCheckApi.get).mockResolvedValue(ready());
  await wrapper.setProps({active:false});await wrapper.setProps({active:true});await flushPromises();
  await button('下一题').trigger('click');await wrapper.get('input[value="a"]').setValue();
  await wrapper.setProps({unitId:'next'});await flushPromises();
  expect(wrapper.text()).toContain('第 1 / 3 题');
  expect(wrapper.findAll('input').some(node=>(node.element as HTMLInputElement).checked)).toBe(false);
 });

 it('retains retry idempotency and ignores a late grading result after changing identity',async()=>{
  await open();await wrapper.get('input[value="b"]').setValue();
  vi.mocked(classroomCheckApi.attempt).mockRejectedValueOnce(new Error('Retry submission'));
  await wrapper.get('form').trigger('submit');await flushPromises();
  const firstKey=vi.mocked(classroomCheckApi.attempt).mock.calls[0]![3];
  let resolve!:(value:CheckAttempt)=>void;
  vi.mocked(classroomCheckApi.attempt).mockImplementationOnce(()=>new Promise(done=>{resolve=done;}));
  await wrapper.get('form').trigger('submit');
  expect(vi.mocked(classroomCheckApi.attempt).mock.calls[1]![3]).toBe(firstKey);
  await wrapper.setProps({unitId:'next'});await flushPromises();
  resolve(attempt);await flushPromises();
  expect(wrapper.find('.check-result').exists()).toBe(false);
  expect(button('提交答案').attributes('disabled')).toBeDefined();
 });

 it('displays persisted server attempts and cancels obsolete reads when identity changes',async()=>{
  let resolve!:(value:CheckState)=>void;
  vi.mocked(classroomCheckApi.get).mockImplementationOnce(()=>new Promise(done=>{resolve=done;}));
  wrapper=mount(StudyroomCheckPanel,{props:{runId:'run',unitId:'old',canPrepare:true}});
  const oldSignal=vi.mocked(classroomCheckApi.get).mock.calls[0]![2]!;
  vi.mocked(classroomCheckApi.get).mockResolvedValue({...ready(),attempts:[{...attempt,id:'earlier',selectedOption:'a'}, {...attempt}]});
  await wrapper.setProps({unitId:'current'});await flushPromises();
  expect(oldSignal.aborted).toBe(true);
  expect(wrapper.text()).toContain('Server explanation');
  expect((wrapper.get('input[value="b"]').element as HTMLInputElement).checked).toBe(true);
  resolve({...ready(),questions:[]});await flushPromises();
  expect(wrapper.text()).toContain('第 1 / 3 题');
  expect(wrapper.text()).toContain('Server explanation');
 });

 it('retains pending polling, failed preparation retry and empty state',async()=>{
  vi.useFakeTimers();
  vi.mocked(classroomCheckApi.get).mockResolvedValueOnce({status:'PENDING',questions:[],attempts:[]}).mockResolvedValueOnce({status:'FAILED',questions:[],attempts:[]});
  await open();expect(wrapper.get('[role="status"]').text()).toContain('准备完成');
  await vi.advanceTimersByTimeAsync(2000);await flushPromises();
  expect(button('重新准备小测')).toBeDefined();
  vi.mocked(classroomCheckApi.prepare).mockResolvedValue({jobId:'job'});
  vi.mocked(classroomCheckApi.get).mockResolvedValue({status:'READY',questions:[],attempts:[]});
  await button('重新准备小测').trigger('click');await flushPromises();
  expect(classroomCheckApi.prepare).toHaveBeenCalledWith('run','unit',expect.any(String));
  expect(wrapper.text()).toContain('暂无题目');
  expect(wrapper.find('form').exists()).toBe(false);
 });

 it('ignores an older read after reactivation and a subsequent saved answer',async()=>{
  let resolveOld!:(value:CheckState)=>void;
  vi.mocked(classroomCheckApi.get).mockImplementationOnce(()=>new Promise(done=>{resolveOld=done;}));
  await open();
  await wrapper.setProps({active:false});await wrapper.setProps({active:true});await flushPromises();
  await wrapper.get('input[value="b"]').setValue();
  await wrapper.get('form').trigger('submit');await flushPromises();
  expect(wrapper.text()).toContain('Server explanation');
  resolveOld(ready());await flushPromises();
  expect(wrapper.text()).toContain('Server explanation');
  expect(wrapper.get('fieldset').attributes('disabled')).toBeDefined();
 });

 it('keeps a committed attempt when an overlapping refresh returns an older snapshot',async()=>{
  await open();
  let resolveRefresh!:(value:CheckState)=>void;
  vi.mocked(classroomCheckApi.get).mockImplementationOnce(()=>new Promise(done=>{resolveRefresh=done;}));
  await wrapper.setProps({active:false});await wrapper.setProps({active:true});
  await wrapper.get('input[value="b"]').setValue();
  await wrapper.get('form').trigger('submit');await flushPromises();
  expect(wrapper.text()).toContain('Server explanation');
  resolveRefresh(ready());await flushPromises();
  expect(wrapper.text()).toContain('Server explanation');
  expect(wrapper.findAll('form')).toHaveLength(1);
 });

 it('keeps the post-prepare read when an older refresh completes later',async()=>{
  vi.mocked(classroomCheckApi.get).mockResolvedValueOnce({status:'FAILED',questions:[],attempts:[]});
  await open();
  let resolveRefresh!:(value:CheckState)=>void;
  vi.mocked(classroomCheckApi.get).mockImplementationOnce(()=>new Promise(done=>{resolveRefresh=done;}));
  await wrapper.setProps({active:false});await wrapper.setProps({active:true});
  vi.mocked(classroomCheckApi.prepare).mockResolvedValue({jobId:'job'});
  await button('重新准备小测').trigger('click');await flushPromises();
  expect(wrapper.text()).toContain('第 1 / 3 题');
  resolveRefresh({status:'FAILED',questions:[],attempts:[]});await flushPromises();
  expect(wrapper.text()).toContain('第 1 / 3 题');
  expect(wrapper.find('form').exists()).toBe(true);
 });

 it('stops pending polling while hidden and resumes with one read on return',async()=>{
  vi.useFakeTimers();
  vi.mocked(classroomCheckApi.get).mockResolvedValue({status:'PENDING',questions:[],attempts:[]});
  await open();
  await wrapper.setProps({active:false});
  await vi.advanceTimersByTimeAsync(6000);await flushPromises();
  expect(classroomCheckApi.get).toHaveBeenCalledTimes(1);
  await wrapper.setProps({active:true});await flushPromises();
  expect(classroomCheckApi.get).toHaveBeenCalledTimes(2);
 });

 it('does not refresh across panel toggles while an answer is being saved',async()=>{
  await open();
  let resolveAttempt!:(value:CheckAttempt)=>void;
  vi.mocked(classroomCheckApi.attempt).mockImplementationOnce(()=>new Promise(done=>{resolveAttempt=done;}));
  await wrapper.get('input[value="b"]').setValue();await wrapper.get('form').trigger('submit');
  await wrapper.setProps({active:false});await wrapper.setProps({active:true});
  expect(classroomCheckApi.get).toHaveBeenCalledTimes(1);
  resolveAttempt({...attempt});await flushPromises();
  expect(wrapper.text()).toContain('Server explanation');
 });
});
