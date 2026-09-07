package com.example.pressdistribution;

import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.seed.enabled=false",
    "app.bootstrap-admin.email=",
    "app.bootstrap-admin.full-name=",
    "app.bootstrap-admin.password="
})
class RecordServiceRollbackTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @MockitoBean(enforceOverride = true)
    AuditLogRepository auditLogRepository;

    @Autowired
    private ParishIssueRecordRepository recordRepository;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private Parish parish1;
    private Issue issue1;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@rollback-test.com").authorities(
            new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@rollback-test.com").authorities(
            new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        given(auditLogRepository.save(any()))
            .willThrow(new DataIntegrityViolationException("simulated audit failure"));

        parish1 = new Parish();
        parish1.setLocality("Rollback Locality");
        parish1.setName("Rollback Parish");
        parish1 = parishRepository.save(parish1);

        Publication publication1 = new Publication();
        publication1.setName("Rollback Publication");
        publication1 = publicationRepository.save(publication1);

        issue1 = new Issue();
        issue1.setPublication(publication1);
        issue1.setIssueNumber("RB-001");
        issue1.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue1.setUnitPrice(BigDecimal.valueOf(2.50));
        issue1 = issueRepository.save(issue1);

        User admin = new User();
        admin.setFullName("Rollback Admin");
        admin.setEmail("admin@rollback-test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCD1234EFGH5678"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("Rollback Priest");
        priest.setEmail("priest@rollback-test.com");
        priest.setPasswordHash(passwordEncoder.encode("Password123!"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("WXYZ9876ABCD1234"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(parish1);
        priest.setActive(true);
        userRepository.save(priest);
    }

    @AfterEach
    void tearDown() {
        recordRepository.deleteAll();
        issueRepository.deleteAll();
        publicationRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    @Test
    void adminRecordCreateRollsBackOnAuditFailure() throws Exception {
        long countBefore = recordRepository.count();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "10")
                .param("returnedCopies", "2")
                .param("paidAmount", "5.00"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // Record should NOT have been persisted — transaction was rolled back
        assertThat(recordRepository.count()).isEqualTo(countBefore);

        // Verify audit log save was attempted
        verify(auditLogRepository).save(any());
    }

    @Test
    void priestRecordCreateRollsBackOnAuditFailure() throws Exception {
        long countBefore = recordRepository.count();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/records/new")
                .with(PRIEST_USER)
                .with(csrf())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "10")
                .param("returnedCopies", "2"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // Record should NOT have been persisted — transaction was rolled back
        assertThat(recordRepository.count()).isEqualTo(countBefore);

        // Verify audit log save was attempted
        verify(auditLogRepository).save(any());
    }

    @Test
    void adminRecordUpdateRollsBackOnAuditFailure() throws Exception {
        // Create a record directly via repository (bypasses service, so no audit call)
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish1);
        record.setIssue(issue1);
        record.setDeliveredCopies(10);
        record.setReturnedCopies(2);
        record.setPaidAmount(BigDecimal.valueOf(5.00));
        record = recordRepository.save(record);

        Long recordId = record.getId();
        int originalDelivered = record.getDeliveredCopies();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/records/" + recordId + "/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "99")
                .param("returnedCopies", "2")
                .param("paidAmount", "5.00"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // Record's delivered copies should remain unchanged — transaction was rolled back
        ParishIssueRecord reloaded = recordRepository.findById(recordId).orElseThrow();
        assertThat(reloaded.getDeliveredCopies()).isEqualTo(originalDelivered);

        // Verify audit log save was attempted
        verify(auditLogRepository).save(any());
    }

    @Test
    void priestRecordUpdateRollsBackOnAuditFailure() throws Exception {
        // Create a record directly via repository (bypasses service, so no audit call)
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish1);
        record.setIssue(issue1);
        record.setDeliveredCopies(10);
        record.setReturnedCopies(2);
        record.setPaidAmount(BigDecimal.valueOf(5.00));
        record = recordRepository.save(record);

        Long recordId = record.getId();
        int originalDelivered = record.getDeliveredCopies();

        assertThatThrownBy(() ->
            mockMvc.perform(post("/records/" + recordId + "/edit")
                .with(PRIEST_USER)
                .with(csrf())
                .param("deliveredCopies", "99")
                .param("returnedCopies", "2"))
        ).hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        // Record's delivered copies should remain unchanged — transaction was rolled back
        ParishIssueRecord reloaded = recordRepository.findById(recordId).orElseThrow();
        assertThat(reloaded.getDeliveredCopies()).isEqualTo(originalDelivered);

        // Verify audit log save was attempted
        verify(auditLogRepository).save(any());
    }
}
