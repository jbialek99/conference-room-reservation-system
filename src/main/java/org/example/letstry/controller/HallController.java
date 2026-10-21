package org.example.letstry.controller;

import org.example.letstry.model.Hall;
import org.example.letstry.model.Localization;
import org.example.letstry.service.BuildingService;
import org.example.letstry.service.HallService;
import org.example.letstry.service.LocalizationService;
import org.springframework.stereotype.Controller;
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

    public HallController(HallService hallService, LocalizationService localizationService, BuildingService buildingService) {
        this.hallService = hallService;
        this.localizationService = localizationService;
        this.buildingService = buildingService;
    }

    @GetMapping("/localization")
    public String chooseHallLocalization() {
        return "/hall/localization";
    }


    @GetMapping("/other-localization")
    public String getLocalization(Model model) {
        List<Localization> localizations = buildingService.getBuildings();
        model.addAttribute("localizations", localizations);
        return "/hall/other-localization";
    }

    @GetMapping("/here")
    public String chooseHallHere(Model model) {
        Long buildingId = 101L;
        List<Hall> halls = hallService.getBuildingHalls(buildingId);
        model.addAttribute("halls", halls);
        return "/hall/here";
    }

    @GetMapping("/city")
    public String showCities(Model model) {
        List<Localization> localizations = localizationService.getLocalizations();
        model.addAttribute("localizations", localizations);
        return "/hall/city";
    }

    @GetMapping("/building/{id}")
    public String showBuildingHalls(@PathVariable Long id,
                                    Model model) {
        List<Hall> halls = hallService.getBuildingHalls(id);
        model.addAttribute("halls", halls);
        return "/hall/building";
    }

    @GetMapping("/calendar/{hallId}")
    public String showCalendar(@PathVariable Long hallId,
                               Model model) {

        Optional<Hall> hall = hallService.findHallById(hallId);
        if (hall.isPresent()) {
            model.addAttribute("hall", hall.get());
        } else {
            model.addAttribute("error", "Sala nie została znaleziona.");
            return "/error/error1";
        }

        return "/hall/calendar";
    }
}


