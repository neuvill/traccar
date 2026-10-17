package org.traccar.reports.model;

import java.util.List;

public class ItineraryVehicleSection {

    private String deviceName;
    private String groupName = "";
    private int vehicleIndex;
    private int vehicleCount;
    private List<ItineraryRow> rows;

    private String distance;
    private String drivingTime;
    private String stoppedTime;
    private String averageSpeed;
    private String tripCount;
    private String stopCount;
    private String odometerStart;
    private String odometerEnd;

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public int getVehicleIndex() {
        return vehicleIndex;
    }

    public void setVehicleIndex(int vehicleIndex) {
        this.vehicleIndex = vehicleIndex;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public List<ItineraryRow> getRows() {
        return rows;
    }

    public void setRows(List<ItineraryRow> rows) {
        this.rows = rows;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public String getDrivingTime() {
        return drivingTime;
    }

    public void setDrivingTime(String drivingTime) {
        this.drivingTime = drivingTime;
    }

    public String getStoppedTime() {
        return stoppedTime;
    }

    public void setStoppedTime(String stoppedTime) {
        this.stoppedTime = stoppedTime;
    }

    public String getAverageSpeed() {
        return averageSpeed;
    }

    public void setAverageSpeed(String averageSpeed) {
        this.averageSpeed = averageSpeed;
    }

    public String getTripCount() {
        return tripCount;
    }

    public void setTripCount(String tripCount) {
        this.tripCount = tripCount;
    }

    public String getStopCount() {
        return stopCount;
    }

    public void setStopCount(String stopCount) {
        this.stopCount = stopCount;
    }

    public String getOdometerStart() {
        return odometerStart;
    }

    public void setOdometerStart(String odometerStart) {
        this.odometerStart = odometerStart;
    }

    public String getOdometerEnd() {
        return odometerEnd;
    }

    public void setOdometerEnd(String odometerEnd) {
        this.odometerEnd = odometerEnd;
    }

}
