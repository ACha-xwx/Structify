import { beforeEach, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { createMemoryHistory, createRouter } from "vue-router";
import StudyroomView from "./StudyroomView.vue";
import StudyroomFragmentReader from '../components/studyroom/StudyroomFragmentReader.vue';
const { api, outline, fragments }=vi.hoisted(()=>({
  api:{getStudyroom:vi.fn(),followUpStudyroom:vi.fn()},
  outline:{current:vi.fn()},fragments:{focus:vi.fn(),progress:vi.fn(),retry:vi.fn(),regenerate:vi.fn()},
}));
vi.mock("../runtime",()=>({userApi:api}));
vi.mock("../outline-runtime",()=>({outlineApi:outline}));
vi.mock("../studyroom-fragment-runtime",()=>({fragmentApi:fragments}));
const fragment={id:"f",key:"f",order:0,status:"READY",stepId:"s",contentRevision:1,error:null};
const point={id:"u",title:"栈是什么",order:0,status:"PARTIAL",fragments:[fragment,{...fragment,id:"g",stepId:null,status:"FAILED",error:{code:"INVALID",message:"后续内容失败",retryable:true}}],readableFragmentCount:1,failedFragmentCount:1,hasPendingContent:false,completed:false};
const run={id:"run",roomId:"room",stateVersion:1,outline:{units:[]},knowledgePoints:[point],progress:null,firstReadableFragmentId:"f"};
const room={id:"room",title:"栈",summary:"理解栈",steps:[{id:"s",title:"栈的定义",explanation:"后进先出",resources:[],orchestration:{schemaVersion:2,intent:"CONCEPT",objective:"理解栈",primary:{id:"text",kind:"TEXT",purpose:"讲解",items:[]},supplementary:[]}}],messages:[]};
beforeEach(()=>{vi.clearAllMocks();fragments.progress.mockResolvedValue({});api.getStudyroom.mockResolvedValue(structuredClone(room));outline.current.mockResolvedValue(structuredClone(run));});
async function render(){const router=createRouter({history:createMemoryHistory(),routes:[{path:"/studyroom/:id",component:StudyroomView}]});await router.push("/studyroom/room");await router.isReady();const wrapper=mount(StudyroomView,{attachTo:document.body,global:{plugins:[router]}});await flushPromises();return wrapper;}
it("shows one knowledge route and keeps already published content readable",async()=>{const wrapper=await render();expect(wrapper.findAll('nav[aria-label="知识点路线"]')).toHaveLength(1);expect(wrapper.find('[aria-label="课堂学习步骤"]').exists()).toBe(false);expect(wrapper.text()).toContain("后进先出");wrapper.unmount();});
it("selecting a failed knowledge point does not implicitly retry",async()=>{const wrapper=await render();await wrapper.get('[data-unit-id="u"]').trigger("click");await flushPromises();expect(fragments.focus).toHaveBeenCalled();expect(fragments.retry).not.toHaveBeenCalled();wrapper.unmount();});
it("keeps current content while showing a failure at the next fragment",async()=>{const wrapper=await render();await wrapper.get('[data-action="continue"]').trigger("click");await flushPromises();expect(wrapper.text()).toContain("后进先出");expect(wrapper.text()).toContain("后续内容失败");expect(fragments.retry).not.toHaveBeenCalled();wrapper.unmount();});
it("opens self-check on demand and retains the lesson reader",async()=>{
 const wrapper=await render();const reader=wrapper.get('.fragment-reader').element;
 expect(wrapper.get('[aria-label="知识点自检"]').isVisible()).toBe(false);
 await wrapper.get('[data-action="show-check"]').trigger('click');
 expect(wrapper.get('[aria-label="知识点自检"]').isVisible()).toBe(true);
 expect(wrapper.get('.explanation').isVisible()).toBe(false);
 await wrapper.get('[data-action="read-explanation"]').trigger('click');
 expect(wrapper.get('.fragment-reader').element).toBe(reader);
 expect(wrapper.get('.fragment-reader').isVisible()).toBe(true);wrapper.unmount();
});
it('continues a ready next fragment after narration without completing the knowledge point',async()=>{
 const content=structuredClone(room);content.steps.push({...content.steps[0]!,id:'s2',title:'第二段讲解'});api.getStudyroom.mockResolvedValue(content);
 outline.current.mockResolvedValue({...structuredClone(run),knowledgePoints:[{...structuredClone(point),fragments:[fragment,{...fragment,id:'g',stepId:'s2'}]}]});
 const wrapper=await render();wrapper.findComponent(StudyroomFragmentReader).vm.$emit('lectureEnded');await flushPromises();
 const current=wrapper.findAllComponents(StudyroomFragmentReader).find(reader=>reader.props('active'))!;
 expect(current.props('fragment').id).toBe('g');expect(current.props('autoStart')).toBe(true);
 expect(fragments.progress.mock.calls.every(call=>call[3]!==true)).toBe(true);wrapper.unmount();
});
