"use client";

import { useEffect, useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import {
  ApiError,
  api,
  downloadTicketPdf,
  type TicketDetail,
  type TicketItem,
} from "@/lib/api";

/**
 * My tickets (US-KH-06): list with status/event/time/venue, QR display, PDF download.
 */
export default function MyTicketsPage() {
  const [tickets, setTickets] = useState<TicketItem[]>([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [detail, setDetail] = useState<TicketDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const query = statusFilter ? `?status=${encodeURIComponent(statusFilter)}` : "";
    api
      .get<{ content: TicketItem[] }>(`/tickets/my${query}`)
      .then((page) => {
        if (!cancelled) setTickets(page.content ?? []);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(e instanceof ApiError ? e.message : "Không tải được danh sách vé.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [statusFilter]);

  const openDetail = async (code: string) => {
    try {
      const d = await api.get<TicketDetail>(`/tickets/${encodeURIComponent(code)}`);
      setDetail(d);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Không tải được chi tiết vé.");
    }
  };

  const download = async (code: string) => {
    setDownloading(code);
    try {
      await downloadTicketPdf(code);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Tải PDF thất bại.");
    } finally {
      setDownloading(null);
    }
  };

  return (
    <main style={{ padding: 24, maxWidth: 760 }}>
      <h1>Vé của tôi</h1>

      <label>
        Lọc trạng thái:{" "}
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)}>
          <option value="">Tất cả</option>
          <option value="VALID">Đã thanh toán</option>
          <option value="USED">Đã sử dụng</option>
          <option value="EXPIRED">Đã hết hạn</option>
        </select>
      </label>

      {loading && <p>Đang tải…</p>}
      {error && (
        <p role="alert" style={{ color: "#dc2626" }}>
          {error}
        </p>
      )}
      {!loading && tickets.length === 0 && <p>Chưa có vé nào.</p>}

      <ul style={{ listStyle: "none", padding: 0 }}>
        {tickets.map((t) => (
          <li
            key={t.code}
            style={{ border: "1px solid #ddd", borderRadius: 8, padding: 12, marginTop: 12 }}
          >
            <strong>{t.eventTitle}</strong>
            <div>
              {t.ticketType} · {t.status}
            </div>
            <div>
              {new Date(t.eventTime).toLocaleString("vi-VN")} · {t.venue}
            </div>
            <div style={{ marginTop: 8, display: "flex", gap: 8 }}>
              <button onClick={() => void openDetail(t.code)}>Xem QR</button>
              <button onClick={() => void download(t.code)} disabled={downloading === t.code}>
                {downloading === t.code ? "Đang tải…" : "Tải PDF"}
              </button>
            </div>
          </li>
        ))}
      </ul>

      {detail && (
        <section style={{ marginTop: 24, border: "1px solid #333", padding: 16 }}>
          <h2>Chi tiết vé</h2>
          <p>
            {detail.eventTitle} · {detail.ticketType} · {detail.status}
          </p>
          <QRCodeSVG value={detail.qrContent} size={220} />
          <p>
            <code>{detail.code}</code>
          </p>
          <div style={{ display: "flex", gap: 8 }}>
            <button onClick={() => void download(detail.code)}>Tải PDF</button>
            <button onClick={() => setDetail(null)}>Đóng</button>
          </div>
        </section>
      )}
    </main>
  );
}
