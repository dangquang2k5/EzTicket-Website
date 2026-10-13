package huce.fit.ezticket_backend.service.mail;

import java.util.List;

public interface OrderMailService {

    void sendOrderConfirmation(Long orderId, List<Long> ticketIds);
}
