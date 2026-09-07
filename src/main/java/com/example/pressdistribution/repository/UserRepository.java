package com.example.pressdistribution.repository;

import com.example.pressdistribution.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "parish")
    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailWithParish(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @EntityGraph(attributePaths = "parish")
    @Query("SELECT u FROM User u ORDER BY LOWER(u.fullName) ASC")
    Page<User> findAllOrderByFullNameIgnoreCase(Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'ADMINISTRATOR' AND u.isActive = true")
    long countActiveAdministrators();
}
