package org.example.letstry.controller;

import org.example.letstry.model.Hall;
import org.example.letstry.service.HallService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Comparator;

@Controller
public class MenuController {

    private final HallService hallService;

    public MenuController(HallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping("/welcome")
    public String welcome() {
        return "menu/welcome";
    }

    @GetMapping("/home")
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            model.addAttribute("email", user.getAttribute("email"));
            model.addAttribute("name", user.getAttribute("name"));
        }

        return "menu/home";
    }

    @GetMapping("/calendar")
    public String redirectToDefaultHallCalendar() {
        return hallService.getAllHalls().stream()
                .filter(Hall::isAvailable)
                .min(Comparator.comparingLong(Hall::getId))
                .map(hall -> "redirect:/calendar/" + hall.getId())
                .orElse("redirect:/localization");
    }
}