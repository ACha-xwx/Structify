import { api } from "../app/providers/runtime";
import type { ApiResponse } from "../shared/api";
import type { LearningMap } from "../shared/types/contracts";

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
};
