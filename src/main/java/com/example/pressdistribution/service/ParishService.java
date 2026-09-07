package com.example.pressdistribution.service;

import com.example.pressdistribution.dto.ParishFormDto;
import com.example.pressdistribution.exception.ParishNotFoundException;
import com.example.pressdistribution.model.AuditLog;
import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.repository.AuditLogRepository;
import com.example.pressdistribution.repository.ParishRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ParishService {

    private final ParishRepository parishRepository;
    private final AuditLogRepository auditLogRepository;

    public ParishService(ParishRepository parishRepository, AuditLogRepository auditLogRepository) {
        this.parishRepository = parishRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public List<Parish> findAllSorted() {
        return parishRepository.findAllSortedByLocalityAndName();
    }

    @Transactional(readOnly = true)
    public Parish findById(Long id) {
        return parishRepository.findById(id)
                .orElseThrow(() -> new ParishNotFoundException(id));
    }

    @Transactional
    public Parish createParish(ParishFormDto dto) {
        Parish parish = new Parish();
        parish.setLocality(dto.getLocality());
        parish.setName(dto.getName());
        parish.setAddress(dto.getAddress());
        Parish saved = parishRepository.save(parish);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Parish created: " + saved.getLocality() + " - " + saved.getName());
        auditLogRepository.save(auditLog);

        return saved;
    }

    @Transactional
    public Parish updateParish(Long id, ParishFormDto dto) {
        Parish parish = parishRepository.findById(id)
                .orElseThrow(() -> new ParishNotFoundException(id));

        parish.setLocality(dto.getLocality());
        parish.setName(dto.getName());
        parish.setAddress(dto.getAddress());
        Parish updated = parishRepository.save(parish);

        AuditLog auditLog = new AuditLog();
        auditLog.setMessage("Parish updated: " + updated.getLocality() + " - " + updated.getName());
        auditLogRepository.save(auditLog);

        return updated;
    }

    public boolean isDuplicateForCreate(String locality, String name) {
        return parishRepository.existsByLocalityIgnoreCaseAndNameIgnoreCase(locality, name);
    }

    public boolean isDuplicateForUpdate(Long id, String locality, String name) {
        return parishRepository.existsByLocalityIgnoreCaseAndNameIgnoreCaseAndIdNot(locality, name, id);
    }
}
