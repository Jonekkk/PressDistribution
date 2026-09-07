package com.example.pressdistribution;

import com.example.pressdistribution.dto.ParishFormDto;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.service.ParishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.seed.enabled=false",
        "app.bootstrap-admin.email=",
        "app.bootstrap-admin.full-name=",
        "app.bootstrap-admin.password="
})
class ParishServiceRollbackTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private ParishService parishServiceWithFailingAudit;

    @BeforeEach
    void setUp() {
        parishRepository.deleteAll();
        auditLogRepository.deleteAll();

        // Create an AuditLogRepository wrapper that delegates all reads
        // but throws on save() to simulate audit persistence failure
        AuditLogRepository failingAuditLogRepository = new AuditLogRepository() {
            @Override
            public <S extends AuditLog> S save(S entity) {
                throw new DataIntegrityViolationException("simulated audit failure");
            }

            @Override
            public <S extends AuditLog> List<S> saveAll(Iterable<S> entities) {
                throw new DataIntegrityViolationException("simulated audit failure");
            }

            @Override
            public Optional<AuditLog> findById(Long id) {
                return auditLogRepository.findById(id);
            }

            @Override
            public boolean existsById(Long id) {
                return auditLogRepository.existsById(id);
            }

            @Override
            public List<AuditLog> findAll() {
                return auditLogRepository.findAll();
            }

            @Override
            public List<AuditLog> findAllById(Iterable<Long> ids) {
                return auditLogRepository.findAllById(ids);
            }

            @Override
            public long count() {
                return auditLogRepository.count();
            }

            @Override
            public void deleteById(Long id) {
                auditLogRepository.deleteById(id);
            }

            @Override
            public void delete(AuditLog entity) {
                auditLogRepository.delete(entity);
            }

            @Override
            public void deleteAllById(Iterable<? extends Long> ids) {
                auditLogRepository.deleteAllById(ids);
            }

            @Override
            public void deleteAll(Iterable<? extends AuditLog> entities) {
                auditLogRepository.deleteAll(entities);
            }

            @Override
            public void deleteAll() {
                auditLogRepository.deleteAll();
            }

            @Override
            public void flush() {
                auditLogRepository.flush();
            }

            @Override
            public <S extends AuditLog> S saveAndFlush(S entity) {
                throw new DataIntegrityViolationException("simulated audit failure");
            }

            @Override
            public <S extends AuditLog> List<S> saveAllAndFlush(Iterable<S> entities) {
                throw new DataIntegrityViolationException("simulated audit failure");
            }

            @Override
            public void deleteAllInBatch(Iterable<AuditLog> entities) {
                auditLogRepository.deleteAllInBatch(entities);
            }

            @Override
            public void deleteAllByIdInBatch(Iterable<Long> ids) {
                auditLogRepository.deleteAllByIdInBatch(ids);
            }

            @Override
            public void deleteAllInBatch() {
                auditLogRepository.deleteAllInBatch();
            }

            @Override
            public AuditLog getOne(Long id) {
                return auditLogRepository.getOne(id);
            }

            @Override
            public AuditLog getById(Long id) {
                return auditLogRepository.getById(id);
            }

            @Override
            public AuditLog getReferenceById(Long id) {
                return auditLogRepository.getReferenceById(id);
            }

            @Override
            public <S extends AuditLog> Optional<S> findOne(Example<S> example) {
                return auditLogRepository.findOne(example);
            }

            @Override
            public <S extends AuditLog> List<S> findAll(Example<S> example) {
                return auditLogRepository.findAll(example);
            }

            @Override
            public <S extends AuditLog> List<S> findAll(Example<S> example, Sort sort) {
                return auditLogRepository.findAll(example, sort);
            }

            @Override
            public <S extends AuditLog> Page<S> findAll(Example<S> example, Pageable pageable) {
                return auditLogRepository.findAll(example, pageable);
            }

            @Override
            public <S extends AuditLog> long count(Example<S> example) {
                return auditLogRepository.count(example);
            }

            @Override
            public <S extends AuditLog> boolean exists(Example<S> example) {
                return auditLogRepository.exists(example);
            }

            @Override
            public <S extends AuditLog, R> R findBy(Example<S> example,
                                                     Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) {
                return auditLogRepository.findBy(example, queryFunction);
            }

            @Override
            public List<AuditLog> findAll(Sort sort) {
                return auditLogRepository.findAll(sort);
            }

            @Override
            public Page<AuditLog> findAll(Pageable pageable) {
                return auditLogRepository.findAll(pageable);
            }
        };

        parishServiceWithFailingAudit = new ParishService(parishRepository, failingAuditLogRepository);
    }

    @Test
    void createParish_rollsBackWhenAuditLogFails() {
        long countBefore = parishRepository.count();

        ParishFormDto dto = new ParishFormDto();
        dto.setLocality("Test Locality");
        dto.setName("Test Parish");
        dto.setAddress("Test Address");

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(
                status -> parishServiceWithFailingAudit.createParish(dto)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(parishRepository.count()).isEqualTo(countBefore);
    }

    @Test
    void updateParish_rollsBackWhenAuditLogFails() {
        // Pre-create a parish directly via repository
        Parish existing = new Parish();
        existing.setLocality("Original Locality");
        existing.setName("Original Name");
        existing.setAddress("Original Address");
        existing = parishRepository.saveAndFlush(existing);

        Long parishId = existing.getId();

        ParishFormDto dto = new ParishFormDto();
        dto.setLocality("Updated Locality");
        dto.setName("Updated Name");
        dto.setAddress("Updated Address");

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);

        assertThatThrownBy(() -> txTemplate.executeWithoutResult(
                status -> parishServiceWithFailingAudit.updateParish(parishId, dto)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Reload and verify values are unchanged
        Parish reloaded = parishRepository.findById(parishId).orElseThrow();
        assertThat(reloaded.getLocality()).isEqualTo("Original Locality");
        assertThat(reloaded.getName()).isEqualTo("Original Name");
        assertThat(reloaded.getAddress()).isEqualTo("Original Address");
    }
}
