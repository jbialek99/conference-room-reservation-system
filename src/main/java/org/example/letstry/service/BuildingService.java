package org.example.letstry.service;

import org.example.letstry.model.Localization;
import org.example.letstry.repository.LocalizationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BuildingService {
   private final LocalizationRepository localizationRepository;
   public BuildingService(LocalizationRepository localizationRepository) {
       this.localizationRepository = localizationRepository;
   }

    public List<Localization> getBuildings(){
        return localizationRepository.findAllWithBuildings();
    }
}
