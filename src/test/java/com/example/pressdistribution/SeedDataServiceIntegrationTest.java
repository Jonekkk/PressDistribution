package com.example.pressdistribution;

import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.service.SeedDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.seed.enabled=false")
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SeedDataServiceIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private SeedDataService seedDataService;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Test
    void testIdempotentReRun() {
        // First run — load seed data
        seedDataService.loadSeedData();

        long parishCount = parishRepository.count();
        long publicationCount = publicationRepository.count();
        long issueCount = issueRepository.count();
        long recordCount = parishIssueRecordRepository.count();

        assertThat(parishCount).isGreaterThan(0);
        assertThat(publicationCount).isGreaterThan(0);
        assertThat(issueCount).isGreaterThan(0);
        assertThat(recordCount).isGreaterThan(0);

        // Second run — should be idempotent
        seedDataService.loadSeedData();

        assertThat(parishRepository.count()).isEqualTo(parishCount);
        assertThat(publicationRepository.count()).isEqualTo(publicationCount);
        assertThat(issueRepository.count()).isEqualTo(issueCount);
        assertThat(parishIssueRecordRepository.count()).isEqualTo(recordCount);
    }

    @Test
    void testNonDestructivePreservesModifiedValues() {
        // Load initial seed data
        seedDataService.loadSeedData();

        // Find the parish issue record for Greendale/St. Emerald Parish + The Valley Herald/2024/01
        Parish parish = parishRepository.findByLocalityAndName("Greendale", "St. Emerald Parish")
                .orElseThrow();
        Publication publication = publicationRepository.findByName("The Valley Herald")
                .orElseThrow();
        Issue issue = issueRepository.findByPublicationAndIssueNumber(publication, "2024/01")
                .orElseThrow();
        ParishIssueRecord record = parishIssueRecordRepository.findByParishAndIssue(parish, issue)
                .orElseThrow();

        // Verify original values from seed data
        assertThat(record.getDeliveredCopies()).isEqualTo(50);
        assertThat(record.getReturnedCopies()).isEqualTo(5);
        assertThat(record.getPaidAmount()).isEqualByComparingTo(new BigDecimal("90.00"));

        // Modify values
        record.setDeliveredCopies(100);
        record.setReturnedCopies(20);
        record.setPaidAmount(new BigDecimal("150.00"));
        parishIssueRecordRepository.save(record);

        // Run seed loader again — should NOT overwrite modified values
        seedDataService.loadSeedData();

        // Reload and verify modified values are preserved
        ParishIssueRecord reloaded = parishIssueRecordRepository.findByParishAndIssue(parish, issue)
                .orElseThrow();

        assertThat(reloaded.getDeliveredCopies()).isEqualTo(100);
        assertThat(reloaded.getReturnedCopies()).isEqualTo(20);
        assertThat(reloaded.getPaidAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
    }
}
