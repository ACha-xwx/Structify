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
async function render(){const router=createRouter({history:createMemoryHistory(),routes:[{path:"/studyroom/:id",component:StudyroomView}]});await router.push("/studyroom/room");await router.isReady();const wrapper=mount(StudyroomView,{attachTo:document.body,global:{plugins:[router]}});await flushPromises();return {wrapper,router};}
it('puts branding in the collapsible rail and keeps header controls with the lesson',async()=>{
 const {wrapper}=await render();
 expect(wrapper.get('.classroom-navigation').text()).toContain('数筑');
 expect(wrapper.find('.stage__brand').exists()).toBe(false);
 expect(wrapper.find('.stage__theme').exists()).toBe(false);
 expect(wrapper.find('.studyroom-header [data-theme-toggle]').exists()).toBe(true);
 expect(wrapper.get('[aria-label="返回学习入口"]').attributes('href')).toBe('/begin');
 expect(wrapper.find('.course-info').exists()).toBe(false);
 const reader=wrapper.get('.fragment-reader').element;
 await wrapper.get('button[aria-label="收起课程目录"]').trigger('click');
 expect(wrapper.get('.classroom-navigation').attributes('aria-hidden')).toBe('true');
 expect(wrapper.find('button[aria-label="展开课程目录"]').exists()).toBe(false);
 expect(document.activeElement).toBe(wrapper.get('button[aria-label="课程目录"]').element);
 expect(wrapper.get('.fragment-reader').element).toBe(reader);
 wrapper.unmount();
});
it.each(['完整讲述','对这里提问 ↗'])('enters fullscreen with both sidebars closed and restores %s on browser exit',async(panel)=>{
 const {wrapper}=await render();const reader=wrapper.get('.fragment-reader').element;
 await wrapper.get(`button[aria-label="${panel}"]`).trigger('click');
 const fullscreenDescriptor=Object.getOwnPropertyDescriptor(document,'fullscreenElement');let current:Element|null=null;
 Object.defineProperty(document,'fullscreenElement',{configurable:true,get:()=>current});
 const root=wrapper.get('.studyroom').element as HTMLElement;
 const request=vi.fn(async()=>{current=root;document.dispatchEvent(new Event('fullscreenchange'));});
 Object.defineProperty(root,'requestFullscreen',{configurable:true,value:request});
 try{
  await wrapper.get('button[aria-label="全屏"]').trigger('click');await flushPromises();
  expect(request).toHaveBeenCalledOnce();
  expect(wrapper.get('.classroom-navigation').attributes('aria-hidden')).toBe('true');
  expect(wrapper.get(`button[aria-label="${panel}"]`).attributes('aria-expanded')).toBe('false');
  expect(wrapper.get('.classroom-layout').classes()).toContain('is-fullscreen');
  expect(wrapper.find('button[aria-label="退出全屏"]').exists()).toBe(true);
  expect(wrapper.get('.fragment-reader').element).toBe(reader);
  current=null;document.dispatchEvent(new Event('fullscreenchange'));await flushPromises();
  expect(wrapper.get('.classroom-navigation').attributes('aria-hidden')).toBe('false');
  expect(wrapper.get(`button[aria-label="${panel}"]`).attributes('aria-expanded')).toBe('true');
  expect(wrapper.get('.classroom-layout').classes()).not.toContain('is-fullscreen');
 }finally{wrapper.unmount();if(fullscreenDescriptor)Object.defineProperty(document,'fullscreenElement',fullscreenDescriptor);else Reflect.deleteProperty(document,'fullscreenElement');}
});
it('keeps the current layout if entering fullscreen is rejected',async()=>{
 const {wrapper}=await render();await wrapper.get('button[aria-label="完整讲述"]').trigger('click');
 Object.defineProperty(wrapper.get('.studyroom').element,'requestFullscreen',{value:vi.fn().mockRejectedValue(new Error('denied'))});
 await wrapper.get('button[aria-label="全屏"]').trigger('click');await flushPromises();
 expect(wrapper.get('.classroom-navigation').attributes('aria-hidden')).toBe('false');
 expect(wrapper.get('button[aria-label="完整讲述"]').attributes('aria-expanded')).toBe('true');
 expect(wrapper.get('[role="alert"]').text()).toContain('当前浏览器暂不支持全屏');wrapper.unmount();
});
 it("shows one knowledge route and keeps already published content readable",async()=>{const {wrapper}=await render();expect(wrapper.findAll('nav[aria-label="课程页面目录"]')).toHaveLength(1);expect(wrapper.find('[aria-label="课堂学习步骤"]').exists()).toBe(false);expect(wrapper.text()).toContain("后进先出");wrapper.unmount();});
 it("clears the unsent question when switching rooms",async()=>{const {wrapper,router}=await render();await wrapper.get('[data-action="open-question"]').trigger('click');await wrapper.get('#classroom-question').setValue('上一门课的草稿');await router.push('/studyroom/other');await flushPromises();await wrapper.get('[data-action="open-question"]').trigger('click');expect((wrapper.get('#classroom-question').element as HTMLTextAreaElement).value).toBe('');wrapper.unmount();});
 it("selecting a failed knowledge point does not implicitly retry",async()=>{outline.current.mockResolvedValue({...structuredClone(run),knowledgePoints:[structuredClone(point),{...structuredClone(point),id:"failed",title:"待恢复",fragments:[{...fragment,id:"failed-f",stepId:null,status:"FAILED"}]}]});const {wrapper}=await render();await wrapper.get('[data-unit-id="failed"]').trigger("click");await flushPromises();expect(fragments.focus).toHaveBeenCalled();expect(fragments.retry).not.toHaveBeenCalled();wrapper.unmount();});
 it("keeps current content while showing a failure at the next fragment",async()=>{const {wrapper}=await render();await wrapper.get('[aria-label="下一页"]').trigger("click");await flushPromises();expect(wrapper.text()).toContain("后进先出");expect(wrapper.text()).toContain("后续内容失败");expect(fragments.retry).not.toHaveBeenCalled();wrapper.unmount();});
it("keeps the reader mounted while notes open and close",async()=>{
 const {wrapper}=await render();const reader=wrapper.get('.fragment-reader').element;
 const toggle=wrapper.get('button[aria-label="完整讲述"]');
 expect(toggle.attributes('aria-expanded')).toBe('false');
 await toggle.trigger('click');expect(toggle.attributes('aria-expanded')).toBe('true');
 expect(wrapper.get('[aria-label="完整讲解正文"]').text()).toContain('后进先出');
 await toggle.trigger('click');expect(toggle.attributes('aria-expanded')).toBe('false');
 expect(wrapper.get('.fragment-reader').element).toBe(reader);wrapper.unmount();
});
it('continues a ready next fragment after narration without completing the knowledge point',async()=>{
 const content=structuredClone(room);content.steps.push({...content.steps[0]!,id:'s2',title:'第二段讲解'});api.getStudyroom.mockResolvedValue(content);
 outline.current.mockResolvedValue({...structuredClone(run),knowledgePoints:[{...structuredClone(point),fragments:[fragment,{...fragment,id:'g',stepId:'s2'}]}]});
  const {wrapper}=await render();wrapper.findComponent(StudyroomFragmentReader).vm.$emit('lectureEnded');await flushPromises();
 const current=wrapper.findAllComponents(StudyroomFragmentReader).find(reader=>reader.props('active'))!;
 expect(current.props('fragment').id).toBe('g');expect(current.props('autoStart')).toBe(true);
 expect(fragments.progress.mock.calls.every(call=>call[3]!==true)).toBe(true);wrapper.unmount();
});
