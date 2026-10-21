package org.example.letstry.model;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "localizations")
public class Localization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToMany(mappedBy = "localization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Building> buildings;

    private String city;
    private String address;
    private String postalCode;
    private String state;

    // Getters and Setters
    public long getId() { return id; }
    public List<Building> getBuildings() { return buildings; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public String getPostalCode() { return postalCode; }
    public String getState() { return state; }

    public void setId(long id) { this.id = id; }
    public void setBuildings(List<Building> buildings) { this.buildings = buildings; }
    public void setCity(String city) { this.city = city; }
    public void setAddress(String address) { this.address = address; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public void setState(String description) { this.state = description; }
}
