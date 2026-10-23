package org.example.letstry.controller;

import org.example.letstry.model.Hall;
import org.example.letstry.model.Localization;
import org.example.letstry.service.BuildingService;
import org.example.letstry.service.HallService;
import org.example.letstry.service.LocalizationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Optional;

@Controller
public class HallController {

    private final HallService hallService;
    private final LocalizationService localizationService;
    private final BuildingService buildingService;

    public HallController(
            HallService hallService,
            LocalizationService localizationService,
            BuildingService buildingService
    ) {
        this.hallService = hallService;
        this.localizationService = localizationService;
        this.buildingService = buildingService;
    }

    @GetMapping("/localization")
    public String chooseHallLocalization() {
        return "hall/localization";
    }

    @GetMapping("/other-localization")
    public String getLocalization(Model model) {
        model.addAttribute("localizations", buildingService.getBuildings());
        return "hall/other-localization";
    }

    @GetMapping("/here")
    public String chooseHallHere() {
        return "redirect:/other-localization";
    }

    @GetMapping("/city")
    public String showCities(Model model) {
        model.addAttribute("localizations", localizationService.getLocalizations());
        return "hall/city";
    }

    @GetMapping("/city/{id}")
    public String showCityBuildings(@PathVariable Long id, Model model) {
        List<Localization> localizations = buildingService.getBuildings().stream()
                .filter(localization -> localization.getId() == id)
                .toList();

        model.addAttribute("localizations", localizations);
        return "hall/other-localization";
    }

    @GetMapping("/building/{id}")
    public String showBuildingHalls(@PathVariable Long id, Model model) {
        model.addAttribute("halls", hallService.getBuildingHalls(id));
        return "hall/building";
    }

    @GetMapping("/calendar/{hallId}")
    public String showCalendar(@PathVariable Long hallId, Model model) {
        Optional<Hall> hall = hallService.findHallById(hallId);

        if (hall.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Sala nie istnieje."
            );
        }

        model.addAttribute("hall", hall.get());
        return "hall/calendar";
    }
}