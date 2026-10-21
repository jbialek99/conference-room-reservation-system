package org.example.letstry.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "buildings")
public class Building {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
    private String description;

    @ManyToOne
    @JoinColumn(name = "localization_id")
    @JsonIgnore
    private Localization localization;

    @OneToMany(mappedBy = "building", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private List<Hall> halls;

    // Getters and Setters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Localization getLocalization() { return localization; }
    public List<Hall> getHalls() { return halls; }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setLocalization(Localization localization) { this.localization = localization; }
    public void setHalls(List<Hall> halls) { this.halls = halls; }
}