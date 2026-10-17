package org.traccar.reports.model;

import java.util.Date;

public final class ItineraryEntry {

    public static ItineraryEntry trip(TripReportItem trip) {
        return new ItineraryEntry(trip, null);
    }

    public static ItineraryEntry stop(StopReportItem stop) {
        return new ItineraryEntry(null, stop);
    }

    private final TripReportItem trip;
    private final StopReportItem stop;

    private ItineraryEntry(TripReportItem trip, StopReportItem stop) {
        this.trip = trip;
        this.stop = stop;
    }

    public boolean isTrip() {
        return trip != null;
    }

    public boolean isStop() {
        return stop != null;
    }

    public TripReportItem getTrip() {
        return trip;
    }

    public StopReportItem getStop() {
        return stop;
    }

    public Date getStartTime() {
        return isTrip() ? trip.getStartTime() : stop.getStartTime();
    }

    public Date getEndTime() {
        return isTrip() ? trip.getEndTime() : stop.getEndTime();
    }

}
