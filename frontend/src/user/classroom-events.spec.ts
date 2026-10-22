import {expect,it} from 'vitest';
import {ClassroomEventCursor} from './classroom-events';
import type {ClassroomEvent,ClassroomStreamSnapshot} from '../shared/types/classroom-experience';
const snapshot:ClassroomStreamSnapshot={streamId:'outline:d',operationId:'op',attempt:2,runId:null,revision:1,cursor:8,floor:3,terminal:false,stateVersion:9,state:{units:[]}};
const event=(extra:Partial<ClassroomEvent>={}):ClassroomEvent=>({...snapshot,sequence:9,stateVersion:10,kind:'phase',payload:{phase:'PLANNING'},...extra});
it('accepts a server snapshot and rejects duplicate, stale attempt and foreign stream events',()=>{
 const cursor=new ClassroomEventCursor('outline:d');
 expect(cursor.accept(event())).toBe(false);
 expect(cursor.reset(snapshot)).toBe(true);
 expect(cursor.accept(event({attempt:1}))).toBe(false);
 expect(cursor.accept(event({streamId:'outline:other'}))).toBe(false);
 expect(cursor.accept(event())).toBe(true);
 expect(cursor.accept(event())).toBe(false);
 expect(cursor.sequence).toBe(9);
});
it('requires a refreshed snapshot for an unknown attempt or a gap',()=>{
 const cursor=new ClassroomEventCursor('outline:d');cursor.reset(snapshot);
 expect(cursor.accept(event({attempt:3}))).toBe(false);expect(cursor.needsReset).toBe(true);
 cursor.reset(snapshot);expect(cursor.accept(event({sequence:12}))).toBe(false);expect(cursor.needsReset).toBe(true);
});
