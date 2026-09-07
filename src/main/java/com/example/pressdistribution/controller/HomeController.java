package com.example.pressdistribution.controller;

import com.example.pressdistribution.model.User;
import com.example.pressdistribution.model.UserRole;
import com.example.pressdistribution.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final UserRepository userRepository;

    public HomeController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/")
    public String home(Model model, @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findByEmail(principal.getUsername())
            .orElseThrow(() -> new RuntimeException("User not found"));

        model.addAttribute("fullName", user.getFullName());
        model.addAttribute("displayRole",
            user.getRole() == UserRole.ADMINISTRATOR ? "Administrator" : "Parish Priest");
        model.addAttribute("isAdministrator", user.getRole() == UserRole.ADMINISTRATOR);

        return "home";
    }
}
