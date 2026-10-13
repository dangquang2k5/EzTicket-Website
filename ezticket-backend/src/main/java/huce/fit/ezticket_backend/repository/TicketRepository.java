package huce.fit.ezticket_backend.repository;

import huce.fit.ezticket_backend.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    List<Ticket> findAllByIdIn(List<Long> ids);
}
