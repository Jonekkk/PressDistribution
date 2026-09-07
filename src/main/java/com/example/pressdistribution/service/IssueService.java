package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.IssueFormDto;
import com.example.pressdistribution.exception.IssueHasRecordsException;
import com.example.pressdistribution.exception.IssueNotFoundException;
import com.example.pressdistribution.exception.PublicationNotFoundException;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final PublicationRepository publicationRepository;
    private final ParishIssueRecordRepository parishIssueRecordRepository;
    private final AuditLogRepository auditLogRepository;

    public IssueService(IssueRepository issueRepository,
                        PublicationRepository publicationRepository,
                        ParishIssueRecordRepository parishIssueRecordRepository,
                        AuditLogRepository auditLogRepository) {
        this.issueRepository = issueRepository;
        this.publicationRepository = publicationRepository;
        this.parishIssueRecordRepository = parishIssueRecordRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<Issue> findAllSorted() {
        return issueRepository.findAllSortedForList();
    }

    @Transactional(readOnly = true)
    public List<Issue> findAllByPublicationId(Long publicationId) {
        return issueRepository.findAllByPublicationIdSorted(publicationId);
    }

    @Transactional(readOnly = true)
    public Issue findById(Long id) {
        return issueRepository.findByIdWithPublication(id)
                .orElseThrow(() -> new IssueNotFoundException(id));
    }

    @Transactional
    public Issue createIssue(IssueFormDto dto) {
        Publication publication = publicationRepository.findById(dto.getPublicationId())
                .orElseThrow(() -> new PublicationNotFoundException(dto.getPublicationId()));

        Issue issue = new Issue();
        issue.setPublication(publication);
        issue.setIssueNumber(dto.getIssueNumber());
        issue.setPublicationDate(dto.getPublicationDate());
        issue.setUnitPrice(dto.getUnitPrice());
        Issue saved = issueRepository.save(issue);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Issue created: " + publication.getName() + " - " + saved.getIssueNumber());
        auditLogRepository.save(auditLog);

        return saved;
    }

    @Transactional
    public Issue updateIssue(Long id, IssueFormDto dto) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IssueNotFoundException(id));

        Publication publication = publicationRepository.findById(dto.getPublicationId())
                .orElseThrow(() -> new PublicationNotFoundException(dto.getPublicationId()));

        issue.setPublication(publication);
        issue.setIssueNumber(dto.getIssueNumber());
        issue.setPublicationDate(dto.getPublicationDate());
        issue.setUnitPrice(dto.getUnitPrice());
        Issue updated = issueRepository.save(issue);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Issue updated: " + publication.getName() + " - " + updated.getIssueNumber());
        auditLogRepository.save(auditLog);

        return updated;
    }

    @Transactional
    public void deleteIssue(Long id) {
        Issue issue = issueRepository.findById(id)
                .orElseThrow(() -> new IssueNotFoundException(id));

        if (parishIssueRecordRepository.existsByIssue(issue)) {
            throw new IssueHasRecordsException(id);
        }

        issueRepository.delete(issue);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Issue deleted: " + issue.getPublication().getName() + " - " + issue.getIssueNumber());
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public Optional<BigDecimal> suggestPrice(Long publicationId) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(publicationId));

        return issueRepository.findTopByPublicationOrderByCreatedAtDescIdDesc(publication)
                .map(Issue::getUnitPrice);
    }

    public boolean isDuplicateForCreate(Long publicationId, String issueNumber) {
        Optional<Publication> publication = publicationRepository.findById(publicationId);
        if (publication.isEmpty()) {
            return false;
        }
        return issueRepository.existsByPublicationAndIssueNumberIgnoreCase(publication.get(), issueNumber);
    }

    public boolean isDuplicateForUpdate(Long issueId, Long publicationId, String issueNumber) {
        Optional<Publication> publication = publicationRepository.findById(publicationId);
        if (publication.isEmpty()) {
            return false;
        }
        return issueRepository.existsByPublicationAndIssueNumberIgnoreCaseAndIdNot(publication.get(), issueNumber, issueId);
    }
}
