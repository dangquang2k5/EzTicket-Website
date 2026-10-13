package huce.fit.ezticket_backend.repository;

import huce.fit.ezticket_backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsBySlug(String slug);

    boolean existsByName(String name);
}
