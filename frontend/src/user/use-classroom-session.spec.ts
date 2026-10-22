import {expect,it,vi} from 'vitest';
import {useClassroomSession} from './use-classroom-session';
import type {MaterialIdentity} from '../shared/types/classroom-experience';
it('pauses on question and restores only the same material and navigation position',async()=>{
 const identity:MaterialIdentity={roomId:'r',runId:'run',unitId:'u',fragmentId:'f',contentRevision:1,sceneId:'s',resourceId:'code'};
 const position={kind:'C_CODE' as const,code:'edited code',stdin:'1',editing:true,scrollTop:90};
 const material={capture:()=>position,pause:vi.fn(),restore:vi.fn(),supports:()=>false,apply:()=>false};
 const session=useClassroomSession(()=>identity,()=>material);
 session.ask();expect(material.pause).toHaveBeenCalledOnce();session.answering();session.answered();
 expect(await session.resume()).toBe(true);expect(material.restore).toHaveBeenCalledWith(position);
 session.ask();session.navigated();expect(await session.resume()).toBe(false);expect(material.restore).toHaveBeenCalledTimes(1);
 session.ask();identity.contentRevision=2;expect(await session.resume()).toBe(false);
});
it('restores a slide after a verified teaching navigation while still refusing later manual navigation',async()=>{
 const identity:MaterialIdentity={roomId:'r',runId:'run',unitId:'u',fragmentId:'f',contentRevision:1,sceneId:'slides',resourceId:'page-1'};
 const restore=vi.fn();const adapter={capture:()=>({kind:'PPT' as const,pageId:'page-1',zoom:2,scrollLeft:30,scrollTop:40}),pause:()=>{},restore,supports:()=>true,apply:()=>true};
 const session=useClassroomSession(()=>identity,()=>adapter);session.ask();
 identity.resourceId='page-2';session.allowTeachingNavigation();expect(await session.resume()).toBe(true);
 expect(restore).toHaveBeenCalledWith(expect.objectContaining({pageId:'page-1',zoom:2}));
 session.ask();identity.resourceId='page-3';session.allowTeachingNavigation();identity.resourceId='page-4';expect(await session.resume()).toBe(false);
});
