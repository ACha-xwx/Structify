export interface ImageBox {left:number;top:number;width:number;height:number}
export function containedImageBox(width:number,height:number,naturalWidth:number,naturalHeight:number):ImageBox|null {
 if([width,height,naturalWidth,naturalHeight].some(value=>!Number.isFinite(value)||value<=0))return null;
 const scale=Math.min(width/naturalWidth,height/naturalHeight);const w=naturalWidth*scale,h=naturalHeight*scale;
 return {left:(width-w)/2,top:(height-h)/2,width:w,height:h};
}
export function regionFromDrag(box:ImageBox,start:{x:number;y:number},end:{x:number;y:number}) {
 const clamp=(value:number)=>Math.max(0,Math.min(1,value));
 const x1=clamp((start.x-box.left)/box.width),y1=clamp((start.y-box.top)/box.height),x2=clamp((end.x-box.left)/box.width),y2=clamp((end.y-box.top)/box.height);
 const region={x:Math.min(x1,x2),y:Math.min(y1,y2),width:Math.abs(x2-x1),height:Math.abs(y2-y1)};
 return region.width>=.01&&region.height>=.01?region:null;
}
