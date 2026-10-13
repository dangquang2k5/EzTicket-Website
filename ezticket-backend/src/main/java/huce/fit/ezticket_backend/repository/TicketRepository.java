package huce.fit.ezticket_backend.repository;

import huce.fit.ezticket_backend.entity.Ticket;
import huce.fit.ezticket_backend.entity.User;
import huce.fit.ezticket_backend.entity.enums.TicketStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Ticket repository (SCRUM-29).
 * BR-25: check-in is a single atomic UPDATE ... WHERE status='VALID'.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByTicketCode(String ticketCode);

    @Query("""
            SELECT t FROM Ticket t
            JOIN FETCH t.event e JOIN FETCH t.ticketType tt JOIN FETCH t.user u
            WHERE t.ticketCode = :code
            """)
    Optional<Ticket> findDetailByTicketCode(@Param("code") String code);

    @Query("""
            SELECT t FROM Ticket t
            JOIN t.event e
            WHERE t.user.email = :email
              AND (:status IS NULL OR t.status = :status)
              AND (:upcomingAfter IS NULL OR e.startTime > :upcomingAfter)
            """)
    Page<Ticket> searchMine(@Param("email") String email,
                            @Param("status") TicketStatus status,
                            @Param("upcomingAfter") LocalDateTime upcomingAfter,
                            Pageable pageable);

    /**
     * BR-25 atomic check-and-set: only one concurrent request flips VALID -&gt; USED.
     *
     * @return 1 when this call performed the check-in, 0 when already used/cancelled/expired.
     */
    @Modifying
    @Query("""
            UPDATE Ticket t
            SET t.status = huce.fit.ezticket_backend.entity.enums.TicketStatus.USED,
                t.checkedInAt = :now, t.checkInGate = :gate, t.checkedInBy = :staff
            WHERE t.ticketCode = :code
              AND t.status = huce.fit.ezticket_backend.entity.enums.TicketStatus.VALID
            """)
    int checkIn(@Param("code") String code,
                @Param("now") LocalDateTime now,
                @Param("gate") String gate,
                @Param("staff") User staff);

    /**
     * Variant without staff link (staff email unknown). Keeps the same atomic guarantee.
     */
    @Modifying
    @Query("UPDATE Ticket t SET t.status = huce.fit.ezticket_backend.entity.enums.TicketStatus.USED, "
            + "t.checkedInAt = :now, t.checkInGate = :gate "
            + "WHERE t.ticketCode = :code "
            + "AND t.status = huce.fit.ezticket_backend.entity.enums.TicketStatus.VALID")
    int checkInWithoutStaff(@Param("code") String code,
                            @Param("now") LocalDateTime now,
                            @Param("gate") String gate);
}
