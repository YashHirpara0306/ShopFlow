import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import type { ApiError } from "./types";

const REFRESH_KEY = "shopflow.refreshToken";

/** access token lives in memory only; refresh token in localStorage. */
let accessToken: string | null = null;

export const tokenStore = {
  get access() {
    return accessToken;
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string | null, refresh: string | null) {
    accessToken = access;
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
    else localStorage.removeItem(REFRESH_KEY);
  },
  clear() {
    accessToken = null;
    localStorage.removeItem(REFRESH_KEY);
  },
};

let onAuthLost: (() => void) | null = null;
export function setOnAuthLost(fn: () => void) {
  onAuthLost = fn;
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || "",
  headers: { "Content-Type": "application/json" },
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (accessToken) config.headers.set("Authorization", `Bearer ${accessToken}`);
  return config;
});

let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const rt = tokenStore.refresh;
  if (!rt) return null;
  try {
    const { data } = await axios.post(
      (import.meta.env.VITE_API_BASE || "") + "/api/auth/refresh",
      { refreshToken: rt },
      { headers: { "Content-Type": "application/json" } },
    );
    tokenStore.set(data.accessToken, data.refreshToken);
    return data.accessToken as string;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;
    const url = original?.url ?? "";
    const isAuthCall = url.includes("/api/auth/");

    if (error.response?.status === 401 && original && !original._retried && !isAuthCall && tokenStore.refresh) {
      original._retried = true;
      refreshing = refreshing ?? refreshAccessToken();
      const fresh = await refreshing;
      refreshing = null;
      if (fresh) {
        original.headers.set("Authorization", `Bearer ${fresh}`);
        return api(original);
      }
      onAuthLost?.();
    }
    return Promise.reject(error);
  },
);

/** Pull a friendly message out of the standard error body. */
export function errorMessage(err: unknown): string {
  const e = err as AxiosError<ApiError>;
  return e?.response?.data?.message || e?.message || "Something went wrong";
}

export function uuid(): string {
  if (crypto.randomUUID) return crypto.randomUUID();
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}
