package com.example.pressdistribution.repository;

import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query("SELECT i FROM Issue i JOIN FETCH i.publication WHERE i.id = :id")
    Optional<Issue> findByIdWithPublication(Long id);

    Optional<Issue> findByPublicationAndIssueNumber(Publication publication, String issueNumber);

    boolean existsByPublication(Publication publication);

    boolean existsByPublicationAndIssueNumberIgnoreCase(Publication publication, String issueNumber);

    boolean existsByPublicationAndIssueNumberIgnoreCaseAndIdNot(Publication publication, String issueNumber, Long id);

    Optional<Issue> findTopByPublicationOrderByCreatedAtDescIdDesc(Publication publication);

    @Query("SELECT i FROM Issue i JOIN FETCH i.publication ORDER BY LOWER(i.publication.name), i.publicationDate DESC, LOWER(i.issueNumber)")
    List<Issue> findAllSortedForList();

    @Query("SELECT i FROM Issue i JOIN FETCH i.publication WHERE i.publication.id = :publicationId ORDER BY i.publicationDate DESC, LOWER(i.issueNumber)")
    List<Issue> findAllByPublicationIdSorted(@org.springframework.data.repository.query.Param("publicationId") Long publicationId);
}
