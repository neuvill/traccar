package org.traccar.reports.model;

public class ItinerarySummary {

    private double distance;
    private long drivingTime;
    private long stoppedTime;
    private int tripCount;
    private int stopCount;
    private double odometerStart;
    private double odometerEnd;

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public long getDrivingTime() {
        return drivingTime;
    }

    public void setDrivingTime(long drivingTime) {
        this.drivingTime = drivingTime;
    }

    public long getStoppedTime() {
        return stoppedTime;
    }

    public void setStoppedTime(long stoppedTime) {
        this.stoppedTime = stoppedTime;
    }

    public int getTripCount() {
        return tripCount;
    }

    public void setTripCount(int tripCount) {
        this.tripCount = tripCount;
    }

    public int getStopCount() {
        return stopCount;
    }

    public void setStopCount(int stopCount) {
        this.stopCount = stopCount;
    }

    public double getOdometerStart() {
        return odometerStart;
    }

    public void setOdometerStart(double odometerStart) {
        this.odometerStart = odometerStart;
    }

    public double getOdometerEnd() {
        return odometerEnd;
    }

    public void setOdometerEnd(double odometerEnd) {
        this.odometerEnd = odometerEnd;
    }

}
