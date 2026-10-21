
package org.example.letstry.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private Instant date = Instant.now();

    private String title;

    private Instant startMeeting;

    private Instant endMeeting;

    private String outlookEventId;

    private String organizerEmail;

    private String description;

    @Column(name = "last_synchronized_at")
    private Instant lastSynchronizedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    public long getId() {
        return id;
    }

    public Instant getDate() {
        return date;
    }

    public String getTitle() {
        return title;
    }

    public Instant getStartMeeting() {
        return startMeeting;
    }

    public Instant getEndMeeting() {
        return endMeeting;
    }

    public String getOutlookEventId() {
        return outlookEventId;
    }

    public String getOrganizerEmail() {
        return organizerEmail;
    }

    public String getDescription() {
        return description;
    }

    public Instant getLastSynchronizedAt() {
        return lastSynchronizedAt;
    }

    public User getUser() {
        return user;
    }

    public Hall getHall() {
        return hall;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setDate(Instant date) {
        this.date = date;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setStartMeeting(Instant startMeeting) {
        this.startMeeting = startMeeting;
    }

    public void setEndMeeting(Instant endMeeting) {
        this.endMeeting = endMeeting;
    }

    public void setOutlookEventId(String outlookEventId) {
        this.outlookEventId = outlookEventId;
    }

    public void setOrganizerEmail(String organizerEmail) {
        this.organizerEmail = organizerEmail;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setLastSynchronizedAt(Instant lastSynchronizedAt) {
        this.lastSynchronizedAt = lastSynchronizedAt;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setHall(Hall hall) {
        this.hall = hall;
    }

    public void setStatus(ReservationStatus status) {
        this.status = status;
    }
}
