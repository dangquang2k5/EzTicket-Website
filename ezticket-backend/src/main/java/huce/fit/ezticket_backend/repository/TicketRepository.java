package huce.fit.ezticket_backend.repository;

import huce.fit.ezticket_backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketCode(String ticketCode);

    List<Ticket> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
