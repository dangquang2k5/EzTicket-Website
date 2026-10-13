package huce.fit.ezticket_backend.event;

import java.util.List;

public record TicketsReadyEvent(Long orderId, List<Long> ticketIds) {
}
