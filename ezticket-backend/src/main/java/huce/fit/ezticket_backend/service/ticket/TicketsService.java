package huce.fit.ezticket_backend.service.ticket;

import huce.fit.ezticket_backend.entity.Order;
import huce.fit.ezticket_backend.entity.OrderItem;
import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.enums.OrderStatus;
import huce.fit.ezticket_backend.entity.enums.TicketStatus;
import huce.fit.ezticket_backend.repository.OrderItemRepository;
import huce.fit.ezticket_backend.repository.OrderRepository;
import huce.fit.ezticket_backend.repository.TicketRepository;
import huce.fit.ezticket_backend.service.qr.QrPayloadCodec;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sinh vé điện tử cho đơn đã PAID (BR-08, BR-09, BR-12).
 * Mỗi đơn vị trong order_items sinh đúng một vé, ticketCode = UUID v4 (C-04 theo DD).
 * Idempotent: đơn đã có vé thì trả lại danh sách cũ, không sinh lần hai.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TicketRepository ticketRepository;
    private final QrPayloadCodec qrPayloadCodec;

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
}
