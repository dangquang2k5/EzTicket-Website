package huce.fit.ezticket_backend.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** GET /api/v1/tickets/my item (US-KH-06 AC-01). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketItemResponse {

    private String code;
    private String eventTitle;
    private LocalDateTime eventTime;
    private String venue;
    private String ticketType;
    private String status;
    private String qrUrl;
}
