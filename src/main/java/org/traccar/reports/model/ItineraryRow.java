package org.traccar.reports.model;

import java.util.Arrays;
import java.util.List;

public class ItineraryRow {

    private boolean trip;
    private String timeRange;
    private String day;
    private String duration;
    private String fromAddress;
    private String toAddress;
    private String distance = "—";
    private String speed = "—";
    private String odometer = "—";
    private String fuel = "—";

    public boolean isTrip() {
        return trip;
    }

    public void setTrip(boolean trip) {
        this.trip = trip;
    }

    /** Localized date of the entry start, used for the day heading rows in the PDF. */
    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(String timeRange) {
        this.timeRange = timeRange;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    /**
     * Address split into its comma-separated parts. The PDF renders each part as its own bidi unit, so numbers
     * such as postcodes keep their place next to Arabic text instead of being reordered into it.
     */
    public List<String> getFromAddressParts() {
        return splitAddress(fromAddress);
    }

    public List<String> getToAddressParts() {
        return splitAddress(toAddress);
    }

    private static List<String> splitAddress(String address) {
        if (address == null || address.isBlank()) {
            return List.of();
        }
        return Arrays.stream(address.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toList();
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public String getSpeed() {
        return speed;
    }

    public void setSpeed(String speed) {
        this.speed = speed;
    }

    public String getOdometer() {
        return odometer;
    }

    public void setOdometer(String odometer) {
        this.odometer = odometer;
    }

    public String getFuel() {
        return fuel;
    }

    public void setFuel(String fuel) {
        this.fuel = fuel;
    }

}
