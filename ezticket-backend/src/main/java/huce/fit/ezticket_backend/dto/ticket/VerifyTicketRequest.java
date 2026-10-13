package huce.fit.ezticket_backend.dto.ticket;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** [GĐ2] POST /api/v1/tickets/verify body (API Spec 9.4, group 6). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyTicketRequest {

    @NotBlank(message = "qrContent không được để trống")
    private String qrContent;

    private Long eventId;
}
