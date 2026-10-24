import {test,expect} from './fixtures/studyroom';

test('preparation reflects server phases, preserves failure and enters only after readable publication',async({page},testInfo)=>{
 let phase='PREPARING_RESOURCES';let readable=false;let failed=false;let cursor=0;
 await page.route('**/api/v1/study-outlines/preparation-ui',route=>route.fulfill({json:{id:'preparation-ui',question:'栈',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,planHash:'hash',outline:{title:'栈的课程准备',units:[{id:'u1',title:'栈',objective:'理解后进先出',materials:[]}],objectives:[],exclusions:[]},issues:[],changes:[],revisions:[]}}));
 await page.route('**/api/v1/study-outlines/preparation-ui/confirm',route=>route.fulfill({json:{roomId:'room-preparation-ui',runId:'run-preparation-ui'}}));
 await page.route('**/api/v1/studyroom-runs/run-preparation-ui',route=>route.fulfill({json:{stateVersion:++cursor,firstReadableFragmentId:readable?'fragment-ready':null,knowledgePoints:[{fragments:[{status:failed?'FAILED':'GENERATING',error:failed?{message:'合成测试：首段校验失败'}:null}]}]}}));
 await page.route('**/api/v1/studyroom-runs/run-preparation-ui/events?*',route=>route.fulfill({contentType:'text/event-stream',body:`event: reset\ndata: ${JSON.stringify({streamId:'run:run-preparation-ui',operationId:'prep-job',attempt:1,runId:'run-preparation-ui',revision:1,cursor:++cursor,floor:0,terminal:false,stateVersion:cursor,state:{phase}})}\n\n`}));
 await page.goto('/studyroom/outlines/preparation-ui');
 await page.getByRole('button',{name:'审阅大纲',exact:true}).click();
 await page.getByRole('button',{name:'确认大纲，开始学习'}).click();
 const progress=page.getByLabel('课堂准备进度');await expect(progress).toBeVisible();
 await expect(progress.getByRole('status')).toHaveText('整理课程材料');
 phase='GENERATING_CONTENT';await expect(progress.getByRole('status')).toHaveText('编写课堂讲解');
 await expect(progress.getByRole('listitem').filter({hasText:'编写课堂讲解'})).toHaveAttribute('aria-current','step');
 await expect(progress.getByRole('listitem').filter({hasText:'整理课程材料'})).toHaveClass(/done/);
 // Wait for the surrounding review panel to leave before capturing the preparation state.
 await expect(page.locator('.review-panel-leave-active,.review-card-leave-active')).toHaveCount(0);
 await page.screenshot({path:testInfo.outputPath('classroom-content-generation.png')});
 await page.setViewportSize({width:390,height:844});
 await expect(progress).toBeInViewport();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:testInfo.outputPath('classroom-generation-mobile.png')});
 await page.setViewportSize({width:1280,height:720});
 phase='PUBLISHING';await expect(progress.getByRole('status')).toHaveText('校验并发布');
 failed=true;await expect(progress).toContainText('合成测试：首段校验失败');
 await expect(page).toHaveURL(/\/studyroom\/outlines\/preparation-ui$/);
 await page.screenshot({path:testInfo.outputPath('classroom-generation-failed.png')});
 failed=false;readable=true;
 await page.getByRole('button',{name:'重新检查准备状态',exact:true}).click();
 await expect(progress.getByRole('status')).toHaveText('课堂已准备好');
 await expect(page).toHaveURL(/\/studyroom\/room-preparation-ui$/);
});

for(const viewport of [{width:1280,height:900},{width:390,height:844},{width:1280,height:560}]){
 test(`review and preparation retain the same frame at ${viewport.width}x${viewport.height}`,async({page})=>{
  await page.setViewportSize(viewport);
  await page.emulateMedia({reducedMotion:'reduce'});
  await page.route('**/api/v1/study-outlines/frame-check',route=>route.fulfill({json:{id:'frame-check',depth:'STANDARD',status:'REVIEWABLE',latestRevision:1,pendingJobId:null,planHash:'hash',outline:{title:'Frame check',units:[{id:'u1',title:'Stack',objective:'Understand LIFO',materials:[]}],objectives:[],exclusions:[]},issues:[],changes:[]}}));
  await page.route('**/api/v1/study-outlines/frame-check/confirm',route=>route.fulfill({json:{roomId:'frame-room',runId:'frame-run'}}));
  await page.route('**/api/v1/studyroom-runs/frame-run',route=>route.fulfill({json:{stateVersion:1,firstReadableFragmentId:null,knowledgePoints:[{fragments:[{status:'GENERATING'}]}]}}));
  await page.route('**/api/v1/studyroom-runs/frame-run/events?*',route=>route.fulfill({contentType:'text/event-stream',body:''}));
  await page.goto('/studyroom/outlines/frame-check');
  await expect(page.locator('.review-trigger')).toBeVisible();
  const before=await page.locator('.outline-flow').boundingBox();
  await page.locator('.review-trigger').click();
  await page.locator('[data-action="confirm"]').click();
  await expect(page.locator('.preparation-card')).toBeVisible();
  await expect(page.locator('.review-card-leave-active,.review-panel-leave-active')).toHaveCount(0);
  const after=await page.locator('.outline-flow').boundingBox();
  expect(before).not.toBeNull();expect(after).not.toBeNull();
  expect(Math.abs(before!.width-after!.width)).toBeLessThanOrEqual(1);
  expect(Math.abs(before!.height-after!.height)).toBeLessThanOrEqual(1);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 });
}
