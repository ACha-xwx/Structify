import { api, issueNodeCompatibilityToken, legacyApi } from "../app/providers/runtime";
import { createUserApi } from "./api";
import { createPresentationApi } from "./adapters/presentation";

export const userApi = createUserApi(api);
export const presentationApi = createPresentationApi(legacyApi, { issueNodeCompatibilityToken });
