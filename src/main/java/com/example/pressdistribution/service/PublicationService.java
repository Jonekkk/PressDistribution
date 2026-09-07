package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.PublicationFormDto;
import com.example.pressdistribution.dto.PublicationListItemDto;
import com.example.pressdistribution.exception.PublicationHasIssuesException;
import com.example.pressdistribution.exception.PublicationNotFoundException;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class PublicationService {

    private final PublicationRepository publicationRepository;
    private final IssueRepository issueRepository;
    private final AuditLogRepository auditLogRepository;

    public PublicationService(PublicationRepository publicationRepository,
                              IssueRepository issueRepository,
                              AuditLogRepository auditLogRepository) {
        this.publicationRepository = publicationRepository;
        this.issueRepository = issueRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<Publication> findAllSorted() {
        return publicationRepository.findAllSortedByName();
    }

    @Transactional(readOnly = true)
    public List<PublicationListItemDto> findAllWithLatestIssueDate() {
        return publicationRepository.findAllWithLatestIssueDate().stream()
                .map(row -> new PublicationListItemDto(
                        (Long) row[0],
                        (String) row[1],
                        (LocalDate) row[2]
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public Publication findById(Long id) {
        return publicationRepository.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return publicationRepository.existsById(id);
    }

    @Transactional
    public Publication createPublication(PublicationFormDto dto) {
        Publication publication = new Publication();
        publication.setName(dto.getName());
        Publication saved = publicationRepository.save(publication);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Publication created: " + saved.getName());
        auditLogRepository.save(auditLog);

        return saved;
    }

    @Transactional
    public Publication updatePublication(Long id, PublicationFormDto dto) {
        Publication publication = publicationRepository.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));

        publication.setName(dto.getName());
        Publication updated = publicationRepository.save(publication);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Publication updated: " + updated.getName());
        auditLogRepository.save(auditLog);

        return updated;
    }

    @Transactional
    public void deletePublication(Long id) {
        Publication publication = publicationRepository.findById(id)
                .orElseThrow(() -> new PublicationNotFoundException(id));

        if (issueRepository.existsByPublication(publication)) {
            throw new PublicationHasIssuesException(id);
        }

        publicationRepository.delete(publication);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Publication deleted: " + publication.getName());
        auditLogRepository.save(auditLog);
    }

    public boolean isDuplicateForCreate(String name) {
        return publicationRepository.existsByNameIgnoreCase(name);
    }

    public boolean isDuplicateForUpdate(Long id, String name) {
        return publicationRepository.existsByNameIgnoreCaseAndIdNot(name, id);
    }
}
