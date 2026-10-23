package org.example.letstry.controller;

import org.example.letstry.model.Hall;
import org.example.letstry.service.BuildingService;
import org.example.letstry.service.HallService;
import org.example.letstry.service.LocalizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.ui.ConcurrentModel;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HallNavigationTest {

    private HallService hallService;
    private HallController hallController;
    private MenuController menuController;

    @BeforeEach
    void setUp() {
        hallService = mock(HallService.class);

        hallController = new HallController(
                hallService,
                mock(LocalizationService.class),
                mock(BuildingService.class)
        );

        menuController = new MenuController(hallService);
    }

    @Test
    void shouldRedirectToLocalizationWhenNoHallsExist() {
        when(hallService.getAllHalls()).thenReturn(List.of());

        assertEquals(
                "redirect:/localization",
                menuController.redirectToDefaultHallCalendar()
        );
    }

    @Test
    void shouldSelectFirstAvailableHall() {
        Hall unavailable = new Hall();
        unavailable.setId(1L);
        unavailable.setAvailable(false);

        Hall available = new Hall();
        available.setId(2L);
        available.setAvailable(true);

        when(hallService.getAllHalls())
                .thenReturn(List.of(unavailable, available));

        assertEquals(
                "redirect:/calendar/2",
                menuController.redirectToDefaultHallCalendar()
        );
    }

    @Test
    void shouldRenderCalendarForExistingHall() {
        Hall hall = new Hall();
        hall.setId(5L);
        hall.setName("Sala Alfa");

        when(hallService.findHallById(5L))
                .thenReturn(Optional.of(hall));

        ConcurrentModel model = new ConcurrentModel();

        assertEquals(
                "hall/calendar",
                hallController.showCalendar(5L, model)
        );

        assertSame(hall, model.getAttribute("hall"));
    }

    @Test
    void shouldReturn404ForMissingHall() {
        when(hallService.findHallById(999L))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> hallController.showCalendar(
                        999L,
                        new ConcurrentModel()
                )
        );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldRedirectHereToLocationSelection() {
        assertEquals(
                "redirect:/other-localization",
                hallController.chooseHallHere()
        );
    }
}