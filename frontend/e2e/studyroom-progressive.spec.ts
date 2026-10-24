import {test,expect} from './fixtures/studyroom';
import {directory,resourcePage,knowledgePoint,notes} from './fixtures/classroom-navigation';
async function approve(page:import('@playwright/test').Page,question:string){
  await page.goto('/studyroom');
  await page.getByLabel('课程问题',{exact:true}).fill(question);
  await page.getByRole('button',{name:'生成大纲',exact:true}).click();
  await expect(page).toHaveURL(/\/studyroom\/outlines\//);
  await page.getByRole('button',{name:'审阅大纲',exact:true}).click();
  const confirm=page.getByRole('button',{name:'确认大纲，开始学习'});
  await expect(confirm).toBeEnabled();await confirm.click();
}
test('automatically enters the first fragment, keeps one route and preserves code drafts',async({page})=>{
  await approve(page,'栈的定义和C代码阅读，浏览回归');
  await expect(page).toHaveURL(/\/studyroom\/[a-f0-9-]+$/);
  await directory(page);await expect(page.getByRole('navigation',{name:'课程页面目录'})).toBeVisible();await directory(page,false);
  await expect(page.getByLabel('课堂学习步骤')).toHaveCount(0);
  await knowledgePoint(page,/阅读 C 实现/);
  await resourcePage(page,/栈的 C 代码实现/);await expect(page.getByLabel('C 代码讲解')).toBeVisible();
  await page.getByRole('button',{name:'打开代码实验',exact:true}).click();
  const editor=page.getByLabel('C 代码编辑器');await editor.fill('// retained harness draft');
  await knowledgePoint(page,/栈是什么/);
  await knowledgePoint(page,/阅读 C 实现/);
  await expect(editor).toHaveValue('// retained harness draft');
  await page.reload();await directory(page);await expect(page.locator('.point-heading').filter({hasText:'阅读 C 实现'})).toHaveAttribute('aria-current','step');
});
test('a failed later fragment is explicit and can be retried independently',async({page})=>{
  await approve(page,'栈的定义和C代码阅读，故障恢复');
  await expect(page).toHaveURL(/\/studyroom\/[a-f0-9-]+$/);
  await notes(page);await expect(page.locator('.notes-scroll:visible').filter({hasText:'最后进入的元素最先离开。'})).toBeVisible();

  await knowledgePoint(page,/阅读 C 实现/);
  await page.getByRole('button',{name:'重新准备这段内容',exact:true}).click();
  await resourcePage(page,/栈的 C 代码实现/);await expect(page.getByLabel('C 代码讲解')).toBeVisible();
  await knowledgePoint(page,/栈是什么/);
  await notes(page);await expect(page.locator('.notes-scroll:visible').filter({hasText:'最后进入的元素最先离开。'})).toBeVisible();
});
test('leaving the preparation page never forces navigation back',async({page})=>{
  await approve(page,'栈的定义和C代码阅读，等待保护');
  await page.getByRole('link',{name:'返回学习入口'}).click();
  await expect(page).toHaveURL('/begin');
  await page.waitForTimeout(4500);
  await expect(page).toHaveURL('/begin');
});
