"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { getRole, getToken } from "@/lib/api";

/**
 * Route guard for /admin/* (C-13): single login endpoint + client role check.
 * Non-ADMIN or unauthenticated users are sent to /admin/login.
 * Server still enforces role on every admin API (BR-17/C-13).
 */
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    if (!getToken() || getRole() !== "ADMIN") {
      router.replace("/admin/login");
    } else {
      setAllowed(true);
    }
  }, [router]);

  if (!allowed) return <p style={{ padding: 24 }}>Đang kiểm tra quyền truy cập…</p>;
  return <>{children}</>;
}
