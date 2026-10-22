import type {LectureCue} from './lecture-types';
export interface LectureVoice {start:(text:string,rate:number,end:()=>void,error:()=>void)=>()=>void}
export class LecturePlayer {
 index=0;state:'idle'|'playing'|'paused'|'waiting-check'|'ended'='idle';muted=false;rate=1;notice='';
 private generation=0;private stop:(()=>void)|undefined;private timer:ReturnType<typeof setTimeout>|undefined;
 private controller=new AbortController();
 constructor(readonly cues:LectureCue[],private apply:(cue:LectureCue,signal:AbortSignal)=>Promise<boolean>,private voice:LectureVoice|undefined,private changed:()=>void){}
 private clear(){this.generation++;this.controller.abort();this.controller=new AbortController();this.stop?.();this.stop=undefined;clearTimeout(this.timer);}
 pause(){this.clear();if(this.state==='playing')this.state='paused';this.changed();}
 async play(){if(!this.cues.length)return;if(this.state==='ended'||this.state==='waiting-check')this.index=0;this.clear();this.state='playing';await this.run(this.generation);}
 async seek(index:number){this.pause();if(!this.cues.length)return;this.index=Math.max(0,Math.min(this.cues.length-1,index));this.state=this.cues[this.index]?.kind==='CHECK'?'waiting-check':'paused';this.changed();await this.apply(this.cues[this.index]!,this.controller.signal);}
 configure(muted:boolean,rate:number){const playing=this.state==='playing';this.pause();this.muted=muted;this.rate=rate;this.changed();if(playing)void this.play();}
 private async run(generation:number){
  const cue=this.cues[this.index];if(!cue||generation!==this.generation)return;
  this.changed();let applied=false;
  try{applied=await this.apply(cue,this.controller.signal);}catch{applied=false;}
  if(generation!==this.generation)return;
  if(!applied){this.notice='当前材料定位暂不可用，仍可阅读讲解字幕。';}
  if(cue.kind==='CHECK'){this.state='waiting-check';this.changed();return;}
  const done=()=>{if(generation!==this.generation||this.state!=='playing')return;this.clear();if(this.index+1>=this.cues.length){this.state='ended';this.changed();return;}this.index++;void this.run(this.generation);};
  const silent=()=>{if(generation!==this.generation)return;this.timer=setTimeout(done,Math.max(1200,Math.min(18000,cue.text.length*160))/this.rate);};
  if(this.muted)silent();else if(!this.voice){this.muted=true;this.notice='当前设备没有可用朗读，已使用静音字幕播放。';silent();}
  else {let failed=false;this.stop=this.voice.start(cue.text,this.rate,()=>{if(!failed)done();},()=>{if(failed||generation!==this.generation)return;failed=true;this.muted=true;this.notice='语音暂不可用，已切换静音字幕播放。';this.changed();silent();});}
  this.changed();
 }
 dispose(){this.clear();this.state='paused';}
}
export function browserLectureVoice():LectureVoice|undefined {
 if(typeof speechSynthesis==='undefined'||typeof SpeechSynthesisUtterance==='undefined')return undefined;
 return {start(text,rate,end,error){
  let alive=true;const utterance=new SpeechSynthesisUtterance(text);utterance.lang='zh-CN';utterance.rate=rate;
  const voices=speechSynthesis.getVoices();const chinese=voices.find(voice=>/^zh/i.test(voice.lang));if(chinese)utterance.voice=chinese;
  if(voices.length&&!chinese){queueMicrotask(()=>{if(alive)error();});return ()=>{alive=false;};}
  const watchdog=setTimeout(()=>{if(alive){speechSynthesis.cancel();error();}},4000);
  utterance.onstart=()=>clearTimeout(watchdog);
  utterance.onend=()=>{clearTimeout(watchdog);if(alive)end();};utterance.onerror=()=>{clearTimeout(watchdog);if(alive)error();};
  try{speechSynthesis.speak(utterance);}catch{error();}
  return ()=>{alive=false;clearTimeout(watchdog);utterance.onend=null;utterance.onerror=null;speechSynthesis.cancel();};
 }};
}
