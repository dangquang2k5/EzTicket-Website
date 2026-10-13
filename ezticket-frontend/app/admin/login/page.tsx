// Placeholder: real /admin/login screen belongs to the Auth workstream (SCRUM-16).
// It must call POST /api/v1/auth/login, store accessToken + role, then route here.
export default function AdminLoginPlaceholder() {
  return (
    <main style={{ padding: 24 }}>
      <h1>Đăng nhập Admin</h1>
      <p>
        [CHƯA CÓ TRONG WORKSTREAM NÀY] Màn hình đăng nhập do SCRUM-16 phụ trách: gọi
        cùng một endpoint <code>/api/v1/auth/login</code> rồi kiểm tra role = ADMIN (C-13).
      </p>
    </main>
  );
}
