package com.example.pressdistribution;

import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.seed.enabled=true")
@Testcontainers
@ActiveProfiles("test")
class SeedEnabledStartupTest {

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

    @Test
    void whenSeedEnabled_allTablesContainExpectedRowCounts() {
        assertThat(parishRepository.count()).isEqualTo(3);
        assertThat(publicationRepository.count()).isEqualTo(2);
        assertThat(issueRepository.count()).isEqualTo(5);
        assertThat(parishIssueRecordRepository.count()).isEqualTo(7);
    }

    @Test
    void whenSeedEnabled_parishesMatchSeedData() {
        Optional<Parish> greendale = parishRepository.findByLocalityAndName("Greendale", "St. Emerald Parish");
        assertThat(greendale).isPresent();
        assertThat(greendale.get().getAddress()).isEqualTo("14 Willow Lane, Greendale");

        Optional<Parish> silverford = parishRepository.findByLocalityAndName("Silverford", "Holy Cross Chapel");
        assertThat(silverford).isPresent();
        assertThat(silverford.get().getAddress()).isEqualTo("7 Bridge Street, Silverford");

        Optional<Parish> maplewood = parishRepository.findByLocalityAndName("Maplewood", "Our Lady of the Fields");
        assertThat(maplewood).isPresent();
        assertThat(maplewood.get().getAddress()).isEqualTo("22 Oak Avenue, Maplewood");
    }

    @Test
    void whenSeedEnabled_publicationsMatchSeedData() {
        Optional<Publication> valleyHerald = publicationRepository.findByName("The Valley Herald");
        assertThat(valleyHerald).isPresent();

        Optional<Publication> communityLighthouse = publicationRepository.findByName("Community Lighthouse");
        assertThat(communityLighthouse).isPresent();
    }

    @Test
    void whenSeedEnabled_issuesMatchSeedData() {
        Publication valleyHerald = publicationRepository.findByName("The Valley Herald").orElseThrow();
        Publication communityLighthouse = publicationRepository.findByName("Community Lighthouse").orElseThrow();

        Optional<Issue> vh2024_01 = issueRepository.findByPublicationAndIssueNumber(valleyHerald, "2024/01");
        assertThat(vh2024_01).isPresent();
        assertThat(vh2024_01.get().getPublicationDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(vh2024_01.get().getUnitPrice()).isEqualByComparingTo(new BigDecimal("2.50"));

        Optional<Issue> vh2024_02 = issueRepository.findByPublicationAndIssueNumber(valleyHerald, "2024/02");
        assertThat(vh2024_02).isPresent();
        assertThat(vh2024_02.get().getPublicationDate()).isEqualTo(LocalDate.of(2024, 2, 15));
        assertThat(vh2024_02.get().getUnitPrice()).isEqualByComparingTo(new BigDecimal("2.50"));

        Optional<Issue> vh2024_03 = issueRepository.findByPublicationAndIssueNumber(valleyHerald, "2024/03");
        assertThat(vh2024_03).isPresent();
        assertThat(vh2024_03.get().getPublicationDate()).isEqualTo(LocalDate.of(2024, 3, 15));
        assertThat(vh2024_03.get().getUnitPrice()).isEqualByComparingTo(new BigDecimal("2.75"));

        Optional<Issue> cl_vol5no1 = issueRepository.findByPublicationAndIssueNumber(communityLighthouse, "Vol 5 No 1");
        assertThat(cl_vol5no1).isPresent();
        assertThat(cl_vol5no1.get().getPublicationDate()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(cl_vol5no1.get().getUnitPrice()).isEqualByComparingTo(new BigDecimal("3.00"));

        Optional<Issue> cl_vol5no2 = issueRepository.findByPublicationAndIssueNumber(communityLighthouse, "Vol 5 No 2");
        assertThat(cl_vol5no2).isPresent();
        assertThat(cl_vol5no2.get().getPublicationDate()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(cl_vol5no2.get().getUnitPrice()).isEqualByComparingTo(new BigDecimal("3.00"));
    }

    @Test
    void whenSeedEnabled_parishIssueRecordsMatchSeedData() {
        Parish greendale = parishRepository.findByLocalityAndName("Greendale", "St. Emerald Parish").orElseThrow();
        Parish silverford = parishRepository.findByLocalityAndName("Silverford", "Holy Cross Chapel").orElseThrow();
        Publication valleyHerald = publicationRepository.findByName("The Valley Herald").orElseThrow();
        Issue vh2024_01 = issueRepository.findByPublicationAndIssueNumber(valleyHerald, "2024/01").orElseThrow();

        Optional<ParishIssueRecord> record = parishIssueRecordRepository.findByParishAndIssue(greendale, vh2024_01);
        assertThat(record).isPresent();
        assertThat(record.get().getDeliveredCopies()).isEqualTo(50);
        assertThat(record.get().getReturnedCopies()).isEqualTo(5);
        assertThat(record.get().getPaidAmount()).isEqualByComparingTo(new BigDecimal("90.00"));

        Optional<ParishIssueRecord> silverfordRecord = parishIssueRecordRepository.findByParishAndIssue(silverford, vh2024_01);
        assertThat(silverfordRecord).isPresent();
        assertThat(silverfordRecord.get().getDeliveredCopies()).isEqualTo(40);
        assertThat(silverfordRecord.get().getReturnedCopies()).isEqualTo(8);
        assertThat(silverfordRecord.get().getPaidAmount()).isEqualByComparingTo(new BigDecimal("64.00"));
    }

    @Test
    void whenSeedEnabled_allParishIssueRecordsHaveValidReferences() {
        List<ParishIssueRecord> allRecords = parishIssueRecordRepository.findAll();
        assertThat(allRecords).hasSize(7);

        for (ParishIssueRecord record : allRecords) {
            // Verify parish reference is valid
            assertThat(record.getParish()).isNotNull();
            assertThat(record.getParish().getId()).isNotNull();
            Optional<Parish> parish = parishRepository.findById(record.getParish().getId());
            assertThat(parish).isPresent();

            // Verify issue reference is valid
            assertThat(record.getIssue()).isNotNull();
            assertThat(record.getIssue().getId()).isNotNull();
            Optional<Issue> issue = issueRepository.findById(record.getIssue().getId());
            assertThat(issue).isPresent();
        }
    }
}
