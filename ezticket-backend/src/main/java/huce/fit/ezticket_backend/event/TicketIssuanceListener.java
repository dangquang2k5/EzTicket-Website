package huce.fit.ezticket_backend.event;

import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.service.pdf.TicketPdfService;
import huce.fit.ezticket_backend.service.TicketsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Worker nền phát hành vé sau khi đơn PAID (BR-10).
 * Chạy AFTER_COMMIT + @Async("ticketTaskExecutor") nên không chặn request thanh toán.
 * Lỗi một vé không chặn các vé còn lại; xong thì bắn {@link TicketsReadyEvent} (cho SCRUM-28 gửi mail).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssuanceListener {

    private final TicketsService ticketsService;
    private final TicketPdfService ticketPdfService;
    private final ApplicationEventPublisher eventPublisher;

    @Async("ticketTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        Long orderId = event.orderId();
        log.info("Bat dau phat hanh ve nen cho don {}", orderId);
        List<Ticket> tickets;
        try {
            tickets = ticketsService.generateTickets(orderId);
        } catch (Exception e) {
            log.error("Khong sinh duoc ve cho don {}", orderId, e);
            return;
        }
        List<Long> readyIds = new ArrayList<>();
        for (Ticket ticket : tickets) {
            try {
                ticketPdfService.generatePdf(ticket.getId());
                readyIds.add(ticket.getId());
            } catch (Exception e) {
                log.error("Khong sinh duoc PDF cho ve {} cua don {}", ticket.getId(), orderId, e);
            }
        }
        eventPublisher.publishEvent(new TicketsReadyEvent(orderId, List.copyOf(readyIds)));
        log.info("Hoan tat phat hanh ve don {}: {}/{} ve co PDF", orderId, readyIds.size(), tickets.size());
    }
}
