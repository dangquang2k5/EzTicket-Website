package huce.fit.ezticket_backend.service;

import huce.fit.ezticket_backend.dto.ticket.CheckInResponse;
import huce.fit.ezticket_backend.dto.ticket.TicketDetailResponse;
import huce.fit.ezticket_backend.dto.ticket.VerifyTicketResponse;
import huce.fit.ezticket_backend.entity.Event;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.TicketType;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.TicketStatus;
import huce.fit.ezticket_backend.entity.enums.UserRole;
import huce.fit.ezticket_backend.exception.TicketException;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.repository.UserRepository;
import huce.fit.ezticket_backend.service.pdf.TicketPdfService;
import huce.fit.ezticket_backend.service.qr.QrPayloadCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SCRUM-29 unit tests (JUnit5 + Mockito, no real DB/Redis).
 * Covers: wrong signature, wrong event, used ticket, expired ticket,
 * atomic concurrent check-in (exactly 1 winner), and role guards.
 */
@ExtendWith(MockitoExtension.class)
class TicketsServiceTest {

    private static final String CODE = "11111111-1111-4111-8111-111111111111";

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private QrPayloadCodec qrPayloadCodec;
    @Mock
    private TicketPdfService ticketPdfService;

    @InjectMocks
    private TicketsService ticketsService;

    private User owner;
    private Event futureEvent;
    private Ticket validTicket;

