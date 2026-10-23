package org.example.letstry.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@ConditionalOnProperty(name = "booking.mode", havingValue = "demo")
public class DemoPageController {

    @GetMapping("/demo")
    public String demo() {
        return "demo/index";
    }
}