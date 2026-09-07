package com.example.pressdistribution;

import com.example.pressdistribution.dto.PublicationFormDto;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import com.example.pressdistribution.service.PublicationService;
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
class PublicationServiceRollbackTest {

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
    private PublicationService publicationService;

    @BeforeEach
    void setUp() {
        issueRepository.deleteAll();
        publicationRepository.deleteAll();
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new DataIntegrityViolationException("simulated audit failure"));
    }

    @Test
    void createPublication_rollsBackWhenAuditLogFails() {
        long countBefore = publicationRepository.count();

        PublicationFormDto dto = new PublicationFormDto();
        dto.setName("Test Publication");

        assertThatThrownBy(() -> publicationService.createPublication(dto))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(publicationRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void updatePublication_rollsBackWhenAuditLogFails() {
        Publication existing = new Publication();
        existing.setName("Original Name");
        existing = publicationRepository.saveAndFlush(existing);

        Long id = existing.getId();

        PublicationFormDto dto = new PublicationFormDto();
        dto.setName("Updated Name");

        assertThatThrownBy(() -> publicationService.updatePublication(id, dto))
                .isInstanceOf(DataIntegrityViolationException.class);

        Publication reloaded = publicationRepository.findById(id).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Original Name");
    }

    @Test
    void deletePublication_rollsBackWhenAuditLogFails() {
        Publication existing = new Publication();
        existing.setName("To Delete");
        existing = publicationRepository.saveAndFlush(existing);

        Long id = existing.getId();

        assertThatThrownBy(() -> publicationService.deletePublication(id))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(publicationRepository.findById(id)).isPresent();
    }
}
