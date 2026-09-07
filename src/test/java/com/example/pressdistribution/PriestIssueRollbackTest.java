package com.example.pressdistribution;

import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
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
class PriestIssueRollbackTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @MockitoBean(enforceOverride = true)
    AuditLogRepository auditLogRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    private Publication testPublication;

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user("priest@rollback-test.com").authorities(
            new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        given(auditLogRepository.save(any()))
            .willThrow(new DataIntegrityViolationException("simulated audit failure"));

        Parish testParish = new Parish();
        testParish.setLocality("Rollback Locality");
        testParish.setName("Rollback Parish");
        testParish = parishRepository.save(testParish);

        User priest = new User();
        priest.setFullName("Rollback Priest");
        priest.setEmail("priest@rollback-test.com");
        priest.setPasswordHash(passwordEncoder.encode("Password123!"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("ABCD1234EFGH5678"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(testParish);
        priest.setActive(true);
        userRepository.save(priest);

        testPublication = new Publication();
        testPublication.setName("Rollback Publication");
        testPublication = publicationRepository.save(testPublication);
    }

    @AfterEach
    void tearDown() {
        issueRepository.deleteAll();
        userRepository.deleteAll();
        publicationRepository.deleteAll();
        parishRepository.deleteAll();
    }

    @Test
    void issueCreationRollsBack_whenAuditLogFails() throws Exception {
        long issueCountBefore = issueRepository.count();

        mockMvc.perform(post("/issues")
                .with(PRIEST_USER)
                .with(csrf())
                .param("publicationId", testPublication.getId().toString())
                .param("issueNumber", "2024/01")
                .param("publicationDate", "2024-01-15")
                .param("unitPrice", "3.50"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("The operation could not be completed. Please try again.")));

        // No issue created - transaction rolled back
        assertThat(issueRepository.count()).isEqualTo(issueCountBefore);

        // Audit log save was attempted
        verify(auditLogRepository).save(any());
    }
}
