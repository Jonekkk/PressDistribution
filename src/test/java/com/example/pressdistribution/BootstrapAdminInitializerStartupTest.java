package com.example.pressdistribution;

import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.bootstrap-admin.email=startup-test@example.com",
    "app.bootstrap-admin.full-name=Startup Test Admin",
    "app.bootstrap-admin.password=startupPass123"
})
class BootstrapAdminInitializerStartupTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private UserRepository userRepository;

    @Test
    void initializerInvokesServiceDuringStartup() {
        Optional<User> admin = userRepository.findByEmail("startup-test@example.com");
        assertThat(admin).isPresent();
        assertThat(admin.get().getFullName()).isEqualTo("Startup Test Admin");
        assertThat(admin.get().getRole()).isEqualTo(UserRole.ADMINISTRATOR);
        assertThat(admin.get().isActive()).isTrue();
    }
}
