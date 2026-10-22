import type {MaterialPosition,TeachingAction} from '../shared/types/classroom-experience';
export interface ClassroomMaterialAdapter {
 capture:()=>MaterialPosition;
 pause:()=>void;
 restore:(position:MaterialPosition)=>void|Promise<void>;
 supports:(action:TeachingAction)=>boolean;
 apply:(action:TeachingAction)=>boolean|Promise<boolean>;
}
export function sameMaterial(left:object,right:object) {
 return JSON.stringify(left)===JSON.stringify(right);
}
