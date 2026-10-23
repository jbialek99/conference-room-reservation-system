package org.example.letstry.service;

public class ReservationUncertainException extends RuntimeException {

    private final long bookingId;

    public ReservationUncertainException(long bookingId, Throwable cause) {
        super("Nie mozna potwierdzic wyniku operacji Graph.", cause);
        this.bookingId = bookingId;
    }

    public long getBookingId() {
        return bookingId;
    }
}