package com.example.pressdistribution;

import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "app.bootstrap-admin.email=admin@test.com",
    "app.bootstrap-admin.full-name=Test Admin",
    "app.bootstrap-admin.password=testpass123"
})
class AuthenticationIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginWithValidCredentials_redirectsToHome() throws Exception {
        // POST login with bootstrap admin credentials
        MvcResult loginResult = mockMvc.perform(post("/login")
                .param("username", "admin@test.com")
                .param("password", "testpass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn();

        // Obtain the authenticated session
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Reuse session for GET / and verify content
        mockMvc.perform(get("/").session(session))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Test Admin")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Administrator")));
    }

    @Test
    void loginWithInactiveUser_isRejected() throws Exception {
        // Create an inactive user
        User inactiveUser = new User();
        inactiveUser.setFullName("Inactive User");
        inactiveUser.setEmail("inactive@test.com");
        inactiveUser.setPasswordHash(passwordEncoder.encode("password"));
        inactiveUser.setRecoveryCodeHash(passwordEncoder.encode("placeholder"));
        inactiveUser.setRole(UserRole.ADMINISTRATOR);
        inactiveUser.setActive(false);
        userRepository.save(inactiveUser);

        // Attempt login
        MvcResult result = mockMvc.perform(post("/login")
                .param("username", "inactive@test.com")
                .param("password", "password")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"))
            .andReturn();

        // Assert no authenticated session exists
        assertNoAuthenticatedSession(result);
    }

    @Test
    void loginWithBadEmail_isRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                .param("username", "nonexistent@test.com")
                .param("password", "anypassword")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"))
            .andReturn();

        // Assert no authenticated session exists
        assertNoAuthenticatedSession(result);
    }

    @Test
    void loginWithBadPassword_isRejected() throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                .param("username", "admin@test.com")
                .param("password", "wrongpassword")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"))
            .andReturn();

        // Assert no authenticated session exists
        assertNoAuthenticatedSession(result);
    }

    @Test
    void logout_invalidatesSessionAndRedirects() throws Exception {
        // First authenticate to get a session
        MvcResult loginResult = mockMvc.perform(post("/login")
                .param("username", "admin@test.com")
                .param("password", "testpass123")
                .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/"))
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // POST logout with the authenticated session
        mockMvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?logout"));

        // Subsequent GET / should redirect to login
        mockMvc.perform(get("/").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    @Test
    void unauthenticatedAccess_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }

    private void assertNoAuthenticatedSession(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            return; // No session means no authentication
        }
        // If session exists, verify it has no SecurityContext with authentication
        Object securityContext = session.getAttribute(
            HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(securityContext).isNull();
    }
}
