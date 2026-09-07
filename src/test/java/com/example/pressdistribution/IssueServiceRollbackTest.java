package com.example.pressdistribution;

import com.example.pressdistribution.dto.IssueFormDto;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.service.IssueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.seed.enabled=false",
    "app.bootstrap-admin.email=",
    "app.bootstrap-admin.full-name=",
    "app.bootstrap-admin.password="
})
class IssueServiceRollbackTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @MockitoBean(enforceOverride = true)
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Autowired
    private IssueService issueService;

    private Publication testPublication;

    @BeforeEach
    void setUp() {
        parishIssueRecordRepository.deleteAll();
        issueRepository.deleteAll();
        publicationRepository.deleteAll();

        testPublication = new Publication();
        testPublication.setName("Test Publication");
        testPublication = publicationRepository.saveAndFlush(testPublication);

        when(auditLogRepository.save(any(AuditLog.class)))
            .thenThrow(new DataIntegrityViolationException("simulated audit failure"));
    }

    @Test
    void createIssue_rollsBackWhenAuditLogFails() {
        long countBefore = issueRepository.count();

        IssueFormDto dto = new IssueFormDto();
        dto.setPublicationId(testPublication.getId());
        dto.setIssueNumber("Issue 1");
        dto.setPublicationDate(LocalDate.of(2024, 1, 15));
        dto.setUnitPrice(new BigDecimal("3.50"));

        assertThatThrownBy(() -> issueService.createIssue(dto))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(issueRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void updateIssue_rollsBackWhenAuditLogFails() {
        // Create an issue directly (bypassing service to avoid audit log call)
        Issue existing = new Issue();
        existing.setPublication(testPublication);
        existing.setIssueNumber("Original");
        existing.setPublicationDate(LocalDate.of(2024, 1, 1));
        existing.setUnitPrice(new BigDecimal("2.00"));
        existing = issueRepository.saveAndFlush(existing);

        Long id = existing.getId();

        IssueFormDto dto = new IssueFormDto();
        dto.setPublicationId(testPublication.getId());
        dto.setIssueNumber("Updated");
        dto.setPublicationDate(LocalDate.of(2024, 6, 1));
        dto.setUnitPrice(new BigDecimal("5.00"));

        assertThatThrownBy(() -> issueService.updateIssue(id, dto))
            .isInstanceOf(DataIntegrityViolationException.class);

        Issue reloaded = issueRepository.findById(id).orElseThrow();
        assertThat(reloaded.getIssueNumber()).isEqualTo("Original");
        assertThat(reloaded.getUnitPrice()).isEqualByComparingTo(new BigDecimal("2.00"));
    }

    @Test
    void deleteIssue_rollsBackWhenAuditLogFails() {
        Issue existing = new Issue();
        existing.setPublication(testPublication);
        existing.setIssueNumber("To Delete");
        existing.setPublicationDate(LocalDate.of(2024, 1, 1));
        existing.setUnitPrice(new BigDecimal("1.00"));
        existing = issueRepository.saveAndFlush(existing);

        Long id = existing.getId();

        assertThatThrownBy(() -> issueService.deleteIssue(id))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(issueRepository.findById(id)).isPresent();
    }
}
