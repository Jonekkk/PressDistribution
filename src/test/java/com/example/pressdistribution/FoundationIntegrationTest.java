package com.example.pressdistribution;

import com.example.pressdistribution.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class FoundationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PublicationRepository publicationRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private ParishIssueRecordRepository parishIssueRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void contextLoads() {
        assertThat(parishRepository).isNotNull();
        assertThat(userRepository).isNotNull();
        assertThat(publicationRepository).isNotNull();
        assertThat(issueRepository).isNotNull();
        assertThat(parishIssueRecordRepository).isNotNull();
        assertThat(auditLogRepository).isNotNull();
    }
}
