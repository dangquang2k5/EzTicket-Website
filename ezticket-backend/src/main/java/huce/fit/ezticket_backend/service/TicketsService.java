package huce.fit.ezticket_backend.service;

import huce.fit.ezticket_backend.dto.ticket.CheckInResponse;
import huce.fit.ezticket_backend.dto.ticket.TicketDetailResponse;
import huce.fit.ezticket_backend.dto.ticket.TicketItemResponse;
import huce.fit.ezticket_backend.dto.ticket.VerifyTicketResponse;
import huce.fit.ezticket_backend.entity.Order;
import huce.fit.ezticket_backend.entity.OrderItem;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.OrderStatus;
import huce.fit.ezticket_backend.entity.enums.TicketStatus;
import huce.fit.ezticket_backend.exception.TicketException;
import huce.fit.ezticket_backend.repository.OrderItemRepository;
import huce.fit.ezticket_backend.repository.OrderRepository;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.repository.UserRepository;
import huce.fit.ezticket_backend.service.pdf.TicketPdfService;
import huce.fit.ezticket_backend.service.qr.QrPayloadCodec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ticket business logic (class diagram: TicketsService).
 * Controller -&gt; Service -&gt; Repository. No business logic in the controller.
 *
 * <p>Phat hanh ve (SCRUM-27): BR-08, BR-09, BR-12.
 * <p>C-04 default (DD format): QR verify is delegated to {@link QrPayloadCodec};
 * this class never re-implements signature logic.
 * BR-17: users only see their own tickets (others -&gt; 403), admins see everything.
 * BR-24: a ticket past {@code events.end_time} is EXPIRED.
 * BR-25 [GĐ2]: verify checks the HMAC signature FIRST, then existence/event/status,
 * and never changes state; check-in flips VALID -&gt; USED with one atomic UPDATE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketsService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final QrPayloadCodec qrPayloadCodec;
    private final TicketPdfService ticketPdfService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * Sinh ve dien tu cho don da PAID (BR-08, BR-09, BR-12).
     * Moi don vi trong order_items sinh dung mot ve, ticketCode = UUID v4 (C-04 theo DD).
     * Idempotent: don da co ve thi tra lai danh sach cu, khong sinh lan hai.
     */
    @Transactional
    public List<Ticket> generateTickets(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Khong tim thay don hang " + orderId));
        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("Chi sinh ve cho don PAID, don " + orderId + " dang " + order.getStatus());
        }
        List<Ticket> existing = ticketRepository.findByOrderId(orderId);
        if (!existing.isEmpty()) {
            log.info("Don {} da co {} ve, bo qua sinh lan hai (BR-12)", orderId, existing.size());
            return existing;
        }
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<Ticket> created = new ArrayList<>();
        for (OrderItem item : items) {
            Long eventId = item.getTicketType().getEvent().getId();
            for (int i = 0; i < item.getQuantity(); i++) {
                String ticketCode = UUID.randomUUID().toString();
                QrPayloadCodec.SignedQr signed = qrPayloadCodec.sign(ticketCode, eventId);
                Ticket ticket = Ticket.builder()
                        .ticketCode(ticketCode)
                        .order(order)
                        .orderItem(item)
                        .ticketType(item.getTicketType())
                        .user(order.getUser())
                        .event(item.getTicketType().getEvent())
                        .holderName(order.getUser().getFullName())
                        .qrPayload(signed.payload())
                        .qrSignature(signed.signature())
                        .status(TicketStatus.VALID)
                        .build();
                created.add(ticketRepository.save(ticket));
            }
        }
        log.info("Da sinh {} ve cho don {}", created.size(), orderId);
        return created;
    }

    /**
     * [GĐ2] POST /tickets/verify. Read-only: never changes ticket state.
     */
    @Transactional(readOnly = true)
    public VerifyTicketResponse verify(String qrContent, Long eventId) {
        // 1. Signature FIRST (BR-25). Never log full QR content.
        QrPayloadCodec.QrVerifyResult decoded = qrPayloadCodec.verify(qrContent);
        if (decoded == null || !decoded.signatureValid()) {
            throw TicketException.invalidSignature();
        }

        // 2. Ticket must exist.
        Ticket ticket = ticketRepository.findByTicketCode(decoded.ticketCode())
                .orElseThrow(TicketException::invalidSignature);

        // 3. Event must match (both the QR-embedded event and the explicit filter).
        if (decoded.eventId() != null
                && !decoded.eventId().equals(ticket.getEvent().getId())) {
            throw TicketException.wrongEvent();
        }
        if (eventId != null && !eventId.equals(ticket.getEvent().getId())) {
            throw TicketException.wrongEvent();
        }

        // 4. Status / expiry.
        assertUsable(ticket);

        return VerifyTicketResponse.builder()
                .valid(true)
                .code(ticket.getTicketCode())
                .buyer(ticket.getUser().getFullName())
                .ticketType(ticket.getTicketType().getName())
                .status(ticket.getStatus().name())
                .message("HỢP LỆ")
                .build();
    }

    /**
     * [GĐ2] POST /tickets/{code}/check-in. Atomic VALID -&gt; USED (BR-25).
     * A re-scan returns 409 ALREADY_USED with the original check-in time.
     */
    @Transactional
    public CheckInResponse checkIn(String code, String gate, String staffEmail) {
        Ticket ticket = ticketRepository.findByTicketCode(code)
                .orElseThrow(TicketException::notFound);
        assertUsable(ticket);

        Long staffId = userRepository.findByEmail(staffEmail)
                .map(User::getId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        // getReferenceById creates a proxy without a SELECT; the UPDATE stays atomic.
        int updated = (staffId == null)
                ? ticketRepository.checkInWithoutStaff(code, now, gate)
                : ticketRepository.checkIn(code, now, gate,
                        userRepository.getReferenceById(staffId));

        if (updated == 1) {
            return CheckInResponse.builder()
                    .code(code)
                    .status(TicketStatus.USED.name())
                    .checkInAt(now)
                    .gate(gate)
                    .build();
        }
        // Lost the race or already used: reload and report the original state.
        Ticket current = ticketRepository.findByTicketCode(code)
                .orElseThrow(TicketException::notFound);
        throw toAlreadyUsed(current);
    }

    /** GET /tickets/my (US-KH-06 AC-01). */
    @Transactional(readOnly = true)
    public Page<TicketItemResponse> listMine(String email, TicketStatus status,
                                             Boolean upcoming, Pageable pageable) {
        LocalDateTime upcomingAfter = Boolean.TRUE.equals(upcoming) ? LocalDateTime.now() : null;
        return ticketRepository.searchMine(email, status, upcomingAfter, pageable)
                .map(t -> TicketItemResponse.builder()
                        .code(t.getTicketCode())
                        .eventTitle(t.getEvent().getTitle())
                        .eventTime(t.getEvent().getStartTime())
                        .venue(t.getEvent().getVenue())
                        .ticketType(t.getTicketType().getName())
                        .status(resolveStatus(t).name())
                        .qrUrl(null) // [CHƯA CÓ TRONG TÀI LIỆU] no qrUrl column/contract; FE renders qrContent from detail.
                        .build());
    }

    /** GET /tickets/{code} (US-KH-06 AC-02/AC-03, BR-17). */
    @Transactional(readOnly = true)
    public TicketDetailResponse getDetail(String code, String email, boolean isAdmin) {
        Ticket ticket = ticketRepository.findDetailByTicketCode(code)
                .orElseThrow(TicketException::notFound);
        if (!isAdmin && !ticket.getUser().getEmail().equals(email)) {
            throw TicketException.forbidden();
        }
        return TicketDetailResponse.builder()
                .code(ticket.getTicketCode())
                .eventTitle(ticket.getEvent().getTitle())
                .eventTime(ticket.getEvent().getStartTime())
                .venue(ticket.getEvent().getVenue())
                .ticketType(ticket.getTicketType().getName())
                .holderName(ticket.getHolderName())
                .buyerEmail(ticket.getUser().getEmail())
                .status(resolveStatus(ticket).name())
                .qrContent(ticket.getQrPayload() + "." + ticket.getQrSignature())
                .qrSignature(ticket.getQrSignature())
                .checkedInAt(ticket.getCheckedInAt())
                .build();
    }

    /** GET /tickets/{code}/pdf (US-KH-06 AC-02, BR-17). */
    @Transactional(readOnly = true)
    public byte[] getPdfBytes(String code, String email, boolean isAdmin) {
        Ticket ticket = ticketRepository.findByTicketCode(code)
                .orElseThrow(TicketException::notFound);
        if (!isAdmin && !ticket.getUser().getEmail().equals(email)) {
            throw TicketException.forbidden();
        }
        // Contract use only: PDF rendering stays inside TicketPdfService.
        return ticketPdfService.getPdfBytes(ticket.getId());
    }

    private void assertUsable(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.USED) {
            throw toAlreadyUsed(ticket);
        }
        if (ticket.getStatus() == TicketStatus.CANCELLED) {
            throw TicketException.cancelled();
        }
        if (ticket.getStatus() == TicketStatus.EXPIRED || isPastEvent(ticket)) {
            throw TicketException.expired();
        }
    }

    private boolean isPastEvent(Ticket ticket) {
        return ticket.getEvent() != null
                && ticket.getEvent().getEndTime() != null
                && LocalDateTime.now().isAfter(ticket.getEvent().getEndTime());
    }

    private TicketStatus resolveStatus(Ticket ticket) {
        if (ticket.getStatus() == TicketStatus.VALID && isPastEvent(ticket)) {
            return TicketStatus.EXPIRED;
        }
        return ticket.getStatus();
    }

    private TicketException toAlreadyUsed(Ticket ticket) {
        String at = ticket.getCheckedInAt() == null
                ? "--:--:--" : ticket.getCheckedInAt().format(TIME_FMT);
        return TicketException.alreadyUsed(at);
    }
}
