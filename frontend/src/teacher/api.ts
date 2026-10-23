import { api } from "../app/providers/runtime";
import { staffErrorMessage, type ApiResponse } from "../shared/api";
import type { CourseLibraryEntry, LearningMap } from "../shared/types/contracts";

function jsonData<T>(response: ApiResponse<T>): T {
  if (response.kind !== "json") throw new Error("TEACHER_INVALID_RESPONSE");
  return response.data;
}

/** A class the signed-in teacher teaches. */
export interface TeacherClass {
  id: string;
  name: string;
  term: string | null;
  students: number;
}

/** One student of a class, with the cheap aggregates a whole-cohort table can afford. */
export interface TeacherStudent {
  id: number;
  username: string | null;
  email: string;
  status: string;
  lastActivityAt: string | null;
  classroomSteps: number;
}

/**
 * One student's learning footprint as the teacher sees it: courses with progress, classroom work, bookmarks and
 * the mastery map. Spend and generation jobs are deliberately absent - they belong to the operations console.
 */
export interface TeacherStudentFootprint {
  studentId: number;
  username: string | null;
  email: string;
  courses: CourseLibraryEntry[];
  bookmarks: number;
  classroomSessions: number;
  classroomEvents: number;
  lastActivityAt: string | null;
  learningMap: LearningMap;
}

/** Teacher-facing failure text: action, server code and request id, same rule as the admin console. */
export function teacherErrorMessage(error: unknown, action: string): string {
  return staffErrorMessage(error, action);
}

export const teacherApi = {
  async classes(): Promise<TeacherClass[]> {
    return jsonData(await api.get<TeacherClass[]>("/teacher/classes"));
  },
  async students(classId: string): Promise<TeacherStudent[]> {
    return jsonData(await api.get<TeacherStudent[]>(`/teacher/classes/${encodeURIComponent(classId)}/students`));
  },
  async studentMap(studentId: number): Promise<LearningMap> {
    return jsonData(await api.get<LearningMap>(`/teacher/students/${encodeURIComponent(studentId)}/learning-map`));
  },
  /** One audited read for everything the page shows about a student. */
  async studentFootprint(studentId: number): Promise<TeacherStudentFootprint> {
    return jsonData(await api.get<TeacherStudentFootprint>(`/teacher/students/${encodeURIComponent(studentId)}/footprint`));
  },
};
