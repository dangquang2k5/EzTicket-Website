package huce.fit.ezticket_backend.service.mail;

import huce.fit.ezticket_backend.entity.Event;
import huce.fit.ezticket_backend.entity.Order;
import huce.fit.ezticket_backend.entity.OrderItem;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.TicketType;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.repository.OrderRepository;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.service.pdf.TicketPdfService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SCRUM-28: JUnit5 + Mockito, không cần SMTP/DB/Redis thật (BR-10).
 */
@ExtendWith(MockitoExtension.class)
class OrderMailServiceTest {

    @Mock
    JavaMailSender mailSender;
    @Mock
    TicketPdfService ticketPdfService;
    @Mock
    OrderRepository orderRepository;
    @Mock
    TicketRepository ticketRepository;

    TemplateEngine realTemplateEngine;
    OrderMailServiceImpl service;

    jakarta.mail.Session session = jakarta.mail.Session.getInstance(new java.util.Properties());

    @BeforeEach
    void setUp() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        realTemplateEngine = engine;

        service = new OrderMailServiceImpl(
                mailSender, realTemplateEngine, ticketPdfService, orderRepository, ticketRepository);
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@ezticket.vn");
    }

    private Order orderFixture() {
        User user = User.builder().id(7L).fullName("Nguyen Van A")
                .email("nguyenvana@example.com").build();
        Order order = Order.builder().id(501L).user(user)
                .orderCode("ORD-20260101-001")
                .totalAmount(new BigDecimal("1000000.00")).build();
        TicketType vip = TicketType.builder().id(11L).name("VIP")
                .price(new BigDecimal("500000")).build();
        OrderItem item = OrderItem.builder().id(1L).order(order)
                .ticketType(vip).quantity(2)
                .unitPrice(new BigDecimal("500000")).subtotal(new BigDecimal("1000000")).build();
        order.setOrderItems(List.of(item));
        return order;
    }

    private Ticket ticketFixture(Long id, String code, Order order) {
        Event event = Event.builder().id(101L).title("Dem nhac Hoi An")
                .venue("Nha hat Lon").city("Ha Noi")
                .startTime(LocalDateTime.of(2026, 12, 20, 20, 0)).build();
        User user = order.getUser();
        return Ticket.builder().id(id).ticketCode(code).order(order)
                .user(user).event(event).qrPayload("p").qrSignature("s").build();
    }

    @Test
    void renderTemplate_containsOrderCodeAndEventTitle() {
        Context ctx = new Context(java.util.Locale.forLanguageTag("vi-VN"));
        ctx.setVariable("fullName", "Nguyen Van A");
        ctx.setVariable("orderCode", "ORD-20260101-001");
        ctx.setVariable("eventTitle", "Dem nhac Hoi An");
        ctx.setVariable("startTime", "20:00 20/12/2026");
        ctx.setVariable("venue", "Nha hat Lon - Ha Noi");
        ctx.setVariable("items", List.of());
        ctx.setVariable("totalAmount", "1.000.000 ₫");
        ctx.setVariable("ticketCount", 2);

        String html = realTemplateEngine.process("mail/order-confirmation", ctx);

        assertThat(html).contains("ORD-20260101-001");
        assertThat(html).contains("Dem nhac Hoi An");
        assertThat(html).contains("Đặt vé thành công! Vé đã được gửi đến email của bạn.");
    }

    @Test
    void sendOrderConfirmation_attachesOnePdfPerTicket() throws Exception {
        Order order = orderFixture();
        Ticket t1 = ticketFixture(1L, "CODE-1", order);
        Ticket t2 = ticketFixture(2L, "CODE-2", order);
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(ticketRepository.findAllByIdIn(List.of(1L, 2L))).thenReturn(List.of(t1, t2));
        when(ticketPdfService.getPdfBytes(1L)).thenReturn(new byte[]{1, 2, 3});
        when(ticketPdfService.getPdfBytes(2L)).thenReturn(new byte[]{4, 5, 6});
        MimeMessage mime = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mime);

        service.sendOrderConfirmation(501L, List.of(1L, 2L));

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        Object content = captor.getValue().getContent();
        assertThat(content).isInstanceOf(jakarta.mail.internet.MimeMultipart.class);
        jakarta.mail.internet.MimeMultipart mp = (jakarta.mail.internet.MimeMultipart) content;
        // 1 body + 2 attachments
        assertThat(mp.getCount()).isEqualTo(3);
    }

    @Test
    void sendThrows_doesNotPropagate() {
        Order order = orderFixture();
        Ticket t1 = ticketFixture(1L, "CODE-1", order);
        when(orderRepository.findById(501L)).thenReturn(Optional.of(order));
        when(ticketRepository.findAllByIdIn(List.of(1L))).thenReturn(List.of(t1));
        when(ticketPdfService.getPdfBytes(1L)).thenReturn(new byte[]{9});
        MimeMessage mime = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        assertThatCode(() -> service.sendOrderConfirmation(501L, List.of(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void listener_swallowExceptionFromService() {
        OrderMailService failing = (o, t) -> {
            throw new RuntimeException("boom");
        };
        OrderMailListener listener = new OrderMailListener(failing);
        assertThatCode(() -> listener.onTicketsReady(
                new huce.fit.ezticket_backend.event.TicketsReadyEvent(501L, List.of(1L))))
                .doesNotThrowAnyException();
    }

    @Test
    void templateEscapesHtml_preventsXss() {
        Context ctx = new Context(java.util.Locale.forLanguageTag("vi-VN"));
        ctx.setVariable("fullName", "<script>alert(1)</script>");
        ctx.setVariable("orderCode", "ORD-1");
        ctx.setVariable("eventTitle", "E");
        ctx.setVariable("startTime", "t");
        ctx.setVariable("venue", "v");
        ctx.setVariable("items", List.of());
        ctx.setVariable("totalAmount", "0 ₫");
        ctx.setVariable("ticketCount", 1);
        String html = realTemplateEngine.process("mail/order-confirmation", ctx);
        assertThat(html).doesNotContain("<script>alert(1)</script>");
        assertThat(html).contains("&lt;script&gt;");
    }
}
