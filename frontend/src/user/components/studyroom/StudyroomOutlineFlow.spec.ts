import { describe,it,expect,vi,beforeEach } from "vitest";
import { mount,flushPromises } from "@vue/test-utils";
import StudyroomOutlineFlow from "./StudyroomOutlineFlow.vue";
const { api }=vi.hoisted(()=>({api:{create:vi.fn(),get:vi.fn(),revise:vi.fn(),manualRevise:vi.fn(),confirm:vi.fn(),run:vi.fn()}}));
const { eventStart }=vi.hoisted(()=>({eventStart:vi.fn()}));
vi.mock("../../outline-runtime",()=>({outlineApi:api}));
vi.mock('../../use-classroom-events',async()=>{const {ref}=await import('vue');return {useClassroomEvents:()=>({start:eventStart,stop:vi.fn(),error:ref('')})};});
describe("outline approval",()=>{
 it('retains streamed row shells and opens editing only after validated completion',async()=>{
  const previous={id:'draft-1',depth:'STANDARD',status:'GENERATING',latestRevision:1,pendingJobId:'job-2',outline:{title:'旧路线',units:[{id:'old',title:'旧知识点',objective:'旧目标',materials:[]}],objectives:[],exclusions:[]},issues:[],changes:[]};
  api.get.mockResolvedValueOnce(previous);
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});
  try{
   await flushPromises();await wrapper.get('.review-trigger').trigger('click');await flushPromises();
   expect(wrapper.get('.outline-review').text()).not.toContain('旧知识点');
   const shell=wrapper.findAll('.outline-unit')[0]!.element;
   const stream=eventStart.mock.calls[0]![0];
   stream.onEvent({kind:'outline_item',payload:{title:'栈的定义',objective:'说明后进先出'}});await flushPromises();
   const row=wrapper.findAll('.outline-unit')[0]!;
   expect(row.element).toBe(shell);
   expect((row.get('[aria-label="知识点标题"]').element as HTMLTextAreaElement).value).toBe('栈的定义');
   expect(row.get('[aria-label="知识点标题"]').attributes('disabled')).toBeDefined();
   expect(row.get('[aria-label="删除知识点"]').attributes('disabled')).toBeDefined();
   expect(wrapper.get('[data-action="confirm"]').attributes('disabled')).toBeDefined();
   stream.onEvent({kind:'outline_item',payload:{title:'栈的操作',objective:'实现出栈'}});await flushPromises();
   const secondShell=wrapper.findAll('.outline-unit')[1]!.element;
   api.get.mockResolvedValueOnce({...previous,status:'REVIEWABLE',latestRevision:2,pendingJobId:null,planHash:'hash',outline:{title:'栈',units:[{id:'u1',title:'栈的定义',objective:'说明后进先出',materials:[]},{id:'u2',title:'栈的操作',objective:'实现出栈',materials:[]}],objectives:[],exclusions:[]}});
   stream.onEvent({kind:'outline_validated',payload:{}});await flushPromises();
   expect(wrapper.findAll('.outline-unit')[0]!.element).toBe(shell);
   expect(row.get('[aria-label="知识点标题"]').attributes('disabled')).toBeUndefined();
   expect(wrapper.get('[data-action="confirm"]').attributes('disabled')).toBeUndefined();
   await row.get('[aria-label="知识点标题"]').setValue('新的定义');
   await row.get('[aria-label="下移知识点"]').trigger('click');
   expect(wrapper.findAll('.outline-unit')[0]!.element).toBe(secondShell);
   expect(wrapper.findAll('.outline-unit')[1]!.element).toBe(shell);
   expect(wrapper.get('[data-action="confirm"]').attributes('disabled')).toBeDefined();
   expect(api.manualRevise).not.toHaveBeenCalled();
  }finally{wrapper.unmount();}
 });
 it('cancels the ready-screen navigation when the draft changes',async()=>{
  vi.useFakeTimers();
  const fixture={id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,planHash:'hash',outline:{title:'栈',units:[],objectives:[],exclusions:[]},issues:[],changes:[]};
  api.get.mockResolvedValue(fixture);api.confirm.mockResolvedValue({roomId:'room-1',runId:'run-1'});api.run.mockResolvedValue({stateVersion:2,firstReadableFragmentId:'fragment-1',knowledgePoints:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});
  try{await flushPromises();await wrapper.get('[data-action="confirm"]').trigger('click');await flushPromises();expect(wrapper.emitted('confirmed')).toBeUndefined();api.get.mockResolvedValue({...fixture,id:'draft-2',confirmedRunId:undefined,confirmedRoomId:undefined});await wrapper.setProps({draftId:'draft-2'});await vi.advanceTimersByTimeAsync(1000);expect(wrapper.emitted('confirmed')).toBeUndefined();}
  finally{wrapper.unmount();vi.useRealTimers();}
 });
 it('still opens review if the browser refuses the shared transition',async()=>{
  api.get.mockResolvedValue({id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,outline:{title:'栈',units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const previous=Object.getOwnPropertyDescriptor(document,'startViewTransition');
  Object.defineProperty(document,'startViewTransition',{configurable:true,value:()=>{throw new Error('Transition unavailable');}});
  vi.stubGlobal('matchMedia',()=>({matches:false}));
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});
  try{await flushPromises();await wrapper.findAll('button').find(b=>b.text().includes('审阅大纲'))!.trigger('click');await flushPromises();expect(wrapper.get('form').isVisible()).toBe(true);}
  finally{wrapper.unmount();if(previous)Object.defineProperty(document,'startViewTransition',previous);else Reflect.deleteProperty(document,'startViewTransition');vi.unstubAllGlobals();}
 });
 it('lets a collapse cancel an unfinished opening transition',async()=>{
  api.get.mockResolvedValue({id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,outline:{title:'栈',units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const previous=Object.getOwnPropertyDescriptor(document,'startViewTransition');
  const skip=vi.fn();let finish!:()=>void;
  const firstFinished=new Promise<void>(resolve=>{finish=resolve;});skip.mockImplementation(finish);
  const start=vi.fn().mockImplementationOnce((change:()=>Promise<void>)=>{void change();return {finished:firstFinished,skipTransition:skip};}).mockImplementation((change:()=>Promise<void>)=>{void change();return {finished:Promise.resolve(),skipTransition:vi.fn()};});
  Object.defineProperty(document,'startViewTransition',{configurable:true,value:start});
  vi.stubGlobal('matchMedia',()=>({matches:false}));
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});
  try{await flushPromises();await wrapper.get('.review-trigger').trigger('click');await wrapper.get('.review-toggle').trigger('click');await flushPromises();expect(skip).toHaveBeenCalledOnce();expect(wrapper.get('form').isVisible()).toBe(false);}
  finally{finish();wrapper.unmount();if(previous)Object.defineProperty(document,'startViewTransition',previous);else Reflect.deleteProperty(document,'startViewTransition');vi.unstubAllGlobals();}
 });
 it('preserves reordered edits through collapse and saves stable IDs',async()=>{
 const fixture={id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,planHash:'hash',outline:{title:'栈',units:[{id:'u1',title:'定义',objective:'说明定义',materials:[]},{id:'u2',title:'操作',objective:'说明操作',materials:[]}],objectives:[],exclusions:[]},issues:[],changes:[]};api.get.mockResolvedValue(fixture);
 const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});await flushPromises();const click=async(label:string)=>{await wrapper.findAll('button').find(b=>b.text().includes(label))!.trigger('click');};await click('审阅大纲');await wrapper.findAll('[aria-label="知识点标题"]')[0]!.setValue('新的定义');await wrapper.findAll('[aria-label="下移知识点"]')[0]!.trigger('click');expect(wrapper.get('[data-action="confirm"]').attributes('disabled')).toBeDefined();await click('收起大纲');await click('审阅大纲');expect((wrapper.findAll('[aria-label="知识点标题"]')[1]!.element as HTMLInputElement).value).toBe('新的定义');api.manualRevise.mockResolvedValue({draftId:'draft-1',revision:2});await click('保存修改');await flushPromises();expect(api.manualRevise).toHaveBeenCalledWith('draft-1',{baseRevision:1,units:[{id:'u2',title:'操作',objective:'说明操作'},{id:'u1',title:'新的定义',objective:'说明定义'}]},expect.any(String));
 await wrapper.findAll('[aria-label="删除知识点"]')[0]!.trigger('click');await click('撤销删除');expect(wrapper.findAll('.outline-unit')).toHaveLength(2);await click('添加知识点');expect(wrapper.findAll('.outline-unit')).toHaveLength(3);expect(wrapper.findAll('button').find(b=>b.text()==='保存修改')!.attributes('disabled')).toBeDefined();wrapper.unmount();
 });
 it('rejects blank feedback without calling the API',async()=>{
 api.get.mockResolvedValue({id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,outline:{title:'栈',units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
 const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});await flushPromises();await wrapper.findAll('button').find(b=>b.text().includes('审阅大纲'))!.trigger('click');await wrapper.findAll('button').find(b=>b.text()==='修改大纲')!.trigger('click');await wrapper.get('#outline-feedback').setValue('   ');expect(wrapper.findAll('button').find(b=>b.text()==='按意见调整大纲')!.attributes('disabled')).toBeDefined();await wrapper.get('form').trigger('submit');await flushPromises();expect(api.revise).not.toHaveBeenCalled();wrapper.unmount();
 });
 it("lets a failed question return to an editable topic without retrying generation",async()=>{
  api.get.mockResolvedValue({id:"draft-1",status:"FAILED",latestRevision:0,pendingJobId:null,outline:null,issues:[],changes:[],generation:{status:"FAILED",failure_message:"没有可用的授权资料"}});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  var review=wrapper.findAll("button").find(b=>/审阅大纲|点击审阅/.test(b.text()));if(review)await review.trigger("click");
  expect(wrapper.get('a[href="/studyroom"]').text()).toBe('更换学习问题');
  expect(api.create).not.toHaveBeenCalled();expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
 beforeEach(()=>vi.clearAllMocks());
 it("stays on the preparation page until a fragment is readable",async()=>{
  api.get.mockResolvedValue({id:"draft-1",depth:"STANDARD",status:"REVIEWABLE",latestRevision:1,pendingJobId:null,planHash:"hash",outline:{title:"栈",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  api.confirm.mockResolvedValue({roomId:"room-1",runId:"run-1"});
  api.run.mockResolvedValue({stateVersion:1,firstReadableFragmentId:null,knowledgePoints:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  var review=wrapper.findAll("button").find(b=>/审阅大纲|点击审阅/.test(b.text()));if(review)await review.trigger("click");
  await wrapper.get('button[data-action="confirm"]').trigger('click');await flushPromises();
  var review=wrapper.findAll("button").find(b=>/审阅大纲|点击审阅/.test(b.text()));if(review)await review.trigger("click");
  expect(wrapper.emitted("confirmed")).toBeUndefined();
  expect(wrapper.text()).toContain("首段");wrapper.unmount();api.confirm.mockClear();
 });
 it("offers three depths and only creates a draft on first submission",async()=>{
  api.create.mockResolvedValue({draftId:"draft-1"});
  const wrapper=mount(StudyroomOutlineFlow);
  expect(wrapper.findAll('input[name="depth"]')).toHaveLength(3);
  await wrapper.get('textarea').setValue("栈的知识有哪些");
  await wrapper.get('form').trigger('submit');await flushPromises();
  var review=wrapper.findAll("button").find(b=>/审阅大纲|点击审阅/.test(b.text()));if(review)await review.trigger("click");
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({depth:"STANDARD",question:"栈的知识有哪些"}),expect.any(String));
  expect(api.confirm).not.toHaveBeenCalled();
  expect(wrapper.emitted("draft")?.[0]).toEqual(["draft-1"]);wrapper.unmount();
 });
 it("blocks confirmation while a revision is generating",async()=>{
  api.get.mockResolvedValue({id:"draft-1",status:"GENERATING",latestRevision:1,pendingJobId:"job-2",outline:{title:"栈",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  var review=wrapper.findAll("button").find(b=>/审阅大纲|点击审阅/.test(b.text()));if(review)await review.trigger("click");
  expect(wrapper.find('button[data-action="confirm"]').attributes('disabled')).toBeDefined();
  expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
 it("pairs each objective with its point and keeps course-wide goals behind disclosure",async()=>{
  api.get.mockResolvedValue({id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,outline:{title:'栈',objectives:['课程总体目标'],units:[{id:'u1',title:'栈的定义',objective:'说明后进先出',materials:[]},{id:'u2',title:'入栈出栈',objective:'判断栈顶变化',materials:[]}],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});await flushPromises();
  var review=wrapper.findAll("button").find(b=>/审阅大纲|点击审阅/.test(b.text()));if(review)await review.trigger("click");
  const rows=wrapper.findAll('.outline-unit');expect(rows).toHaveLength(2);
  expect((rows[0]!.get('[aria-label="知识点目标"]').element as HTMLTextAreaElement).value).toBe('说明后进先出');
  expect((rows[1]!.get('[aria-label="知识点目标"]').element as HTMLTextAreaElement).value).toBe('判断栈顶变化');
  expect(wrapper.get('[data-course-goals]').element.tagName).toBe('DETAILS');
  expect(wrapper.get('[data-course-goals]').attributes('open')).toBeUndefined();
  expect(wrapper.get('.outline-actions').text()).toContain('确认大纲，开始学习');wrapper.unmount();
 });
 it('shows initial load failure and retries without an empty review card',async()=>{
  api.get.mockRejectedValueOnce(new Error('Unavailable')).mockResolvedValueOnce({id:'draft-1',depth:'STANDARD',latestRevision:1,outline:{title:'Stack',units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});await flushPromises();
  expect(wrapper.get('.outline-load-error').text()).toContain('Unavailable');
  expect(wrapper.find('.outline-compact').exists()).toBe(false);
  await wrapper.get('.outline-load-error button').trigger('click');await flushPromises();
  expect(wrapper.find('.outline-load-error').exists()).toBe(false);expect(wrapper.find('.outline-compact').exists()).toBe(true);wrapper.unmount();
 });
 it("loads confirmed drafts directly into preparation without flashing review",async()=>{
  let resolve!:(value:unknown)=>void;
  api.get.mockImplementationOnce(()=>new Promise(done=>{resolve=done;}));
  api.run.mockResolvedValue({stateVersion:1,firstReadableFragmentId:null,knowledgePoints:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});
  expect(wrapper.find('.outline-loading').exists()).toBe(true);
  expect(wrapper.find('.outline-compact').exists()).toBe(false);
  expect(wrapper.get('form').isVisible()).toBe(false);
  resolve({id:"draft-1",status:"CONFIRMED",latestRevision:3,pendingJobId:null,confirmedRunId:"run-1",confirmedRoomId:"room-1",outline:{title:"Stack",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  await flushPromises();
  expect(wrapper.find('.outline-loading').exists()).toBe(false);
  expect(wrapper.find('.outline-compact').exists()).toBe(false);
  expect(wrapper.find('.preparation-card').exists()).toBe(true);
  expect(api.run).toHaveBeenCalledWith("run-1",expect.anything());
  expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
 it('clears an expanded prior review while a confirmed replacement draft loads',async()=>{
  api.get.mockResolvedValueOnce({id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,outline:{title:'旧课程',units:[{id:'old',title:'旧知识点',objective:'旧目标',materials:[]}],objectives:[],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});
  try{
   await flushPromises();await wrapper.get('.review-trigger').trigger('click');await flushPromises();
   let resolve!:(value:unknown)=>void;
   api.get.mockImplementationOnce(()=>new Promise(done=>{resolve=done;}));
   api.run.mockResolvedValue({stateVersion:1,firstReadableFragmentId:null,knowledgePoints:[]});
   await wrapper.setProps({draftId:'draft-2'});
   expect(wrapper.get('.outline-loading').isVisible()).toBe(true);
   expect(wrapper.get('.outline-loading').findAll('.outline-loading-row')).toHaveLength(3);
   expect(wrapper.find('.outline-unit').exists()).toBe(false);
   expect(wrapper.get('form').isVisible()).toBe(false);
   resolve({id:'draft-2',status:'CONFIRMED',latestRevision:3,pendingJobId:null,confirmedRunId:'run-2',confirmedRoomId:'room-2',outline:{title:'新课程',units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
   await flushPromises();
   expect(wrapper.find('.outline-compact').exists()).toBe(false);
   expect(wrapper.get('form').isVisible()).toBe(false);
   expect(wrapper.find('.preparation-card').exists()).toBe(true);
   expect(api.confirm).not.toHaveBeenCalled();
  }finally{wrapper.unmount();}
 });
});
