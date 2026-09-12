import axios from "axios";
import { clearAuthSession, readAuthSession } from "../auth/authStorage";
import {
  shouldAttachAuthentication,
  shouldInvalidateAuthentication,
} from "../auth/authPolicy";

const api = axios.create({
  baseURL: "",
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use(
  (config) => {
    const user = readAuthSession();
    if (
      user &&
      shouldAttachAuthentication(
        config.url,
        config.baseURL,
        window.location.origin,
      )
    ) {
      config.headers.Authorization = `Bearer ${user.token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (shouldInvalidateAuthentication(
      error.response?.status,
      Boolean(error.config?.headers?.Authorization),
    )) {
      clearAuthSession();
    }
    return Promise.reject(error);
  }
);

export default api;
