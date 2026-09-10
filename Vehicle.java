package com.traffic.model;

public class Vehicle {
    private final String vehicleNumber;
    private final String ownerName;
    private final String vehicleType; // Car, Bike, Truck etc.

    public Vehicle(String vehicleNumber, String ownerName, String vehicleType) {
        this.vehicleNumber = vehicleNumber;
        this.ownerName = ownerName;
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() { return vehicleNumber; }
    public String getOwnerName() { return ownerName; }
    public String getVehicleType() { return vehicleType; }
}
