package com.example.pressdistribution.repository;

import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ParishIssueRecordRepository extends JpaRepository<ParishIssueRecord, Long> {

    Optional<ParishIssueRecord> findByParishAndIssue(Parish parish, Issue issue);

    boolean existsByIssue(Issue issue);

    // Record list queries
    @Query("SELECT r FROM ParishIssueRecord r " +
           "JOIN FETCH r.parish JOIN FETCH r.issue i JOIN FETCH i.publication " +
           "ORDER BY LOWER(r.parish.locality), LOWER(r.parish.name), " +
           "LOWER(i.publication.name), i.publicationDate DESC, LOWER(i.issueNumber)")
    List<ParishIssueRecord> findAllWithDetailsOrdered();

    @Query("SELECT r FROM ParishIssueRecord r " +
           "JOIN FETCH r.issue i JOIN FETCH i.publication " +
           "WHERE r.parish.id = :parishId " +
           "ORDER BY LOWER(i.publication.name), i.publicationDate DESC, LOWER(i.issueNumber)")
    List<ParishIssueRecord> findByParishIdWithDetailsOrdered(@Param("parishId") Long parishId);

    // Duplicate check methods
    boolean existsByParishIdAndIssueId(Long parishId, Long issueId);

    boolean existsByParishIdAndIssueIdAndIdNot(Long parishId, Long issueId, Long id);

    // --- Publication report (paginated, with @EntityGraph) ---
    @EntityGraph(attributePaths = {"parish", "issue", "issue.publication"})
    @Query("SELECT r FROM ParishIssueRecord r " +
           "WHERE (:dateFrom IS NULL OR r.issue.publicationDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR r.issue.publicationDate <= :dateTo) " +
           "AND (:publicationId IS NULL OR r.issue.publication.id = :publicationId) " +
           "AND (:issueId IS NULL OR r.issue.id = :issueId) " +
           "AND (:parishId IS NULL OR r.parish.id = :parishId)")
    Page<ParishIssueRecord> findPublicationReport(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("publicationId") Long publicationId,
            @Param("issueId") Long issueId,
            @Param("parishId") Long parishId,
            Pageable pageable);

    // --- Publication report (all rows for CSV export, with JOIN FETCH) ---
    @Query("SELECT r FROM ParishIssueRecord r " +
           "JOIN FETCH r.parish p JOIN FETCH r.issue i JOIN FETCH i.publication pub " +
           "WHERE (:dateFrom IS NULL OR i.publicationDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR i.publicationDate <= :dateTo) " +
           "AND (:publicationId IS NULL OR pub.id = :publicationId) " +
           "AND (:issueId IS NULL OR i.id = :issueId) " +
           "AND (:parishId IS NULL OR p.id = :parishId) " +
           "ORDER BY LOWER(pub.name), i.publicationDate DESC, LOWER(i.issueNumber), " +
           "LOWER(p.locality), LOWER(p.name)")
    List<ParishIssueRecord> findPublicationReportAll(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("publicationId") Long publicationId,
            @Param("issueId") Long issueId,
            @Param("parishId") Long parishId);

    // --- Parish report (paginated, with @EntityGraph) ---
    @EntityGraph(attributePaths = {"issue", "issue.publication"})
    @Query("SELECT r FROM ParishIssueRecord r " +
           "WHERE r.parish.id = :parishId " +
           "AND (:dateFrom IS NULL OR r.issue.publicationDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR r.issue.publicationDate <= :dateTo)")
    Page<ParishIssueRecord> findParishReport(
            @Param("parishId") Long parishId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    // --- Parish report (all rows for CSV export, with JOIN FETCH) ---
    @Query("SELECT r FROM ParishIssueRecord r " +
           "JOIN FETCH r.issue i JOIN FETCH i.publication pub " +
           "WHERE r.parish.id = :parishId " +
           "AND (:dateFrom IS NULL OR i.publicationDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR i.publicationDate <= :dateTo) " +
           "ORDER BY i.publicationDate DESC, LOWER(pub.name), LOWER(i.issueNumber)")
    List<ParishIssueRecord> findParishReportAll(
            @Param("parishId") Long parishId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    // --- Single record with all associations eagerly fetched (for edit form) ---
    @Query("SELECT r FROM ParishIssueRecord r JOIN FETCH r.parish JOIN FETCH r.issue i JOIN FETCH i.publication WHERE r.id = :id")
    Optional<ParishIssueRecord> findByIdWithDetails(@Param("id") Long id);

    // --- Filtered record list (paginated) ---
    @EntityGraph(attributePaths = {"parish", "issue", "issue.publication"})
    @Query("SELECT r FROM ParishIssueRecord r " +
           "WHERE (:publicationId IS NULL OR r.issue.publication.id = :publicationId) " +
           "AND (:issueId IS NULL OR r.issue.id = :issueId)")
    Page<ParishIssueRecord> findAllFiltered(
            @Param("publicationId") Long publicationId,
            @Param("issueId") Long issueId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"issue", "issue.publication"})
    @Query("SELECT r FROM ParishIssueRecord r " +
           "WHERE r.parish.id = :parishId " +
           "AND (:publicationId IS NULL OR r.issue.publication.id = :publicationId) " +
           "AND (:issueId IS NULL OR r.issue.id = :issueId)")
    Page<ParishIssueRecord> findByParishIdFiltered(
            @Param("parishId") Long parishId,
            @Param("publicationId") Long publicationId,
            @Param("issueId") Long issueId,
            Pageable pageable);

    // --- Bulk entry: all records for a given issue with parish eagerly fetched ---
    @Query("SELECT r FROM ParishIssueRecord r JOIN FETCH r.parish WHERE r.issue.id = :issueId")
    List<ParishIssueRecord> findByIssueIdWithParish(@Param("issueId") Long issueId);
}
