import { api } from '../app/providers/runtime';
export interface PersonalCourse {
  id:string;title:string;status:'DRAFT'|'PREPARING'|'LEARNING'|'COMPLETED'|'NEEDS_ATTENTION';
  roomId:string|null;runId:string|null;totalPoints:number;completedPoints:number;readableFragments:number;
  lastPointTitle:string|null;lastLearningAt:string|null;activityAt:string;
}
export function sortCourses(courses:PersonalCourse[]) {
  return [...courses].sort((a,b)=>Date.parse(b.activityAt)-Date.parse(a.activityAt)||a.id.localeCompare(b.id));
}
export function resumeCourse(courses:PersonalCourse[]):PersonalCourse|null {
  const learned=courses.filter(c=>c.lastLearningAt).sort((a,b)=>Date.parse(b.lastLearningAt!)-Date.parse(a.lastLearningAt!)||a.id.localeCompare(b.id));
  return learned[0]??sortCourses(courses)[0]??null;
}
export function courseDestination(course:PersonalCourse) {
  return course.roomId?`/studyroom/${encodeURIComponent(course.roomId)}`:`/studyroom/outlines/${encodeURIComponent(course.id)}`;
}
export const courseLibrary={
  async list():Promise<PersonalCourse[]> {const result=await api.request<PersonalCourse[]>('/users/courses');if(result.kind!=='json')throw new Error('Invalid course response');return result.data;},
  async rename(id:string,title:string){await api.request(`/users/courses/${encodeURIComponent(id)}`,{method:'PATCH',body:{title}});},
  async remove(id:string){await api.request(`/users/courses/${encodeURIComponent(id)}`,{method:'DELETE'});},
};
