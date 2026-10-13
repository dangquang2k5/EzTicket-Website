package huce.fit.ezticket_backend.controller;

import huce.fit.ezticket_backend.dto.common.ApiResponse;
import huce.fit.ezticket_backend.dto.ticket.CheckInRequest;
import huce.fit.ezticket_backend.dto.ticket.CheckInResponse;
import huce.fit.ezticket_backend.dto.ticket.TicketDetailResponse;
import huce.fit.ezticket_backend.dto.ticket.TicketItemResponse;
import huce.fit.ezticket_backend.dto.ticket.VerifyTicketRequest;
import huce.fit.ezticket_backend.dto.ticket.VerifyTicketResponse;
import huce.fit.ezticket_backend.entity.enums.TicketStatus;
import huce.fit.ezticket_backend.service.TicketsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket endpoints (SCRUM-29, class diagram: TicketsController).
 * Verify / check-in are [GĐ2] and ADMIN-only on Web Admin (C-06, C-13).
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketsController {

    private final TicketsService ticketsService;

    /** [GĐ2] POST /api/v1/tickets/verify — ADMIN only, read-only (BR-25). */
    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<VerifyTicketResponse>> verify(
            @Valid @RequestBody VerifyTicketRequest request) {
        VerifyTicketResponse data = ticketsService.verify(request.getQrContent(), request.getEventId());
        return ResponseEntity.ok(ApiResponse.ok("HỢP LỆ", data));
    }

    /** [GĐ2] POST /api/v1/tickets/{code}/check-in — ADMIN only (BR-25). */
    @PostMapping("/{code}/check-in")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CheckInResponse>> checkIn(
            @PathVariable String code,
            @RequestBody(required = false) CheckInRequest request,
            Authentication authentication) {
        String gate = request == null ? null : request.getGate();
        String staffEmail = authentication == null ? null : authentication.getName();
        CheckInResponse data = ticketsService.checkIn(code, gate, staffEmail);
        return ResponseEntity.ok(ApiResponse.ok("HỢP LỆ", data));
    }

    /** GET /api/v1/tickets/my — owner only (US-KH-06 AC-01, BR-17). */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<TicketItemResponse>>> listMine(
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Boolean upcoming,
            @PageableDefault(size = 10) Pageable pageable,
            Authentication authentication) {
        Page<TicketItemResponse> data =
                ticketsService.listMine(authentication.getName(), status, upcoming, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách vé thành công", data));
    }

    /** GET /api/v1/tickets/{code} — owner or ADMIN (US-KH-06 AC-03, BR-17). */
    @GetMapping("/{code}")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getDetail(
            @PathVariable String code, Authentication authentication) {
        TicketDetailResponse data = ticketsService.getDetail(
                code, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết vé thành công", data));
    }

    /** GET /api/v1/tickets/{code}/pdf — owner or ADMIN (US-KH-06 AC-02). */
    @GetMapping("/{code}/pdf")
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable String code, Authentication authentication) {
        byte[] pdf = ticketsService.getPdfBytes(
                code, authentication.getName(), isAdmin(authentication));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition
                        .attachment()
                        .filename(code + ".pdf")
                        .build().toString())
                .body(pdf);
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
