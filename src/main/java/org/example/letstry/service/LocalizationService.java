package org.example.letstry.service;

import org.example.letstry.model.Localization;
import org.example.letstry.repository.LocalizationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LocalizationService {
    private final LocalizationRepository localizationRepository;

    public LocalizationService(LocalizationRepository localizationRepository) {
        this.localizationRepository = localizationRepository;
    }

    public List<Localization> getLocalizations(){
        return localizationRepository.findAll();
    }
}
