package com.example.pressdistribution.repository;

import com.example.pressdistribution.model.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PublicationRepository extends JpaRepository<Publication, Long> {

    Optional<Publication> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT p FROM Publication p ORDER BY LOWER(p.name)")
    List<Publication> findAllSortedByName();

    /**
     * Returns all publications with their latest issue publication_date.
     * Uses a LEFT JOIN subquery to find the most recent issue per publication
     * (ordered by created_at DESC, id DESC) and fetches its publication_date.
     * Returns Object[] rows: [Publication.id, Publication.name, latestIssueDate].
     */
    @Query("""
            SELECT p.id, p.name, i.publicationDate
            FROM Publication p
            LEFT JOIN Issue i ON i.publication = p
                AND i.createdAt = (
                    SELECT MAX(i2.createdAt) FROM Issue i2 WHERE i2.publication = p
                )
                AND i.id = (
                    SELECT MAX(i3.id) FROM Issue i3 WHERE i3.publication = p AND i3.createdAt = (
                        SELECT MAX(i4.createdAt) FROM Issue i4 WHERE i4.publication = p
                    )
                )
            ORDER BY LOWER(p.name)
            """)
    List<Object[]> findAllWithLatestIssueDate();
}
