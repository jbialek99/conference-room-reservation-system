package org.example.letstry.controller;
import org.example.letstry.service.HallService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
public class MenuController {

    private final HallService hallService;

    public MenuController(HallService hallService) {
        this.hallService = hallService;
    }

    @GetMapping("/welcome")
    public String welcome() {
        return "/menu/welcome";
    }

    @GetMapping("/home")
    public String home(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof OAuth2User user) {
            model.addAttribute("email", user.getAttribute("email"));
            model.addAttribute("name", user.getAttribute("name"));
        }

        hallService.findByEmail("sala1@bucikbialekgmail.onmicrosoft.com").ifPresent(hall ->
                model.addAttribute("defaultHallId", hall.getId()));

        return "/menu/home";
    }


    @GetMapping("/calendar")
    public String redirectToDefaultHallCalendar() {
        return hallService.findByEmail("sala1@bucikbialekgmail.onmicrosoft.com")
                .map(hall -> "redirect:/calendar/" + hall.getId())
                .orElse("redirect:/error");
    }
}
