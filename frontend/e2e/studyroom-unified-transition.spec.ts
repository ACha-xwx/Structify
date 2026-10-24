import {test,expect} from './fixtures/studyroom';

test('confirmed outline refresh never exposes the old review before preparation',async({page})=>{
 let release:()=>void=()=>{};
 const pending=new Promise<void>(resolve=>{release=resolve;});
 await page.route('**/api/v1/study-outlines/confirmed-transition',async route=>{
  await pending;
  await route.fulfill({json:{id:'confirmed-transition',question:'栈',depth:'STANDARD',status:'CONFIRMED',latestRevision:1,pendingJobId:null,confirmedRunId:'transition-run',confirmedRoomId:'transition-room',outline:{title:'栈',units:[{id:'point',title:'栈',objective:'理解后进先出',materials:[]}],objectives:[],exclusions:[]},issues:[],changes:[],revisions:[]}});
 });
 await page.route('**/api/v1/studyroom-runs/transition-run',route=>route.fulfill({json:{stateVersion:1,firstReadableFragmentId:null,knowledgePoints:[{fragments:[{status:'GENERATING'}]}]}}));
 await page.route('**/api/v1/studyroom-runs/transition-run/events?*',route=>route.fulfill({contentType:'text/event-stream',body:''}));
 await page.addInitScript(()=>{
  const seen:string[]=[];
  (window as unknown as {transitionSeen:string[]}).transitionSeen=seen;
  function sample(){
   for(const selector of ['.outline-create','.outline-depth','.review-trigger']){
    const e=document.querySelector(selector) as HTMLElement|null;
    if(e&&e.getBoundingClientRect().width>0&&e.getBoundingClientRect().height>0)seen.push(selector);
   }
   requestAnimationFrame(sample);
  }requestAnimationFrame(sample);
 });
 await page.goto('/studyroom/outlines/confirmed-transition');
 await expect(page.locator('.outline-create')).not.toBeVisible();
 release();
 await expect(page.getByLabel('课堂准备进度')).toBeVisible();
 expect(await page.evaluate(()=>(window as unknown as {transitionSeen:string[]}).transitionSeen)).toEqual([]);
});
