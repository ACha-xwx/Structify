import {expect,it} from 'vitest';
import {containedImageBox,regionFromDrag} from './slide-regions';
it('maps regions to the painted image excluding object-fit letterboxing',()=>{
 const box=containedImageBox(1000,500,400,400)!;expect(box).toEqual({left:250,top:0,width:500,height:500});
 expect(regionFromDrag(box,{x:300,y:50},{x:550,y:250})).toEqual({x:.1,y:.1,width:.5,height:.4});
 expect(containedImageBox(0,500,400,400)).toBeNull();
 expect(regionFromDrag(box,{x:0,y:0},{x:249,y:200})).toBeNull();
});
