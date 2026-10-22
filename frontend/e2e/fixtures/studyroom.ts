import { test as base,expect } from '@playwright/test';
import type {APIRequestContext} from '@playwright/test';
type HarnessSession=Awaited<ReturnType<APIRequestContext['storageState']>>;
export const test=base.extend<{}, {harnessSession:HarnessSession}>({
 harnessSession:[async({playwright},use)=>{
  const client=await playwright.request.newContext({baseURL:'http://127.0.0.1:5183'});
  const login=await client.post('/api/v1/auth/login',{data:{email:'harness@structify.test',password:'HarnessOnly123!'}});
  expect(login.ok()).toBeTruthy();await use(await client.storageState());await client.dispose();
 },{scope:'worker'}],
 storageState:async({harnessSession},use)=>{await use(harnessSession);},
 page:async({page},use)=>{
  await page.addInitScript(()=>localStorage.setItem('structify.session.hint','1'));
  await use(page);
}});
export {expect};
