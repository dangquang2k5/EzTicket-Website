package huce.fit.ezticket_backend.repository;

import huce.fit.ezticket_backend.entity.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, Long> {
    boolean existsByEventIdAndName(Long eventId, String name);
}
