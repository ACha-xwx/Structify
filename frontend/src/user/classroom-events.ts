import {api} from '../app/providers/runtime';
import type {ClassroomEvent,ClassroomStreamSnapshot} from '../shared/types/classroom-experience';

export class ClassroomEventCursor {
 sequence=0; needsReset=false;
 private snapshot:ClassroomStreamSnapshot|null=null;
 constructor(readonly streamId:string) {}
 reset(snapshot:ClassroomStreamSnapshot) {
  if(snapshot.streamId!==this.streamId)return false;
  this.snapshot={...snapshot};this.sequence=snapshot.cursor;this.needsReset=false;return true;
 }
 accept(event:ClassroomEvent) {
  const expected=this.snapshot;
  if(!expected||event.streamId!==this.streamId)return false;
  if(event.operationId!==expected.operationId||event.attempt!==expected.attempt||event.runId!==expected.runId||event.revision!==expected.revision){this.needsReset=true;return false;}
  if(event.sequence<=this.sequence||event.stateVersion<=expected.stateVersion)return false;
  if(event.sequence!==this.sequence+1){this.needsReset=true;return false;}
  this.sequence=event.sequence;expected.stateVersion=event.stateVersion;return true;
 }
}

export async function* classroomEvents(path:string,since:number,signal:AbortSignal) {
 const events=await api.stream<ClassroomEvent|ClassroomStreamSnapshot>(path,{query:{since},signal});
 for await(const event of events) {
  const data=event.parsed??JSON.parse(event.data) as ClassroomEvent|ClassroomStreamSnapshot;
  yield {kind:event.event,data};
 }
}
