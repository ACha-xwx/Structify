import {test,expect} from './fixtures/studyroom';

test('explains a library sample using the published chapter ID rather than the menu number',async({page})=>{
 await page.setViewportSize({width:1366,height:900});
 await page.route('**/api/v1/code/samples*',route=>route.fulfill({json:{sampleCount:1,lessons:[{coursewareKey:'02-03',lessonTitle:'单链表基本运算',chapterId:'02-linear-list',samples:[{id:'02-03-test',title:'单链表：头插建表',sourceFile:'ch02/code/linklist.h',sections:[],targets:[],summary:'',stdin:'',expectedStdout:'',code:'int main(void) { return 0; }'}]}]}}));
 await page.route('**/api/v1/code/library',route=>route.fulfill({json:{fragmentCount:0,chapters:[]}}));
 let chapterId:unknown;
 await page.route('**/api/v1/code/analyze',route=>{
  chapterId=route.request().postDataJSON().chapterId;
  return chapterId==='02-linear-list'?route.fulfill({json:{analysis:'这段代码是单链表演示的入口。'}}):route.fulfill({status:400,json:{code:'COMPILER_CHAPTER_INVALID',message:'章节参数无效',requestId:'test',details:[]}});
 });
 await page.goto('/compiler');
 const samples=page.locator('[data-library-group="samples"]');
 await samples.locator('[data-chapter="02"]').click();
 await page.getByRole('menuitem',{name:/^单链表/}).click();
 await page.locator('[data-entry-id="02-03-test"]').click();
 await page.getByRole('button',{name:'解释这段代码',exact:true}).click();
 await expect(page.getByLabel('代码解释',{exact:true})).toContainText('这段代码是单链表演示的入口。');
 expect(chapterId).toBe('02-linear-list');
 await page.screenshot({path:'test-results/compiler-chapter-fixed.png',fullPage:true});
});
