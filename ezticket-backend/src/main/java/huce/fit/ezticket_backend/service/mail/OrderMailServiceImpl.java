package huce.fit.ezticket_backend.service.mail;

import huce.fit.ezticket_backend.entity.Order;
import huce.fit.ezticket_backend.entity.OrderItem;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.repository.OrderRepository;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.service.pdf.TicketPdfService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * SCRUM-28: gửi email xác nhận đơn kèm PDF vé (BR-10, US-KH-05).
 * Mọi lỗi đều bị bắt và chỉ log, không ném ra ngoài để không rollback đơn PAID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMailServiceImpl implements OrderMailService {

    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final TicketPdfService ticketPdfService;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;

    @Value("${app.mail.from:no-reply@ezticket.vn}")
    private String mailFrom;

    @Override
    public void sendOrderConfirmation(Long orderId, List<Long> ticketIds) {
        try {
            doSend(orderId, ticketIds);
        } catch (MailException | MessagingException | IllegalStateException | IllegalArgumentException e) {
            log.warn("[SCRUM-28] Gui email that bai orderId={} toi={} lyDo={}: {}",
                    orderId, maskEmail(resolveRecipientSafe(orderId)),
                    e.getClass().getSimpleName(), e.getMessage());
        } catch (Exception e) {
            log.warn("[SCRUM-28] Gui email that bai (unexpected) orderId={} lyDo={}: {}",
                    orderId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void doSend(Long orderId, List<Long> ticketIds) throws MessagingException {
        if (orderId == null || ticketIds == null || ticketIds.isEmpty()) {
            throw new IllegalArgumentException("orderId/ticketIds khong duoc rong");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay don hang id=" + orderId));
        List<Ticket> tickets = ticketRepository.findAllByIdIn(ticketIds);
        if (tickets.isEmpty()) {
            throw new IllegalStateException("Khong tim thay ve nao cho orderId=" + orderId);
        }

        String recipient = order.getUser() != null ? order.getUser().getEmail() : null;
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalStateException("Don hang thieu email nguoi nhan, orderId=" + orderId);
        }
        String fullName = order.getUser().getFullName() != null
                ? order.getUser().getFullName() : "Quý khách";

        // Sự kiện đầu tiên (giả định tối thiểu [CHƯA CÓ TRONG TÀI LIỆU]: 1 đơn = 1 sự kiện).
        Ticket first = tickets.get(0);
        String eventTitle = first.getEvent() != null ? first.getEvent().getTitle() : "Sự kiện EzTicket";
        String startTime = first.getEvent() != null && first.getEvent().getStartTime() != null
                ? first.getEvent().getStartTime().format(DATE_TIME_FMT) : "Đang cập nhật";
        String venue = first.getEvent() != null
                ? joinNonBlank(first.getEvent().getVenue(), first.getEvent().getCity()) : "Đang cập nhật";

        List<Map<String, Object>> items = buildItems(order);
        String totalStr = formatVnd(order.getTotalAmount());

        Context ctx = new Context(Locale.forLanguageTag("vi-VN"));
        ctx.setVariable("fullName", fullName);
        ctx.setVariable("orderCode", order.getOrderCode());
        ctx.setVariable("eventTitle", eventTitle);
        ctx.setVariable("startTime", startTime);
        ctx.setVariable("venue", venue);
        ctx.setVariable("items", items);
        ctx.setVariable("totalAmount", totalStr);
        ctx.setVariable("ticketCount", tickets.size());
        String html = templateEngine.process("mail/order-confirmation", ctx);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(recipient);
        helper.setSubject("[EzTicket] Xác nhận đặt vé đơn " + order.getOrderCode());
        helper.setText(html, true);

        int attached = 0;
        for (Ticket ticket : tickets) {
            byte[] pdf;
            try {
                pdf = ticketPdfService.getPdfBytes(ticket.getId());
            } catch (Exception e) {
                log.warn("[SCRUM-28] Thieu PDF, bo qua dinh kem orderId={} ticketId={} lyDo={}: {}",
                        orderId, ticket.getId(), e.getClass().getSimpleName(), e.getMessage());
                continue;
            }
            if (pdf == null || pdf.length == 0) {
                log.warn("[SCRUM-28] Thieu PDF (rong), bo qua dinh kem orderId={} ticketId={}",
                        orderId, ticket.getId());
                continue;
            }
            String code = ticket.getTicketCode() != null ? ticket.getTicketCode() : String.valueOf(ticket.getId());
            helper.addAttachment("ve-" + code + ".pdf", new ByteArrayResource(pdf));
            attached++;
        }
        if (attached == 0) {
            throw new IllegalStateException("Khong co file PDF nao de dinh kem, orderId=" + orderId);
        }

        mailSender.send(message);
        log.info("[SCRUM-28] Da gui email xac nhan orderId={} toi={} kem {} tep PDF",
                orderId, maskEmail(recipient), attached);
    }

    private List<Map<String, Object>> buildItems(Order order) {
        Map<String, Map<String, Object>> grouped = new LinkedHashMap<>();
        // Ưu tiên orderItems (snapshot giá lúc đặt, BR-11).
        if (order.getOrderItems() != null && !order.getOrderItems().isEmpty()) {
            for (OrderItem item : order.getOrderItems()) {
                String name = item.getTicketType() != null ? item.getTicketType().getName() : "Vé";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("quantity", item.getQuantity());
                row.put("unitPrice", formatVnd(item.getUnitPrice()));
                grouped.put(name, row);
            }
            return new ArrayList<>(grouped.values());
        }
        return List.of();
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        NumberFormat fmt = NumberFormat.getNumberInstance(new Locale("vi", "VN"));
        fmt.setMaximumFractionDigits(0);
        return fmt.format(amount) + " ₫";
    }

    private String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(" - ");
                }
                sb.append(p.strip());
            }
        }
        return sb.length() == 0 ? "Đang cập nhật" : sb.toString();
    }

    private String resolveRecipientSafe(Long orderId) {
        try {
            return orderRepository.findById(orderId)
                    .map(o -> o.getUser() != null ? o.getUser().getEmail() : null)
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String masked = local.length() <= 2 ? "***" : local.substring(0, 2) + "***";
        return masked + "@" + parts[1];
    }
}
