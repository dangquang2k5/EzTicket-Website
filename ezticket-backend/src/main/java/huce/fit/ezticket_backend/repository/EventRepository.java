package huce.fit.ezticket_backend.repository;

import huce.fit.ezticket_backend.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    boolean existsBySlug(String slug);
}
