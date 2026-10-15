// Shared API client for SCRUM-29 screens.
// Token: Authorization Bearer (access token). Refresh token travels via HttpOnly cookie.

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T;
  errorCode?: string;
  errors?: Record<string, string>;
  timestamp?: string;
  path?: string;
}

export class ApiError extends Error {
  status: number;
  errorCode?: string;
  constructor(status: number, message: string, errorCode?: string) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }
}

export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem("ezticket_access_token");
}

export function getRole(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem("ezticket_role");
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = getToken();
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init?.headers ?? {}),
    },
    credentials: "include",
  });
  if (res.status === 204) return undefined as T;
  const body = (await res.json().catch(() => null)) as ApiEnvelope<T> | null;
  if (!res.ok) {
    throw new ApiError(
      res.status,
      body?.message ?? `Yêu cầu thất bại (${res.status})`,
      body?.errorCode,
    );
  }
  return (body?.data ?? body) as T;
}

export const api = {
  get: <T,>(path: string) => request<T>(path, { method: "GET" }),
  post: <T,>(path: string, payload?: unknown) =>
    request<T>(path, { method: "POST", body: JSON.stringify(payload ?? {}) }),
};

export async function downloadTicketPdf(code: string): Promise<void> {
  const token = getToken();
  const res = await fetch(`${API_BASE}/tickets/${encodeURIComponent(code)}/pdf`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    credentials: "include",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as ApiEnvelope<unknown> | null;
    throw new ApiError(res.status, body?.message ?? "Tải vé thất bại", body?.errorCode);
  }
  const blob = await res.blob();
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = `${code}.pdf`;
  a.click();
  window.URL.revokeObjectURL(url);
}

// ---- Ticket types (camelCase JSON per Context Pack 3.5) ----
export interface TicketItem {
  code: string;
  eventTitle: string;
  eventTime: string;
  venue: string;
  ticketType: string;
  status: string;
}

export interface TicketDetail extends TicketItem {
  holderName: string;
  buyerEmail: string;
  qrContent: string;
  qrSignature: string;
  checkedInAt: string | null;
}

export interface VerifyResult {
  valid: boolean;
  code: string;
  buyer: string;
  ticketType: string;
  status: string;
  message: string;
}

export interface CheckInResult {
  code: string;
  status: string;
  checkInAt: string;
  gate: string | null;
}
