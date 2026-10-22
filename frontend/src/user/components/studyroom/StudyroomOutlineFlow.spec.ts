import { describe,it,expect,vi,beforeEach } from "vitest";
import { mount,flushPromises } from "@vue/test-utils";
import StudyroomOutlineFlow from "./StudyroomOutlineFlow.vue";
const { api }=vi.hoisted(()=>({api:{create:vi.fn(),get:vi.fn(),revise:vi.fn(),confirm:vi.fn(),run:vi.fn()}}));
vi.mock("../../outline-runtime",()=>({outlineApi:api}));
describe("outline approval",()=>{
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
 it("returns to an already confirmed room without generating or confirming again",async()=>{
  api.get.mockResolvedValue({id:"draft-1",status:"CONFIRMED",latestRevision:3,pendingJobId:null,confirmedRunId:"run-1",confirmedRoomId:"room-1",outline:{title:"栈",units:[],objectives:[],exclusions:[]},issues:[],changes:[]});
  const wrapper=mount(StudyroomOutlineFlow,{props:{draftId:"draft-1"}});await flushPromises();
  await wrapper.get('button.primary').trigger('click');
  expect(wrapper.emitted("confirmed")?.[0]).toEqual(["room-1"]);
  expect(api.confirm).not.toHaveBeenCalled();wrapper.unmount();
 });
});
