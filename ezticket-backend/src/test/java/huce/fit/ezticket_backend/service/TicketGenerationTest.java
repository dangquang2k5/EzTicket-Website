package huce.fit.ezticket_backend.service;

import huce.fit.ezticket_backend.entity.Event;
import huce.fit.ezticket_backend.entity.Order;
import huce.fit.ezticket_backend.entity.OrderItem;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.TicketType;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.OrderStatus;
import huce.fit.ezticket_backend.repository.OrderItemRepository;
import huce.fit.ezticket_backend.repository.OrderRepository;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.repository.UserRepository;
import huce.fit.ezticket_backend.service.pdf.TicketPdfService;
import huce.fit.ezticket_backend.service.qr.QrPayloadCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phat hanh ve cho don PAID (SCRUM-27: BR-08, BR-09, BR-12).
 * Test bang Mockito, khong can DB/Redis that.
 */
@ExtendWith(MockitoExtension.class)
class TicketGenerationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketPdfService ticketPdfService;
    @Mock
    private QrPayloadCodec qrPayloadCodec;

    @InjectMocks
    private TicketsService ticketsService;

    private Order paidOrder() {
        User user = User.builder().id(7L).fullName("Nguyen Van A").email("a@example.com")
                .passwordHash("hash").build();
        return Order.builder().id(1L).user(user).orderCode("ORD-1")
                .totalAmount(new BigDecimal("300.00")).status(OrderStatus.PAID).build();
    }

    private List<OrderItem> twoItems(Order order) {
        Event event = Event.builder().id(101L).title("Show").venue("Hall").build();
        TicketType vip = TicketType.builder().id(11L).event(event).name("VIP")
                .price(new BigDecimal("200.00")).totalQuantity(100).build();
        TicketType std = TicketType.builder().id(12L).event(event).name("Standard")
                .price(new BigDecimal("100.00")).totalQuantity(100).build();
        OrderItem i1 = OrderItem.builder().id(21L).order(order).ticketType(vip)
                .quantity(2).unitPrice(new BigDecimal("200.00")).subtotal(new BigDecimal("400.00")).build();
        OrderItem i2 = OrderItem.builder().id(22L).order(order).ticketType(std)
                .quantity(1).unitPrice(new BigDecimal("100.00")).subtotal(new BigDecimal("100.00")).build();
        return List.of(i1, i2);
    }

    @Test
    void soVeBangTongQuantity() {
        Order order = paidOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ticketRepository.findByOrderId(1L)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(1L)).thenReturn(twoItems(order));
        when(qrPayloadCodec.sign(anyString(), anyLong()))
                .thenAnswer(inv -> new QrPayloadCodec.SignedQr("p", "s", "p.s"));
        AtomicLong seq = new AtomicLong(100);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(inv -> {
            Ticket t = inv.getArgument(0);
            t.setId(seq.getAndIncrement());
            return t;
        });

        List<Ticket> tickets = ticketsService.generateTickets(1L);

        assertEquals(3, tickets.size());
        assertTrue(tickets.stream().allMatch(t -> t.getTicketCode() != null));
        assertEquals(3, tickets.stream().map(Ticket::getTicketCode).distinct().count());
        verify(ticketRepository, times(3)).save(any(Ticket.class));
    }

    @Test
    void goiHaiLanKhongSinhTrung() {
        Order order = paidOrder();
        Ticket existing = Ticket.builder().id(100L).ticketCode(UUID.randomUUID().toString())
                .order(order).status(huce.fit.ezticket_backend.entity.enums.TicketStatus.VALID).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(ticketRepository.findByOrderId(1L)).thenReturn(List.of(existing));

        List<Ticket> first = ticketsService.generateTickets(1L);
        List<Ticket> second = ticketsService.generateTickets(1L);

        assertEquals(1, first.size());
        assertEquals(1, second.size());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void donChuaPaidThiKhongSinhVe() {
        Order order = paidOrder();
        order.setStatus(OrderStatus.HOLDING);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class, () -> ticketsService.generateTickets(1L));
        verify(ticketRepository, never()).save(any(Ticket.class));
    }
}
