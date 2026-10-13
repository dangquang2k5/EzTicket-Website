"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Html5QrcodeScanner } from "html5-qrcode";
import { ApiError, api, type CheckInResult, type VerifyResult } from "@/lib/api";

type Banner =
  | { kind: "idle" }
  | { kind: "ok"; text: string }
  | { kind: "error"; text: string };

const SCAN_REGION_ID = "ezticket-scan-region";
// Debounce: ignore repeat scans of the same content for 3s + while a request is in flight.
const RESCAN_COOLDOWN_MS = 3000;

/**
 * [GĐ2] Web Admin QR scanner (C-06: Web Admin only, no separate native app).
 * Flow: scan -> POST /tickets/verify -> (if valid) POST /tickets/{code}/check-in.
 * Green "HỢP LỆ" banner ONLY on check-in 200. Red banners for 409/400.
 */
export default function AdminScanPage() {
  const [banner, setBanner] = useState<Banner>({ kind: "idle" });
  const [manualCode, setManualCode] = useState("");
  const [gate, setGate] = useState("");
  const [busy, setBusy] = useState(false);
  const busyRef = useRef(false);
  const lastScanRef = useRef<{ content: string; at: number }>({ content: "", at: 0 });

  const handleContent = useCallback(async (qrContent: string) => {
    const now = Date.now();
    if (busyRef.current) return; // debounce: request in flight
    if (
      lastScanRef.current.content === qrContent &&
      now - lastScanRef.current.at < RESCAN_COOLDOWN_MS
    ) {
      return; // debounce: same QR repeated
    }
    lastScanRef.current = { content: qrContent, at: now };
    busyRef.current = true;
    setBusy(true);
    setBanner({ kind: "idle" });
    try {
      const verify = await api.post<VerifyResult>("/tickets/verify", {
        qrContent,
        // eventId omitted: scanner accepts tickets for any event of this organizer.
      });
      const checkIn = await api.post<CheckInResult>(
        `/tickets/${encodeURIComponent(verify.code)}/check-in`,
        { gate: gate || undefined },
      );
      setBanner({
        kind: "ok",
        text: `HỢP LỆ — ${checkIn.code} · ${verify.buyer} · ${verify.ticketType}`,
      });
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.status === 409 && e.errorCode === "ALREADY_USED") {
          // Backend message already has the required form:
          // "CẢNH BÁO: VÉ ĐÃ SỬ DỤNG LÚC [HH:mm:ss]"
          setBanner({ kind: "error", text: e.message });
        } else if (e.status === 409) {
          setBanner({ kind: "error", text: e.message });
        } else {
          // 400 INVALID_SIGNATURE / WRONG_EVENT -> forged or wrong ticket.
          setBanner({ kind: "error", text: "VÉ KHÔNG HỢP LỆ" });
        }
      } else {
        // Offline / network failure: warn, never let the visitor through (BR-25).
        setBanner({
          kind: "error",
          text: "Không thể xác thực – vui lòng kiểm tra kết nối mạng.",
        });
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  }, [gate]);

  useEffect(() => {
    // fps 10 + qrbox keeps recognition well under 1.5s on typical hardware.
    const scanner = new Html5QrcodeScanner(
      SCAN_REGION_ID,
      { fps: 10, qrbox: { width: 260, height: 260 }, rememberLastUsedCamera: true },
      false,
    );
    scanner.render(
      (decoded) => void handleContent(decoded),
      () => undefined, // ignore per-frame decode errors
    );
    return () => {
      scanner.clear().catch(() => undefined);
    };
  }, [handleContent]);

  const reset = () => {
    lastScanRef.current = { content: "", at: 0 };
    setBanner({ kind: "idle" });
    setManualCode("");
  };

  return (
    <main style={{ padding: 24, maxWidth: 640 }}>
      <h1>Soát vé [GĐ2]</h1>
      <label style={{ display: "block", marginBottom: 12 }}>
        Cổng soát vé (tùy chọn):{" "}
        <input
          value={gate}
          onChange={(e) => setGate(e.target.value)}
          placeholder="VD: Cổng A1"
          style={{ padding: 8, width: 200 }}
        />
      </label>

      <div id={SCAN_REGION_ID} />

      <div style={{ marginTop: 16 }}>
        <p>Camera lỗi? Nhập mã thủ công:</p>
        <input
          value={manualCode}
          onChange={(e) => setManualCode(e.target.value)}
          placeholder="Mã vé (UUID) hoặc nội dung QR"
          style={{ padding: 8, width: "100%", maxWidth: 420 }}
        />
        <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
          <button onClick={() => manualCode && void handleContent(manualCode)} disabled={busy}>
            Xác thực mã
          </button>
          <button onClick={reset}>Quét tiếp</button>
        </div>
      </div>

      {banner.kind === "ok" && (
        <div
          role="alert"
          style={{ marginTop: 16, padding: 16, background: "#16a34a", color: "#fff" }}
        >
          {banner.text}
        </div>
      )}
      {banner.kind === "error" && (
        <div
          role="alert"
          style={{ marginTop: 16, padding: 16, background: "#dc2626", color: "#fff" }}
        >
          {banner.text}
        </div>
      )}
      {busy && <p>Đang xác thực…</p>}
    </main>
  );
}
