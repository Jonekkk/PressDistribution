package com.example.pressdistribution.repository;

import com.example.pressdistribution.model.Parish;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ParishRepository extends JpaRepository<Parish, Long> {

    Optional<Parish> findByLocalityAndName(String locality, String name);

    boolean existsByLocalityIgnoreCaseAndNameIgnoreCase(String locality, String name);

    boolean existsByLocalityIgnoreCaseAndNameIgnoreCaseAndIdNot(String locality, String name, Long id);

    @Query("SELECT p FROM Parish p ORDER BY LOWER(p.locality), LOWER(p.name)")
    List<Parish> findAllSortedByLocalityAndName();
}
