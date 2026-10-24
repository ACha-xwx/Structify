import {test,expect} from './fixtures/studyroom';

test('a rejected unrelated topic can return to a fresh question and the learning entry',async({page})=>{
 await page.route('**/api/v1/study-outlines/failed-topic',route=>route.fulfill({json:{id:'failed-topic',question:'无关问题',depth:'STANDARD',status:'FAILED',latestRevision:0,pendingJobId:null,outline:null,issues:[],changes:[],generation:{status:'FAILED',failure_message:'没有可用的授权资料，暂时无法规划学习路线'}}}));
 await page.goto('/studyroom/outlines/failed-topic');
 await expect(page.getByRole('link',{name:'更换学习问题'})).toBeVisible();
 await page.getByRole('link',{name:'更换学习问题'}).click();
 await expect(page).toHaveURL(/\/studyroom$/);
 await expect(page.getByLabel('课程问题')).toBeEditable();
 await page.getByRole('link',{name:'返回学习入口'}).click();
 await expect(page).toHaveURL(/\/begin$/);
});
