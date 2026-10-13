import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "EzTicket",
  description: "Web bán vé sự kiện trực tuyến",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi">
      <body style={{ margin: 0, fontFamily: "system-ui, sans-serif" }}>{children}</body>
    </html>
  );
}
