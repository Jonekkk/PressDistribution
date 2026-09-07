package com.example.pressdistribution;

import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
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
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class BulkEntryRollbackTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @MockitoBean(enforceOverride = true)
    AuditLogRepository auditLogRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Parish parish1;
    private Parish parish2;
    private Issue issue;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user("admin@bulk-rollback-test.com").authorities(
            new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    @BeforeEach
    void setUp() {
        given(auditLogRepository.save(any()))
            .willThrow(new DataIntegrityViolationException("simulated audit failure"));

        // Create parishes
        parish1 = new Parish();
        parish1.setLocality("Rollback Locality A");
        parish1.setName("Rollback Parish 1");
        parish1 = parishRepository.save(parish1);

        parish2 = new Parish();
        parish2.setLocality("Rollback Locality B");
        parish2.setName("Rollback Parish 2");
        parish2 = parishRepository.save(parish2);

        // Create publication and issue
        Publication publication = new Publication();
        publication.setName("Rollback Test Publication");
        publication = publicationRepository.save(publication);

        Issue newIssue = new Issue();
        newIssue.setPublication(publication);
        newIssue.setIssueNumber("RB 01");
        newIssue.setPublicationDate(LocalDate.of(2024, 6, 1));
        newIssue.setUnitPrice(new BigDecimal("2.50"));
        issue = issueRepository.save(newIssue);

        // Create admin user
        User admin = new User();
        admin.setFullName("Bulk Rollback Admin");
        admin.setEmail("admin@bulk-rollback-test.com");
        admin.setPasswordHash(passwordEncoder.encode("Password123!"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCD1234EFGH5678"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);
    }

    @AfterEach
    void tearDown() {
        parishIssueRecordRepository.deleteAll();
        issueRepository.deleteAll();
        publicationRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    @Test
    void bulkSaveRollsBack_whenAuditLogFails() throws Exception {
        long recordCountBefore = parishIssueRecordRepository.count();

        mockMvc.perform(post("/admin/records/bulk")
                .with(ADMIN_USER)
                .with(csrf())
                .param("issueId", issue.getId().toString())
                .param("rows[0].parishId", parish1.getId().toString())
                .param("rows[0].deliveredCopies", "10")
                .param("rows[0].returnedCopies", "2")
                .param("rows[0].paidAmount", "5.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("The operation could not be completed. Please try again.")));

        // No records created - transaction rolled back
        assertThat(parishIssueRecordRepository.count()).isEqualTo(recordCountBefore);

        // Audit log save was attempted
        verify(auditLogRepository).save(any());
    }
}
