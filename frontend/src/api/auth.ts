import { api, tokenStore } from "./client";
import type { TokenResponse, User } from "./types";

export async function register(email: string, password: string, displayName: string): Promise<User> {
  const { data } = await api.post<User>("/api/auth/register", { email, password, displayName });
  return data;
}

export async function login(email: string, password: string): Promise<TokenResponse> {
  const { data } = await api.post<TokenResponse>("/api/auth/login", { email, password });
  tokenStore.set(data.accessToken, data.refreshToken);
  return data;
}

export async function logout(): Promise<void> {
  const rt = tokenStore.refresh;
  tokenStore.clear();
  if (rt) {
    try {
      await api.post("/api/auth/logout", { refreshToken: rt });
    } catch {
      /* best effort */
    }
  }
}

export async function refresh(): Promise<TokenResponse | null> {
  const rt = tokenStore.refresh;
  if (!rt) return null;
  try {
    const { data } = await api.post<TokenResponse>("/api/auth/refresh", { refreshToken: rt });
    tokenStore.set(data.accessToken, data.refreshToken);
    return data;
  } catch {
    tokenStore.clear();
    return null;
  }
}

export async function me(): Promise<User> {
  const { data } = await api.get<User>("/api/users/me");
  return data;
}

/** Decode the roles/sub from a JWT without verifying (display only). */
export function decodeJwt(token: string): { sub?: string; roles?: string[]; exp?: number } {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json);
  } catch {
    return {};
  }
}
