package org.example.letstry.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "halls")
public class Hall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private String name;
    private String type;
    private String capacity;
    private boolean available;
    private String description;
    private String email;

    @OneToMany(mappedBy = "hall", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Reservation> reservations;

    @ManyToOne
    @JoinColumn(name = "building_id")
    private Building building;

    // Getters and Setters
    public long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public boolean isAvailable() { return available; }
    public String getCapacity() { return capacity; }
    public List<Reservation> getReservations() { return reservations; }
    public Building getBuilding() { return building; }
    public String getDescription() {
        return description;
    }
    public String getEmail() {
        return email;
    }

    public void setId(long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setType(String type) { this.type = type; }
    public void setCapacity(String capacity) { this.capacity = capacity; }
    public void setAvailable(boolean available) { this.available = available; }
    public void setReservations(List<Reservation> reservations) { this.reservations = reservations; }
    public void setBuilding(Building building) { this.building = building; }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setEmail(String email) {
        this.email = email;
    }
}
