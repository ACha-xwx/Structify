import {test,expect} from './fixtures/studyroom';
test('restores the paper review and original classroom material interface',async({page},testInfo)=>{
 await page.goto('/studyroom');
 await page.getByLabel('课程问题',{exact:true}).fill('栈的定义和C代码阅读，视觉回退检查');
 await page.getByRole('button',{name:'生成大纲',exact:true}).click();
 await expect(page.locator('.visual-sheet')).toBeVisible();
 await expect(page.getByRole('button',{name:'审阅大纲',exact:true})).toBeVisible();
 await page.screenshot({path:testInfo.outputPath('restored-paper.png')});
 await page.getByRole('button',{name:'审阅大纲',exact:true}).click();
 await expect(page.getByLabel('知识点标题',{exact:true}).first()).toBeVisible();
 await expect(page.locator('.unit-content label')).toHaveCount(0);
 await page.screenshot({path:testInfo.outputPath('restored-outline.png')});
 await page.getByRole('button',{name:'确认大纲，开始学习',exact:true}).click();
 await expect(page).toHaveURL(/\/studyroom\/[a-f0-9-]+$/);
 await expect(page.getByRole('button',{name:'完整讲述',exact:true})).toBeVisible();
 await expect(page.locator('.text-slides')).toHaveCount(0);
 await page.screenshot({path:testInfo.outputPath('restored-classroom.png')});
});
