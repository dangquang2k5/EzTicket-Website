package huce.fit.ezticket_backend.dto.ticket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** [GĐ2] POST /api/v1/tickets/{code}/check-in response data. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInResponse {

    private String code;
    private String status;
    private LocalDateTime checkInAt;
    private String gate;
}
