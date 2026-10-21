package org.example.letstry.repository;

import org.example.letstry.model.Localization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocalizationRepository extends JpaRepository<Localization, Long> {
    @Query("SELECT DISTINCT l FROM Localization l LEFT JOIN FETCH l.buildings")
    List<Localization> findAllWithBuildings();

}
