package huce.fit.ezticket_backend.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** [GĐ2] POST /api/v1/tickets/verify response data. Does NOT change state. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyTicketResponse {

    private boolean valid;
    private String code;
    private String buyer;
    private String ticketType;
    private String status;
    private String message;
}
