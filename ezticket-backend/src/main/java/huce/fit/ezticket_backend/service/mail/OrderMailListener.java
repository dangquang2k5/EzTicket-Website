package huce.fit.ezticket_backend.service.mail;

import huce.fit.ezticket_backend.event.TicketsReadyEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SCRUM-28: lắng nghe TicketsReadyEvent và gửi mail bất đồng bộ (BR-10).
 * Không ném ngoại lệ ra ngoài để không ảnh hưởng đơn PAID.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMailListener {

    private final OrderMailService orderMailService;

    @Async("ticketTaskExecutor")
    @EventListener
    public void onTicketsReady(TicketsReadyEvent event) {
        try {
            orderMailService.sendOrderConfirmation(event.orderId(), event.ticketIds());
        } catch (Exception e) {
            // Service đã tự log; đây là lưới an toàn cuối cùng.
            log.warn("[SCRUM-28] Listener bat ngoai le orderId={} lyDo={}: {}",
                    event.orderId(), e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
