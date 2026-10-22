import {it,expect,vi,afterEach} from 'vitest';
import {LecturePlayer} from './lecture-player';
afterEach(()=>vi.useRealTimers());
const cues=[{id:'1',text:'第一句',kind:'TEXT' as const},{id:'2',text:'第二句',kind:'TEXT' as const},{id:'3',text:'开始自检',kind:'CHECK' as const}];
it('waits for actual speech completion and ignores stale callbacks after pause',async()=>{
 let end=()=>{};const cancel=vi.fn();const apply=vi.fn(async()=>true);
 const player=new LecturePlayer(cues,apply,{start:(_t,_r,done)=>{end=done;return cancel;}},()=>{});
 await player.play();expect(apply).toHaveBeenCalledTimes(1);player.pause();end();expect(player.index).toBe(0);expect(cancel).toHaveBeenCalled();
 await player.play();end();await Promise.resolve();await Promise.resolve();expect(player.index).toBe(1);player.dispose();
});
it('pauses at checks and invalidates in-flight material selection',async()=>{
 let release=()=>{};const player=new LecturePlayer(cues,cue=>cue.kind==='CHECK'?Promise.resolve(true):new Promise<boolean>(resolve=>{release=()=>resolve(true);}),undefined,()=>{});
 const pending=player.play();player.pause();release();await pending;expect(player.state).toBe('paused');
 await player.seek(2);expect(player.state).toBe('waiting-check');player.dispose();
});
it('falls back to bounded silent captions when voice fails and cancels their timer',async()=>{
 vi.useFakeTimers();const player=new LecturePlayer(cues,async()=>true,{start:(_t,_r,_done,error)=>{error();return ()=>{};}},()=>{});
 await player.play();expect(player.notice).toContain('静音');player.pause();await vi.runAllTimersAsync();expect(player.index).toBe(0);player.dispose();
});
