import {expect,type Page} from '@playwright/test';
export async function directory(page:Page,open=true){if(!open){const close=page.getByRole('button',{name:'收起课程目录',exact:true});if(await close.isVisible())await close.click();return;}const toggle=page.getByRole('button',{name:'课程目录',exact:true});if(await toggle.getAttribute('aria-expanded')!=='true')await toggle.click();}
async function closeNotes(page:Page){const close=page.getByRole('button',{name:'收起完整讲述',exact:true});if(await close.isVisible())await close.click();}
export async function knowledgePoint(page:Page,title:RegExp){await closeNotes(page);await directory(page);await page.locator('.point-heading').filter({hasText:title}).click();await directory(page,false);}
export async function resourcePage(page:Page,title:RegExp){await closeNotes(page);await directory(page);await page.locator('.page-thumbnail').filter({hasText:title}).first().click();await directory(page,false);}
export async function notes(page:Page){const button=page.getByRole('button',{name:'完整讲述',exact:true});if(await button.getAttribute('aria-expanded')!=='true')await button.click();await expect(page.getByLabel('完整讲解正文')).toBeVisible();}