    @BeforeEach
    void setUp() {
        owner = User.builder().id(7L).fullName("Nguyen Van A")
                .email("a@example.com").role(UserRole.USER).build();
        futureEvent = Event.builder().id(101L).title("Show X")
                .venue("Hall 1")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(2))
                .build();
        TicketType type = TicketType.builder().id(11L).name("VIP").build();
        validTicket = Ticket.builder().id(1L).ticketCode(CODE)
                .user(owner).event(futureEvent).ticketType(type)
                .qrPayload(CODE + ".101.1700000000").qrSignature("sig")
                .status(TicketStatus.VALID).build();
    }

    private void stubValidQr() {
        when(qrPayloadCodec.verify(anyString())).thenReturn(
                new QrPayloadCodec.QrVerifyResult(true, CODE, 101L, 1700000000L));
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(validTicket));
    }

    @Test
    void verify_validTicket_returnsHopLe() {
        stubValidQr();

        VerifyTicketResponse res = ticketsService.verify("qr", 101L);

        assertTrue(res.isValid());
        assertEquals("HỢP LỆ", res.getMessage());
        assertEquals(CODE, res.getCode());
    }

    @Test
    void verify_badSignature_throws400() {
        when(qrPayloadCodec.verify(anyString())).thenReturn(
                new QrPayloadCodec.QrVerifyResult(false, null, null, null));

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.verify("fake", null));

        assertEquals("INVALID_SIGNATURE", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
        assertEquals("VÉ KHÔNG HỢP LỆ", ex.getMessage());
    }

    @Test
    void verify_wrongEventParam_throws400() {
        stubValidQr();

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.verify("qr", 999L));

        assertEquals("WRONG_EVENT", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());
    }

    @Test
    void verify_qrEmbeddedEventMismatch_throws400() {
        when(qrPayloadCodec.verify(anyString())).thenReturn(
                new QrPayloadCodec.QrVerifyResult(true, CODE, 555L, 1700000000L));
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(validTicket));

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.verify("qr", null));

        assertEquals("WRONG_EVENT", ex.getErrorCode());
    }

    @Test
    void verify_usedTicket_throws409WithTimestamp() {
        Ticket used = Ticket.builder().ticketCode(CODE).user(owner)
                .event(futureEvent).ticketType(validTicket.getTicketType())
                .qrPayload("p").qrSignature("s").status(TicketStatus.USED)
                .checkedInAt(LocalDateTime.of(2026, 8, 15, 20, 30, 45))
                .build();
        when(qrPayloadCodec.verify(anyString())).thenReturn(
                new QrPayloadCodec.QrVerifyResult(true, CODE, 101L, 1700000000L));
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(used));

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.verify("qr", 101L));

        assertEquals("ALREADY_USED", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
        assertEquals("CẢNH BÁO: VÉ ĐÃ SỬ DỤNG LÚC [20:30:45]", ex.getMessage());
    }

    @Test
    void verify_pastEvent_throwsExpired409() {
        Event past = Event.builder().id(101L).title("Old")
                .startTime(LocalDateTime.now().minusDays(2))
                .endTime(LocalDateTime.now().minusDays(1)).build();
        Ticket t = Ticket.builder().ticketCode(CODE).user(owner).event(past)
                .ticketType(validTicket.getTicketType())
                .qrPayload("p").qrSignature("s").status(TicketStatus.VALID).build();
        when(qrPayloadCodec.verify(anyString())).thenReturn(
                new QrPayloadCodec.QrVerifyResult(true, CODE, 101L, 1700000000L));
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(t));

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.verify("qr", 101L));

        assertEquals("EXPIRED", ex.getErrorCode());
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void checkIn_validTicket_flipsToUsed() {
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(validTicket));
        when(userRepository.findByEmail("admin@ezticket.vn")).thenReturn(Optional.empty());
        when(ticketRepository.checkInWithoutStaff(eq(CODE), any(), eq("A1"))).thenReturn(1);

        CheckInResponse res = ticketsService.checkIn(CODE, "A1", "admin@ezticket.vn");

        assertEquals("USED", res.getStatus());
        assertEquals("A1", res.getGate());
    }

    @Test
    void checkIn_knownStaff_linksStaffAtomically() {
        User admin = User.builder().id(9L).email("admin@ezticket.vn").role(UserRole.ADMIN).build();
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(validTicket));
        when(userRepository.findByEmail("admin@ezticket.vn")).thenReturn(Optional.of(admin));
        when(userRepository.getReferenceById(9L)).thenReturn(admin);
        when(ticketRepository.checkIn(eq(CODE), any(), eq("A1"), eq(admin))).thenReturn(1);

        CheckInResponse res = ticketsService.checkIn(CODE, "A1", "admin@ezticket.vn");

        assertEquals("USED", res.getStatus());
    }

    @Test
    void checkIn_rescan_throws409WithOldTime() {
        Ticket used = Ticket.builder().ticketCode(CODE).user(owner)
                .event(futureEvent).ticketType(validTicket.getTicketType())
                .status(TicketStatus.USED)
                .checkedInAt(LocalDateTime.of(2026, 8, 15, 19, 5, 7))
                .build();
        // First lookup sees VALID, atomic UPDATE loses (0 rows), reload sees USED.
        when(ticketRepository.findByTicketCode(CODE))
                .thenReturn(Optional.of(validTicket), Optional.of(used));
        when(userRepository.findByEmail("admin@ezticket.vn")).thenReturn(Optional.empty());
        when(ticketRepository.checkInWithoutStaff(eq(CODE), any(), eq("A1"))).thenReturn(0);

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.checkIn(CODE, "A1", "admin@ezticket.vn"));

        assertEquals("ALREADY_USED", ex.getErrorCode());
        assertEquals("CẢNH BÁO: VÉ ĐÃ SỬ DỤNG LÚC [19:05:07]", ex.getMessage());
    }

    @Test
    void checkIn_concurrent_twoRequests_onlyOneSucceeds() throws Exception {
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(validTicket));
        when(userRepository.findByEmail("staff@ezticket.vn")).thenReturn(Optional.empty());
        // Simulate atomic UPDATE ... WHERE status='VALID': exactly one caller gets row-count 1.
        AtomicInteger winners = new AtomicInteger(0);
        when(ticketRepository.checkInWithoutStaff(eq(CODE), any(), eq("A1")))
                .thenAnswer(inv -> winners.getAndIncrement() == 0 ? 1 : 0);
        // After losing the race the reload still reports ALREADY_USED (BR-25).
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<String> f1 = pool.submit(() -> {
                start.await();
                try {
                    ticketsService.checkIn(CODE, "A1", "staff@ezticket.vn");
                    return "OK";
                } catch (TicketException e) {
                    return e.getErrorCode();
                }
            });
            Future<String> f2 = pool.submit(() -> {
                start.await();
                try {
                    ticketsService.checkIn(CODE, "A1", "staff@ezticket.vn");
                    return "OK";
                } catch (TicketException e) {
                    return e.getErrorCode();
                }
            });
            start.countDown();
            String r1 = f1.get();
            String r2 = f2.get();
            int ok = (r1.equals("OK") ? 1 : 0) + (r2.equals("OK") ? 1 : 0);
            assertEquals(1, ok, "exactly one concurrent check-in must succeed, got " + r1 + "/" + r2);
            assertTrue(r1.equals("ALREADY_USED") || r2.equals("ALREADY_USED"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void getDetail_nonOwnerNonAdmin_throws403() {
        when(ticketRepository.findDetailByTicketCode(CODE)).thenReturn(Optional.of(validTicket));

        TicketException ex = assertThrows(TicketException.class,
                () -> ticketsService.getDetail(CODE, "other@example.com", false));

        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    @Test
    void getDetail_adminCanView() {
        when(ticketRepository.findDetailByTicketCode(CODE)).thenReturn(Optional.of(validTicket));

        TicketDetailResponse res = ticketsService.getDetail(CODE, "admin@ezticket.vn", true);

        assertEquals(CODE, res.getCode());
    }

    @Test
    void getPdfBytes_delegatesToContract() {
        when(ticketRepository.findByTicketCode(CODE)).thenReturn(Optional.of(validTicket));
        when(ticketPdfService.getPdfBytes(1L)).thenReturn(new byte[]{1, 2, 3});

        byte[] pdf = ticketsService.getPdfBytes(CODE, "a@example.com", false);

        assertEquals(3, pdf.length);
    }

    @Test
    void verifyAndCheckIn_endpoints_requireAdminRole() throws Exception {
        // BR-17/C-13: USER calling verify/check-in must be rejected with 403
        // (enforced by @PreAuthorize + SecurityConfig matchers).
        for (String method : new String[]{"verify", "checkIn"}) {
            Method m = findControllerMethod(method);
            org.springframework.security.access.prepost.PreAuthorize ann =
                    m.getAnnotation(org.springframework.security.access.prepost.PreAuthorize.class);
            assertTrue(ann != null && ann.value().contains("ADMIN"),
                    method + " must require ADMIN role");
        }
    }

    private Method findControllerMethod(String name) {
        for (Method m : huce.fit.ezticket_backend.controller.TicketsController.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalStateException("method not found: " + name);
    }
}
