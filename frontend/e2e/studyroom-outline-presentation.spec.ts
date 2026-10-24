import {test,expect} from './fixtures/studyroom';

test.use({video:'on'});

test('question entry stays compact while review retains a bounded work area',async({page},testInfo)=>{
 await page.setViewportSize({width:1366,height:900});
 await page.goto('/studyroom');
 await expect(page.getByLabel('课程问题',{exact:true})).toBeVisible();
 const form=page.locator('.outline-flow');
 expect((await form.boundingBox())!.height).toBeLessThan(570);
 expect((await form.boundingBox())!.width).toBeLessThanOrEqual(920);
 await expect(page.getByRole('button',{name:'生成大纲',exact:true})).toBeInViewport();
 await expect(page.locator('input[name="depth"]')).toHaveCount(3);
 await page.screenshot({path:testInfo.outputPath('question-entry.png')});
 await page.setViewportSize({width:390,height:844});
 await expect(page.getByRole('button',{name:'生成大纲',exact:true})).toBeInViewport();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
 await page.screenshot({path:testInfo.outputPath('question-entry-mobile.png')});
});

for(const viewport of [{width:1366,height:768},{width:390,height:844},{width:844,height:390}]) {
 test(`bounded generation and objective-linked review at ${viewport.width}x${viewport.height}`,async({page},testInfo)=>{
  await page.setViewportSize(viewport);
  const units=Array.from({length:18},(_,i)=>({id:`u${i}`,title:`知识点 ${i+1}：栈的操作与应用`,objective:`目标 ${i+1}：理解当前操作对栈顶指针与存储空间的影响。`,materials:[{kind:'ANIMATION',availability:'AVAILABLE',purpose:'观察栈顶移动',candidateIds:[],requirement:'OPTIONAL'}]}));
  let generating=true;let polls=0;
  await page.route('**/api/v1/study-outlines/outline-presentation',route=>{
   polls++;
   return route.fulfill({json:{id:'outline-presentation',question:'栈',depth:'STANDARD',status:generating?'GENERATING':'REVIEWABLE',latestRevision:1,pendingJobId:generating?'job-1':null,planHash:'synthetic-hash',outline:{title:'栈：从概念到应用',units,objectives:['课程整体目标'],exclusions:[]},issues:[],changes:[],messages:[],revisions:[]}});
  });
  await page.route('**/api/v1/study-outlines/outline-presentation/events?*',route=>route.fulfill({contentType:'text/event-stream',body:`event: reset\ndata: ${JSON.stringify({streamId:'outline:outline-presentation',operationId:'job-1',attempt:1,runId:null,revision:1,cursor:8,floor:0,terminal:false,stateVersion:8,state:{phase:'PLANNING',units:units.slice(0,8)}})}\n\n`}));
  await page.goto('/studyroom/outlines/outline-presentation');
  const feedback=page.getByLabel('课堂生成进度');
  await expect(feedback).toBeVisible();
  await expect(feedback).toContainText('已编排 8 个知识点');
  await expect(page.locator('.visual-sheet')).toBeVisible();
  await expect(page.locator('.visual-sheet')).not.toHaveClass(/stage-change-leave-active/);
  await expect(page.locator('.generation-preview li')).toHaveCount(3);
  await expect(page.locator('.outline-unit')).toHaveCount(0);
  await expect(page.locator('.outline-actions')).toBeInViewport();
  await expect.poll(()=>polls).toBeGreaterThan(1);
  await expect(feedback).toContainText('已编排 8 个知识点');
  await page.screenshot({path:testInfo.outputPath('outline-generation.png')});
  generating=false;
  await expect(page.locator('.outline-unit')).toHaveCount(18);
  await expect(page.locator('.outline-unit').first()).toContainText('目标 1：');
  await expect(page.locator('[data-course-goals]')).not.toHaveAttribute('open');
  const action=page.getByRole('button',{name:'确认大纲，开始学习'});
  await expect(action).toBeEnabled();await expect(action).toBeInViewport();
  const overflow=await page.evaluate(()=>({height:document.documentElement.scrollHeight,viewport:innerHeight,width:document.documentElement.scrollWidth,viewportWidth:innerWidth}));
  expect(overflow.height).toBeLessThanOrEqual(overflow.viewport+1);
  expect(overflow.width).toBeLessThanOrEqual(overflow.viewportWidth+1);
  await page.locator('.outline-unit').last().scrollIntoViewIfNeeded();
  await expect(action).toBeInViewport();
  await page.getByRole('button',{name:'修改大纲',exact:true}).click();
  await expect(page.getByLabel('修改意见',{exact:true})).toBeVisible();
  await expect(action).toBeInViewport();
  await page.getByRole('button',{name:'收起修改',exact:true}).click();
  await page.locator('.outline-unit').first().scrollIntoViewIfNeeded();
  await page.screenshot({path:testInfo.outputPath('outline-review.png')});
  await page.emulateMedia({reducedMotion:'reduce'});
  expect(await page.locator('.outline-flow').evaluate(element=>getComputedStyle(element).overflow)).toBe('hidden');
 });
}
