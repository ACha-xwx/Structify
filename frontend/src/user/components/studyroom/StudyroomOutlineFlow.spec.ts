import { describe,it,expect,vi,beforeEach } from "vitest";
import { mount,flushPromises } from "@vue/test-utils";
import StudyroomOutlineFlow from "./StudyroomOutlineFlow.vue";
const { api }=vi.hoisted(()=>({api:{create:vi.fn(),get:vi.fn(),revise:vi.fn(),confirm:vi.fn(),run:vi.fn()}}));
vi.mock("../../outline-runtime",()=>({outlineApi:api}));
describe("outline approval",()=>{
 it("lets a failed question return to an editable topic without retrying generation",async()=>{
  api.get.mockResolvedValue({id:"draft-1",status:"FAILED",latestRevision:0,pendingJobId:null,outline:null,issues:[],changes:[],generation:{status:"FAILED",failure_message:"没有可用的授权资料"}});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  expect(wrapper.get('a[href="/studyroom"]').text()).toBe('更换学习问题');
  expect(api.create).not.toHaveBeenCalled();expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
 beforeEach(()=>vi.clearAllMocks());
 it("stays on the preparation page until a fragment is readable",async()=>{
  api.get.mockResolvedValue({id:"draft-1",depth:"STANDARD",status:"REVIEWABLE",latestRevision:1,pendingJobId:null,planHash:"hash",outline:{title:"栈",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  api.confirm.mockResolvedValue({roomId:"room-1",runId:"run-1"});
  api.run.mockResolvedValue({stateVersion:1,firstReadableFragmentId:null,knowledgePoints:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  await wrapper.get('button[data-action="confirm"]').trigger('click');await flushPromises();
  expect(wrapper.emitted("confirmed")).toBeUndefined();
  expect(wrapper.text()).toContain("首段");wrapper.unmount();api.confirm.mockClear();
 });
 it("offers three depths and only creates a draft on first submission",async()=>{
  api.create.mockResolvedValue({draftId:"draft-1"});
  const wrapper=mount(StudyroomOutlineFlow);
  expect(wrapper.findAll('input[name="depth"]')).toHaveLength(3);
  await wrapper.get('textarea').setValue("栈的知识有哪些");
  await wrapper.get('form').trigger('submit');await flushPromises();
  expect(api.create).toHaveBeenCalledWith(expect.objectContaining({depth:"STANDARD",question:"栈的知识有哪些"}),expect.any(String));
  expect(api.confirm).not.toHaveBeenCalled();
  expect(wrapper.emitted("draft")?.[0]).toEqual(["draft-1"]);wrapper.unmount();
 });
 it("blocks confirmation while a revision is generating",async()=>{
  api.get.mockResolvedValue({id:"draft-1",status:"GENERATING",latestRevision:1,pendingJobId:"job-2",outline:{title:"栈",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  expect(wrapper.find('button[data-action="confirm"]').attributes('disabled')).toBeDefined();
  expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
 it("pairs each objective with its point and keeps course-wide goals behind disclosure",async()=>{
  api.get.mockResolvedValue({id:'draft-1',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,outline:{title:'栈',objectives:['课程总体目标'],units:[{id:'u1',title:'栈的定义',objective:'说明后进先出',materials:[]},{id:'u2',title:'入栈出栈',objective:'判断栈顶变化',materials:[]}],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:'draft-1'}});await flushPromises();
  const rows=wrapper.findAll('.outline-unit');expect(rows).toHaveLength(2);
  expect(rows[0]!.get('.unit-objective').text()).toBe('说明后进先出');
  expect(rows[1]!.get('.unit-objective').text()).toBe('判断栈顶变化');
  expect(wrapper.get('[data-course-goals]').element.tagName).toBe('DETAILS');
  expect(wrapper.get('[data-course-goals]').attributes('open')).toBeUndefined();
  expect(wrapper.get('.outline-actions').text()).toContain('确认大纲，开始学习');wrapper.unmount();
 });
 it("returns to an already confirmed room without generating or confirming again",async()=>{
  api.get.mockResolvedValue({id:"draft-1",status:"CONFIRMED",latestRevision:3,pendingJobId:null,confirmedRunId:"run-1",confirmedRoomId:"room-1",outline:{title:"栈",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  await wrapper.get('button.primary').trigger('click');
  expect(wrapper.emitted("confirmed")?.[0]).toEqual(["room-1"]);
  expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
});
