package com.example.pressdistribution;

import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.service.SeedDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "app.seed.enabled=false")
@Testcontainers
@ActiveProfiles("test")
class SeedFailureIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        jdbcTemplate.execute("TRUNCATE TABLE parish_issue_records");
        jdbcTemplate.execute("TRUNCATE TABLE issues");
        jdbcTemplate.execute("TRUNCATE TABLE publications");
        jdbcTemplate.execute("TRUNCATE TABLE parishes");
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
    }

    /**
     * Creates a SeedDataService with a custom ResourceLoader that remaps
     * "classpath:seed/" paths to a test-specific directory.
     */
    private SeedDataService createServiceWithResourceDir(String testResourceDir) {
        ResourceLoader customLoader = new DefaultResourceLoader() {
            @Override
            public Resource getResource(String location) {
                String remapped = location.replace("classpath:seed/", "classpath:" + testResourceDir + "/");
                return super.getResource(remapped);
            }
        };
        return new SeedDataService(
                parishRepository,
                publicationRepository,
                issueRepository,
                parishIssueRecordRepository,
                customLoader
        );
    }

    @Test
    void testMissingSeedFile_throwsExceptionAndNoRecordsCreated() {
        // Use a resource directory that does not exist
        SeedDataService service = createServiceWithResourceDir("seed-nonexistent");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> service.loadSeedData()))
                .isInstanceOf(RuntimeException.class);

        // Verify no records were created
        assertThat(parishRepository.count()).isZero();
        assertThat(publicationRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();
        assertThat(parishIssueRecordRepository.count()).isZero();
    }

    @Test
    void testMalformedJson_throwsExceptionAndNoRecordsRemain() {
        // seed-malformed/parishes.json contains invalid JSON
        SeedDataService service = createServiceWithResourceDir("seed-malformed");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> service.loadSeedData()))
                .isInstanceOf(RuntimeException.class);

        // Verify no records remain from the failed execution
        assertThat(parishRepository.count()).isZero();
        assertThat(publicationRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();
        assertThat(parishIssueRecordRepository.count()).isZero();
    }

    @Test
    void testReturnedCopiesExceedsDeliveredCopies_validationFailsAndRollsBack() {
        // seed-invalid-returned has returnedCopies (15) > deliveredCopies (10)
        SeedDataService service = createServiceWithResourceDir("seed-invalid-returned");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> service.loadSeedData()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("returnedCopies");

        // Verify transaction was rolled back — no records from the failed execution remain
        assertThat(parishRepository.count()).isZero();
        assertThat(publicationRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();
        assertThat(parishIssueRecordRepository.count()).isZero();
    }

    @Test
    void testUnresolvableNaturalReference_rollsBackAndNoPartialRecordsRemain() {
        // seed-unresolvable/issues.json references "Non Existent Publication"
        SeedDataService service = createServiceWithResourceDir("seed-unresolvable");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> service.loadSeedData()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot resolve publicationName");

        // Verify no partial records remain — parishes loaded before the failure must be rolled back
        assertThat(parishRepository.count()).isZero();
        assertThat(publicationRepository.count()).isZero();
        assertThat(issueRepository.count()).isZero();
        assertThat(parishIssueRecordRepository.count()).isZero();
    }

    @Test
    void testMalformedJson_preExistingRecordsUnchanged() {
        // Pre-seed a parish directly
        Parish existingParish = new Parish();
        existingParish.setLocality("PreExisting");
        existingParish.setName("Unchanged Parish");
        existingParish.setAddress("Original Address");
        parishRepository.save(existingParish);

        Publication existingPub = new Publication();
        existingPub.setName("PreExisting Pub");
        publicationRepository.save(existingPub);

        long parishCountBefore = parishRepository.count();
        long pubCountBefore = publicationRepository.count();

        // Attempt to load malformed seed data — should fail
        SeedDataService service = createServiceWithResourceDir("seed-malformed");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> service.loadSeedData()))
                .isInstanceOf(RuntimeException.class);

        // Verify pre-existing records are unchanged
        assertThat(parishRepository.count()).isEqualTo(parishCountBefore);
        assertThat(publicationRepository.count()).isEqualTo(pubCountBefore);

        Parish reloaded = parishRepository.findByLocalityAndName("PreExisting", "Unchanged Parish")
                .orElseThrow();
        assertThat(reloaded.getAddress()).isEqualTo("Original Address");
    }

    @Test
    void testUnresolvableReference_preExistingRecordsUnchanged() {
        // Pre-seed a parish directly
        Parish existingParish = new Parish();
        existingParish.setLocality("PreExisting");
        existingParish.setName("Stable Parish");
        existingParish.setAddress("Stable Address");
        parishRepository.save(existingParish);

        long parishCountBefore = parishRepository.count();

        // Attempt to load seed data with unresolvable reference — should fail
        SeedDataService service = createServiceWithResourceDir("seed-unresolvable");
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(status -> service.loadSeedData()))
                .isInstanceOf(IllegalStateException.class);

        // Verify pre-existing records are unchanged
        assertThat(parishRepository.count()).isEqualTo(parishCountBefore);

        Parish reloaded = parishRepository.findByLocalityAndName("PreExisting", "Stable Parish")
                .orElseThrow();
        assertThat(reloaded.getAddress()).isEqualTo("Stable Address");
    }
}
