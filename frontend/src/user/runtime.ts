import { api, auth } from "../app/providers/runtime";
import { createCoursewareCatalog } from "../shared/courseware/courseware-catalog";
import { createUserApi } from "./api";

export const userApi = createUserApi(api);
export const coursewareCatalog = createCoursewareCatalog(userApi, () => auth.state.user);
