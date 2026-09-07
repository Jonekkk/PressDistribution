package com.example.pressdistribution;

import com.example.pressdistribution.model.AuditLog;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
class RecordManagementIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Parish parish1;
    private Parish parish2;
    private Publication publication1;
    private Publication publication2;
    private Issue issue1;
    private Issue issue2;

    private static final String ADMIN_EMAIL = "admin@record-test.com";
    private static final String PRIEST_EMAIL = "priest@record-test.com";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user(ADMIN_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user(PRIEST_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        // Create parishes
        parish1 = new Parish();
        parish1.setLocality("Test Locality");
        parish1.setName("Parish One");
        parish1 = parishRepository.save(parish1);

        parish2 = new Parish();
        parish2.setLocality("Other Locality");
        parish2.setName("Parish Two");
        parish2 = parishRepository.save(parish2);

        // Create publications
        publication1 = new Publication();
        publication1.setName("Test Publication");
        publication1 = publicationRepository.save(publication1);

        publication2 = new Publication();
        publication2.setName("Another Publication");
        publication2 = publicationRepository.save(publication2);

        // Create issues
        issue1 = new Issue();
        issue1.setPublication(publication1);
        issue1.setIssueNumber("2024/01");
        issue1.setPublicationDate(LocalDate.of(2024, 1, 15));
        issue1.setUnitPrice(new BigDecimal("2.50"));
        issue1 = issueRepository.save(issue1);

        issue2 = new Issue();
        issue2.setPublication(publication2);
        issue2.setIssueNumber("2024/02");
        issue2.setPublicationDate(LocalDate.of(2024, 2, 20));
        issue2.setUnitPrice(new BigDecimal("3.00"));
        issue2 = issueRepository.save(issue2);

        // Create admin user
        User admin = new User();
        admin.setFullName("Test Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCD-EFGH-IJKL-MNOP"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        // Create priest user assigned to parish1
        User priest = new User();
        priest.setFullName("Test Priest");
        priest.setEmail(PRIEST_EMAIL);
        priest.setPasswordHash(passwordEncoder.encode("password123"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("QRST-UVWX-YZAB-CDEF"));
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
        auditLogRepository.deleteAll();
        userRepository.deleteAll();
        parishRepository.deleteAll();
    }

    // ---- Test 1: Admin sees all records including paid amount ----

    @Test
    void adminSeesAllRecordsIncludingPaidAmount() throws Exception {
        createRecord(parish1, issue1, 100, 20, new BigDecimal("50.00"));
        createRecord(parish2, issue2, 200, 30, new BigDecimal("75.00"));

        mockMvc.perform(get("/records").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Paid amount")))
            .andExpect(content().string(containsString("Test Locality")))
            .andExpect(content().string(containsString("Parish One")))
            .andExpect(content().string(containsString("Other Locality")))
            .andExpect(content().string(containsString("Parish Two")))
            .andExpect(content().string(containsString("50.00")))
            .andExpect(content().string(containsString("75.00")));
    }

    // ---- Test 2: Priest sees only own parish records without paid amount ----

    @Test
    void priestSeesOnlyOwnParishRecordsWithoutPaidAmount() throws Exception {
        createRecord(parish1, issue1, 100, 20, new BigDecimal("50.00"));
        createRecord(parish2, issue2, 200, 30, new BigDecimal("75.00"));

        mockMvc.perform(get("/records").with(PRIEST_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("Paid amount"))))
            .andExpect(content().string(containsString("Test Publication")))
            .andExpect(content().string(not(containsString("Other Locality"))))
            .andExpect(content().string(not(containsString("Parish Two"))))
            .andExpect(content().string(not(containsString("50.00"))))
            .andExpect(content().string(not(containsString("75.00"))));
    }

    // ---- Test 3: Priest cannot access other parish's record ----

    @Test
    void priestCannotAccessOtherParishRecord() throws Exception {
        ParishIssueRecord otherRecord = createRecord(parish2, issue2, 100, 10, new BigDecimal("30.00"));

        mockMvc.perform(get("/records/" + otherRecord.getId() + "/edit").with(PRIEST_USER))
            .andExpect(status().isForbidden());
    }

    // ---- Test 4: Unauthenticated redirects to login ----

    @Test
    void unauthenticatedRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/records"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    // ---- Test 5: CSRF enforcement ----

    @Test
    void csrfEnforcement() throws Exception {
        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "100")
                .param("returnedCopies", "10")
                .param("paidAmount", "50.00"))
            .andExpect(status().isForbidden());
    }

    // ---- Test 6: Admin creates record successfully ----

    @Test
    void adminCreatesRecordSuccessfully() throws Exception {
        long recordCountBefore = recordRepository.count();
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "100")
                .param("returnedCopies", "10")
                .param("paidAmount", "50.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/records"))
            .andExpect(flash().attribute("successMessage", "Record created successfully"));

        assertThat(recordRepository.count()).isEqualTo(recordCountBefore + 1);
        assertThat(auditLogRepository.count()).isGreaterThan(auditCountBefore);

        ParishIssueRecord saved = recordRepository.findAll().getFirst();
        assertThat(saved.getParish().getId()).isEqualTo(parish1.getId());
        assertThat(saved.getIssue().getId()).isEqualTo(issue1.getId());
        assertThat(saved.getDeliveredCopies()).isEqualTo(100);
        assertThat(saved.getReturnedCopies()).isEqualTo(10);
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ---- Test 7: Priest creates record with paidAmount zero ----

    @Test
    void priestCreatesRecordWithPaidAmountZero() throws Exception {
        mockMvc.perform(post("/records/new")
                .with(PRIEST_USER)
                .with(csrf())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "50")
                .param("returnedCopies", "5"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/records"));

        ParishIssueRecord saved = recordRepository.findAll().getFirst();
        assertThat(saved.getParish().getId()).isEqualTo(parish1.getId());
        assertThat(saved.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- Test 8: Duplicate parish+issue rejected ----

    @Test
    void duplicateParishIssueRejected() throws Exception {
        createRecord(parish1, issue1, 100, 10, new BigDecimal("50.00"));

        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "200")
                .param("returnedCopies", "20")
                .param("paidAmount", "60.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("already exists")));

        assertThat(recordRepository.count()).isEqualTo(1);
    }

    // ---- Test 9: Returned exceeds delivered rejected ----

    @Test
    void returnedExceedsDeliveredRejected() throws Exception {
        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "10")
                .param("returnedCopies", "20")
                .param("paidAmount", "5.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("must not exceed")));

        assertThat(recordRepository.count()).isEqualTo(0);
    }

    // ---- Test 10: Negative values rejected ----

    @Test
    void negativeValuesRejected() throws Exception {
        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "-1")
                .param("returnedCopies", "0")
                .param("paidAmount", "5.00"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("at least 0")));

        assertThat(recordRepository.count()).isEqualTo(0);
    }

    // ---- Test 11: Admin edits all fields ----

    @Test
    void adminEditsAllFields() throws Exception {
        ParishIssueRecord record = createRecord(parish1, issue1, 100, 10, new BigDecimal("50.00"));

        mockMvc.perform(post("/records/" + record.getId() + "/edit")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish2.getId().toString())
                .param("issueId", issue2.getId().toString())
                .param("deliveredCopies", "200")
                .param("returnedCopies", "30")
                .param("paidAmount", "100.00"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/records"));

        ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
        assertThat(updated.getParish().getId()).isEqualTo(parish2.getId());
        assertThat(updated.getIssue().getId()).isEqualTo(issue2.getId());
        assertThat(updated.getDeliveredCopies()).isEqualTo(200);
        assertThat(updated.getReturnedCopies()).isEqualTo(30);
        assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ---- Test 12: Priest edits only delivered and returned ----

    @Test
    void priestEditsOnlyDeliveredAndReturned() throws Exception {
        ParishIssueRecord record = createRecord(parish1, issue1, 100, 10, new BigDecimal("50.00"));

        mockMvc.perform(post("/records/" + record.getId() + "/edit")
                .with(PRIEST_USER)
                .with(csrf())
                .param("deliveredCopies", "150")
                .param("returnedCopies", "25"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/records"));

        ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
        assertThat(updated.getDeliveredCopies()).isEqualTo(150);
        assertThat(updated.getReturnedCopies()).isEqualTo(25);
        // Parish, issue, and paidAmount must be unchanged
        assertThat(updated.getParish().getId()).isEqualTo(parish1.getId());
        assertThat(updated.getIssue().getId()).isEqualTo(issue1.getId());
        assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ---- Test 13: Priest edit preserves existing paid amount ----

    @Test
    void priestEditPreservesExistingPaidAmount() throws Exception {
        ParishIssueRecord record = createRecord(parish1, issue1, 100, 10, new BigDecimal("50.00"));

        mockMvc.perform(post("/records/" + record.getId() + "/edit")
                .with(PRIEST_USER)
                .with(csrf())
                .param("deliveredCopies", "120")
                .param("returnedCopies", "15"))
            .andExpect(status().is3xxRedirection());

        ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
        assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ---- Test 14: Sold copies calculation correct ----

    @Test
    void soldCopiesCalculationCorrect() throws Exception {
        createRecord(parish1, issue1, 100, 20, new BigDecimal("10.00"));

        // sold = 100 - 20 = 80
        mockMvc.perform(get("/records").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(">80<")));
    }

    // ---- Test 15: Amount due calculation correct ----

    @Test
    void amountDueCalculationCorrect() throws Exception {
        // issue1 has unitPrice = 2.50
        // delivered=100, returned=20 => sold=80, amountDue=80*2.50=200.00
        createRecord(parish1, issue1, 100, 20, new BigDecimal("10.00"));

        mockMvc.perform(get("/records").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("200.00")));
    }

    // ---- Test 16: Not found returns 404 ----

    @Test
    void notFoundReturns404() throws Exception {
        mockMvc.perform(get("/records/99999/edit").with(ADMIN_USER))
            .andExpect(status().isNotFound());
    }

    // ---- Test 17: Audit log created on success ----

    @Test
    void auditLogCreatedOnSuccess() throws Exception {
        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "50")
                .param("returnedCopies", "5")
                .param("paidAmount", "10.00"))
            .andExpect(status().is3xxRedirection());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isNotEmpty();
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("created") &&
            log.getMessage().contains("Test Locality") &&
            log.getMessage().contains("Parish One") &&
            log.getMessage().contains("Test Publication"));
    }

    // ---- Test 18: No audit log on validation failure ----

    @Test
    void noAuditLogOnValidationFailure() throws Exception {
        long auditCountBefore = auditLogRepository.count();

        mockMvc.perform(post("/records/new")
                .with(ADMIN_USER)
                .with(csrf())
                .param("parishId", parish1.getId().toString())
                .param("issueId", issue1.getId().toString())
                .param("deliveredCopies", "-1")
                .param("returnedCopies", "0")
                .param("paidAmount", "5.00"))
            .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(auditCountBefore);
    }

    // ---- Test 19: Priest cannot alter paidAmount via crafted params ----

    @Test
    void priestCannotAlterPaidAmountViaCraftedParams() throws Exception {
        ParishIssueRecord record = createRecord(parish1, issue1, 100, 10, new BigDecimal("50.00"));

        // Priest tries to submit paidAmount=100 via crafted params
        mockMvc.perform(post("/records/" + record.getId() + "/edit")
                .with(PRIEST_USER)
                .with(csrf())
                .param("deliveredCopies", "110")
                .param("returnedCopies", "15")
                .param("paidAmount", "100.00"))
            .andExpect(status().is3xxRedirection());

        ParishIssueRecord updated = recordRepository.findById(record.getId()).orElseThrow();
        // paidAmount must remain unchanged
        assertThat(updated.getPaidAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    // ---- Helper Methods ----

    private ParishIssueRecord createRecord(Parish parish, Issue issue, int delivered, int returned, BigDecimal paidAmount) {
        ParishIssueRecord record = new ParishIssueRecord();
        record.setParish(parish);
        record.setIssue(issue);
        record.setDeliveredCopies(delivered);
        record.setReturnedCopies(returned);
        record.setPaidAmount(paidAmount);
        return recordRepository.save(record);
    }
}
