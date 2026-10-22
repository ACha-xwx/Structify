import { describe, expect, it } from 'vitest';
import { courseDestination, resumeCourse, sortCourses, type PersonalCourse } from './course-library';
const course = (over: Partial<PersonalCourse> = {}): PersonalCourse => ({id:'draft',title:'栈',status:'DRAFT',roomId:null,runId:null,totalPoints:0,completedPoints:0,readableFragments:0,lastPointTitle:null,lastLearningAt:null,activityAt:'2026-10-20T08:00:00Z',...over});
describe('personal course navigation',()=>{
 it('prioritizes actual learning over a newer draft and uses the server room cursor',()=>{
  const learning=course({id:'learning',roomId:'room',status:'LEARNING',lastLearningAt:'2026-10-19T08:00:00Z'});
  expect(resumeCourse([course(),learning])?.id).toBe('learning');
  expect(courseDestination(learning)).toBe('/studyroom/room');
  expect(courseDestination(course())).toBe('/studyroom/outlines/draft');
 });
 it('falls back to the latest task, with deterministic ties',()=>{
  expect(sortCourses([course({id:'b'}),course({id:'a'})]).map(c=>c.id)).toEqual(['a','b']);
  expect(resumeCourse([])).toBeNull();
  expect(resumeCourse([course({id:'old',activityAt:'2026-10-18T00:00:00Z'}),course()])?.id).toBe('draft');
 });
});
