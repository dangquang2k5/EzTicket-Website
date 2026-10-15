package huce.fit.ezticket_backend.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** [GĐ2] POST /api/v1/tickets/{code}/check-in body. Gate is optional. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInRequest {

    private String gate;
}
