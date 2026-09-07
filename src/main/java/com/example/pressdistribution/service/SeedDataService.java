package com.example.pressdistribution.service;

import com.example.pressdistribution.config.seed.IssueSeedDto;
import com.example.pressdistribution.config.seed.ParishIssueRecordSeedDto;
import com.example.pressdistribution.config.seed.ParishSeedDto;
import com.example.pressdistribution.config.seed.PublicationSeedDto;
import com.example.pressdistribution.model.Issue;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.ParishIssueRecord;
import com.example.pressdistribution.model.Publication;
import com.example.pressdistribution.repository.IssueRepository;
import com.example.pressdistribution.repository.ParishIssueRecordRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.PublicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SeedDataService {

    private static final Logger log = LoggerFactory.getLogger(SeedDataService.class);

    private final ParishRepository parishRepository;
    private final PublicationRepository publicationRepository;
    private final IssueRepository issueRepository;
    private final ParishIssueRecordRepository parishIssueRecordRepository;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;

    public SeedDataService(ParishRepository parishRepository,
                           PublicationRepository publicationRepository,
                           IssueRepository issueRepository,
                           ParishIssueRecordRepository parishIssueRecordRepository,
                           ResourceLoader resourceLoader) {
        this.parishRepository = parishRepository;
        this.publicationRepository = publicationRepository;
        this.issueRepository = issueRepository;
        this.parishIssueRecordRepository = parishIssueRecordRepository;
        this.resourceLoader = resourceLoader;
        this.objectMapper = JsonMapper.builder().build();
    }

    @Transactional
    public void loadSeedData() {
        log.info("Starting seed data loading...");

        List<ParishSeedDto> parishes = loadFromClasspath("seed/parishes.json", ParishSeedDto[].class);
        List<PublicationSeedDto> publications = loadFromClasspath("seed/publications.json", PublicationSeedDto[].class);
        List<IssueSeedDto> issues = loadFromClasspath("seed/issues.json", IssueSeedDto[].class);
        List<ParishIssueRecordSeedDto> records = loadFromClasspath("seed/parish-issue-records.json", ParishIssueRecordSeedDto[].class);

        validateParishes(parishes);
        validatePublications(publications);
        validateIssues(issues);
        validateParishIssueRecords(records);

        loadParishes(parishes);
        loadPublications(publications);
        loadIssues(issues);
        loadParishIssueRecords(records);

        log.info("Seed data loading completed successfully.");
    }

    // --- Validation methods ---

    private void validateParishes(List<ParishSeedDto> parishes) {
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < parishes.size(); i++) {
            ParishSeedDto dto = parishes.get(i);
            String context = "parishes.json[" + i + "]";

            validateRequiredString(dto.locality(), "locality", 150, context);
            validateRequiredString(dto.name(), "name", 255, context);

            if (dto.address() != null && dto.address().length() > 500) {
                throw new IllegalStateException(context + ": 'address' exceeds maximum length of 500 characters");
            }

            String naturalKey = dto.locality() + "|" + dto.name();
            if (!seenKeys.add(naturalKey)) {
                throw new IllegalStateException(context + ": duplicate parish (locality='" + dto.locality()
                        + "', name='" + dto.name() + "') within parishes.json");
            }
        }
    }

    private void validatePublications(List<PublicationSeedDto> publications) {
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < publications.size(); i++) {
            PublicationSeedDto dto = publications.get(i);
            String context = "publications.json[" + i + "]";

            validateRequiredString(dto.name(), "name", 255, context);

            if (!seenKeys.add(dto.name())) {
                throw new IllegalStateException(context + ": duplicate publication name '" + dto.name()
                        + "' within publications.json");
            }
        }
    }

    private void validateIssues(List<IssueSeedDto> issues) {
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < issues.size(); i++) {
            IssueSeedDto dto = issues.get(i);
            String context = "issues.json[" + i + "]";

            validateRequiredString(dto.publicationName(), "publicationName", 255, context);
            validateRequiredString(dto.issueNumber(), "issueNumber", 100, context);

            if (dto.publicationDate() == null) {
                throw new IllegalStateException(context + ": 'publicationDate' is required but was null");
            }

            if (dto.unitPrice() == null) {
                throw new IllegalStateException(context + ": 'unitPrice' is required but was null");
            }
            validateNonNegativeDecimal(dto.unitPrice(), "unitPrice", context);
            validateMaxTwoDecimalPlaces(dto.unitPrice(), "unitPrice", context);

            String naturalKey = dto.publicationName() + "|" + dto.issueNumber();
            if (!seenKeys.add(naturalKey)) {
                throw new IllegalStateException(context + ": duplicate issue (publicationName='" + dto.publicationName()
                        + "', issueNumber='" + dto.issueNumber() + "') within issues.json");
            }
        }
    }

    private void validateParishIssueRecords(List<ParishIssueRecordSeedDto> records) {
        Set<String> seenKeys = new HashSet<>();
        for (int i = 0; i < records.size(); i++) {
            ParishIssueRecordSeedDto dto = records.get(i);
            String context = "parish-issue-records.json[" + i + "]";

            validateRequiredString(dto.parishLocality(), "parishLocality", 150, context);
            validateRequiredString(dto.parishName(), "parishName", 255, context);
            validateRequiredString(dto.publicationName(), "publicationName", 255, context);
            validateRequiredString(dto.issueNumber(), "issueNumber", 100, context);

            if (dto.deliveredCopies() < 0) {
                throw new IllegalStateException(context + ": 'deliveredCopies' must be non-negative, was " + dto.deliveredCopies());
            }
            if (dto.returnedCopies() < 0) {
                throw new IllegalStateException(context + ": 'returnedCopies' must be non-negative, was " + dto.returnedCopies());
            }
            if (dto.returnedCopies() > dto.deliveredCopies()) {
                throw new IllegalStateException(context + ": 'returnedCopies' (" + dto.returnedCopies()
                        + ") must not exceed 'deliveredCopies' (" + dto.deliveredCopies() + ")");
            }

            if (dto.paidAmount() == null) {
                throw new IllegalStateException(context + ": 'paidAmount' is required but was null");
            }
            validateNonNegativeDecimal(dto.paidAmount(), "paidAmount", context);
            validateMaxTwoDecimalPlaces(dto.paidAmount(), "paidAmount", context);

            String naturalKey = dto.parishLocality() + "|" + dto.parishName() + "|" + dto.publicationName() + "|" + dto.issueNumber();
            if (!seenKeys.add(naturalKey)) {
                throw new IllegalStateException(context + ": duplicate parish issue record (parishLocality='"
                        + dto.parishLocality() + "', parishName='" + dto.parishName()
                        + "', publicationName='" + dto.publicationName()
                        + "', issueNumber='" + dto.issueNumber() + "') within parish-issue-records.json");
            }
        }
    }

    private void validateRequiredString(String value, String fieldName, int maxLength, String context) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(context + ": '" + fieldName + "' is required and must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalStateException(context + ": '" + fieldName + "' exceeds maximum length of " + maxLength
                    + " characters (was " + value.length() + ")");
        }
    }

    private void validateNonNegativeDecimal(BigDecimal value, String fieldName, String context) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(context + ": '" + fieldName + "' must be non-negative, was " + value);
        }
    }

    private void validateMaxTwoDecimalPlaces(BigDecimal value, String fieldName, String context) {
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalStateException(context + ": '" + fieldName + "' must have at most 2 decimal places, was " + value);
        }
    }

    // --- Loading methods ---

    private void loadParishes(List<ParishSeedDto> parishes) {
        for (ParishSeedDto dto : parishes) {
            if (parishRepository.findByLocalityAndName(dto.locality(), dto.name()).isEmpty()) {
                Parish parish = new Parish();
                parish.setLocality(dto.locality());
                parish.setName(dto.name());
                parish.setAddress(dto.address());
                parishRepository.save(parish);
                log.debug("Seeded parish: {} / {}", dto.locality(), dto.name());
            }
        }
    }

    private void loadPublications(List<PublicationSeedDto> publications) {
        for (PublicationSeedDto dto : publications) {
            if (publicationRepository.findByName(dto.name()).isEmpty()) {
                Publication publication = new Publication();
                publication.setName(dto.name());
                publicationRepository.save(publication);
                log.debug("Seeded publication: {}", dto.name());
            }
        }
    }

    private void loadIssues(List<IssueSeedDto> issues) {
        for (IssueSeedDto dto : issues) {
            Publication publication = publicationRepository.findByName(dto.publicationName())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve publicationName '" + dto.publicationName()
                                    + "' for issue '" + dto.issueNumber() + "' in issues.json"));

            if (issueRepository.findByPublicationAndIssueNumber(publication, dto.issueNumber()).isEmpty()) {
                Issue issue = new Issue();
                issue.setPublication(publication);
                issue.setIssueNumber(dto.issueNumber());
                issue.setPublicationDate(dto.publicationDate());
                issue.setUnitPrice(dto.unitPrice());
                issueRepository.save(issue);
                log.debug("Seeded issue: {} / {}", dto.publicationName(), dto.issueNumber());
            }
        }
    }

    private void loadParishIssueRecords(List<ParishIssueRecordSeedDto> records) {
        for (ParishIssueRecordSeedDto dto : records) {
            Parish parish = parishRepository.findByLocalityAndName(dto.parishLocality(), dto.parishName())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve parish (locality='" + dto.parishLocality()
                                    + "', name='" + dto.parishName()
                                    + "') in parish-issue-records.json"));

            Publication publication = publicationRepository.findByName(dto.publicationName())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve publicationName '" + dto.publicationName()
                                    + "' in parish-issue-records.json"));

            Issue issue = issueRepository.findByPublicationAndIssueNumber(publication, dto.issueNumber())
                    .orElseThrow(() -> new IllegalStateException(
                            "Cannot resolve issue (publicationName='" + dto.publicationName()
                                    + "', issueNumber='" + dto.issueNumber()
                                    + "') in parish-issue-records.json"));

            if (parishIssueRecordRepository.findByParishAndIssue(parish, issue).isEmpty()) {
                ParishIssueRecord record = new ParishIssueRecord();
                record.setParish(parish);
                record.setIssue(issue);
                record.setDeliveredCopies(dto.deliveredCopies());
                record.setReturnedCopies(dto.returnedCopies());
                record.setPaidAmount(dto.paidAmount());
                parishIssueRecordRepository.save(record);
                log.debug("Seeded parish issue record: {} / {} - {} / {}",
                        dto.parishLocality(), dto.parishName(), dto.publicationName(), dto.issueNumber());
            }
        }
    }

    // --- Resource loading ---

    <T> List<T> loadFromClasspath(String path, Class<T[]> type) {
        Resource resource = resourceLoader.getResource("classpath:" + path);
        try (InputStream inputStream = resource.getInputStream()) {
            T[] array = objectMapper.readValue(inputStream, type);
            return Arrays.asList(array);
        } catch (JacksonException e) {
            throw new RuntimeException("Failed to parse seed data from " + path + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load seed data from " + path + ": " + e.getMessage(), e);
        }
    }
}
