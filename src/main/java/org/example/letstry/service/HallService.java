package org.example.letstry.service;

import org.example.letstry.model.Building;
import org.example.letstry.model.Hall;
import org.example.letstry.repository.BuildingRepository;
import org.example.letstry.repository.HallRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
public class HallService {

    private final HallRepository hallRepository;
    private final BuildingRepository buildingRepository;

    public HallService( HallRepository hallRepository, BuildingRepository buildingRepository) {
    this.hallRepository = hallRepository;
    this.buildingRepository = buildingRepository;
    }
// ZMIANA!
    public List<Hall> getHalls(){
        Optional<Building> building = buildingRepository.findById(1L);
        return building.map(hallRepository::findByBuilding).orElse(List.of());
    }

    public List<Hall> getBuildingHalls(Long buildingId){
        Optional<Building> building = buildingRepository.findById(buildingId);
        return building.map(hallRepository::findByBuilding).orElse(List.of());
    }

    public Optional<Hall> findHallById(Long hallId){
        return hallRepository.findById(hallId);
    }

    public List<Hall> getAllHalls() {
        return hallRepository.findAll();
    }

    public Optional<Hall> findByEmail(String email) {
        return hallRepository.findByEmail(email);
    }


}
