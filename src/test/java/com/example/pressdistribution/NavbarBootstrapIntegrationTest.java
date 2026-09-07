package com.example.pressdistribution;

import com.example.pressdistribution.model.Parish;
import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.ParishRepository;
import com.example.pressdistribution.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bug Condition Exploration Test: Bootstrap JS Bundle Missing From Rendered Pages.
 *
 * <p>This test asserts the EXPECTED BEHAVIOR: every rendered page must include
 * bootstrap.bundle.min.js before {@code </body>}. On unfixed code these tests
 * will FAIL, confirming the bug exists. After the fix they will PASS.
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 2.1, 2.2</b>
 */
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
class NavbarBootstrapIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ParishRepository parishRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String ADMIN_EMAIL = "admin@bootstrap-test.com";
    private static final String PRIEST_EMAIL = "priest@bootstrap-test.com";

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor ADMIN_USER =
        user(ADMIN_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_ADMINISTRATOR"));

    private static final SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor PRIEST_USER =
        user(PRIEST_EMAIL).authorities(new SimpleGrantedAuthority("ROLE_PARISH_PRIEST"));

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        parishRepository.deleteAll();

        Parish parish = new Parish();
        parish.setLocality("Bootstrap Test Locality");
        parish.setName("Bootstrap Test Parish");
        parish = parishRepository.save(parish);

        User admin = new User();
        admin.setFullName("Bootstrap Admin");
        admin.setEmail(ADMIN_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("password123"));
        admin.setRecoveryCodeHash(passwordEncoder.encode("ABCDEFGHIJKLMNOP"));
        admin.setRole(UserRole.ADMINISTRATOR);
        admin.setParish(null);
        admin.setActive(true);
        userRepository.save(admin);

        User priest = new User();
        priest.setFullName("Bootstrap Priest");
        priest.setEmail(PRIEST_EMAIL);
        priest.setPasswordHash(passwordEncoder.encode("password123"));
        priest.setRecoveryCodeHash(passwordEncoder.encode("QRSTUVWXYZABCDEF"));
        priest.setRole(UserRole.PARISH_PRIEST);
        priest.setParish(parish);
        priest.setActive(true);
        userRepository.save(priest);
    }

    @Test
    @DisplayName("Bug condition: Admin home page must include bootstrap.bundle.min.js")
    void adminHomePageIncludesBootstrapJs() throws Exception {
        mockMvc.perform(get("/").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("bootstrap.bundle.min.js")));
    }

    @Test
    @DisplayName("Bug condition: Admin publications page must include bootstrap.bundle.min.js")
    void adminPublicationsPageIncludesBootstrapJs() throws Exception {
        mockMvc.perform(get("/admin/publications").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("bootstrap.bundle.min.js")));
    }

    @Test
    @DisplayName("Bug condition: Priest my-parish report page must include bootstrap.bundle.min.js")
    void priestMyParishReportIncludesBootstrapJs() throws Exception {
        mockMvc.perform(get("/reports/my-parish").with(PRIEST_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("bootstrap.bundle.min.js")));
    }

    @Test
    @DisplayName("Bug condition: Login page must include bootstrap.bundle.min.js")
    void loginPageIncludesBootstrapJs() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("bootstrap.bundle.min.js")));
    }

    // ===== Preservation Property Tests =====
    // These tests verify existing behavior that must remain unchanged after the fix.
    // Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5

    @Test
    @DisplayName("Preservation: Admin sees Publications, Parishes, Users, Parish report nav links")
    void adminNavLinksPresent() throws Exception {
        mockMvc.perform(get("/").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(">Publications</a>")))
            .andExpect(content().string(containsString(">Parishes</a>")))
            .andExpect(content().string(containsString(">Users</a>")))
            .andExpect(content().string(containsString(">Parish report</a>")));
    }

    @Test
    @DisplayName("Preservation: Priest sees Parish report link but NOT Publications, Parishes, Users")
    void priestNavLinksCorrect() throws Exception {
        mockMvc.perform(get("/").with(PRIEST_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(">Parish report</a>")))
            .andExpect(content().string(not(containsString("/admin/publications"))))
            .andExpect(content().string(not(containsString("/admin/parishes"))))
            .andExpect(content().string(not(containsString("/admin/users"))));
    }

    @Test
    @DisplayName("Preservation: Navbar toggler has correct data-bs and aria attributes")
    void navbarTogglerAccessibility() throws Exception {
        mockMvc.perform(get("/").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("data-bs-toggle=\"collapse\"")))
            .andExpect(content().string(containsString("data-bs-target=\"#navbarNav\"")))
            .andExpect(content().string(containsString("aria-controls=\"navbarNav\"")))
            .andExpect(content().string(containsString("aria-expanded=\"false\"")))
            .andExpect(content().string(containsString("aria-label=\"Toggle navigation\"")));
    }

    @Test
    @DisplayName("Preservation: Pages load Bootstrap CSS 5.3.3")
    void bootstrapCssVersionConsistent() throws Exception {
        mockMvc.perform(get("/").with(ADMIN_USER))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(
                "bootstrap@5.3.3/dist/css/bootstrap.min.css")));
    }
}
