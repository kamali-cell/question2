package com.traffic.service;

import com.traffic.exception.DuplicateChallanException;
import com.traffic.exception.InvalidVehicleException;
import com.traffic.model.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ChallanManager {
    private final Map<String, Vehicle> vehicles = new ConcurrentHashMap<>();
    private final Map<String, List<Challan>> violationHistory = new ConcurrentHashMap<>();
    private final Map<String, Challan> allChallans = new ConcurrentHashMap<>();
    private final AtomicInteger challanIdCounter = new AtomicInteger(1000);

    public void registerVehicle(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleNumber() == null || vehicle.getVehicleNumber().trim().isEmpty()) {
            throw new InvalidVehicleException("Invalid data: Vehicle number missing.");
        }
        vehicles.put(vehicle.getVehicleNumber(), vehicle);
        violationHistory.putIfAbsent(vehicle.getVehicleNumber(), new ArrayList<>());
    }

    public Challan issueChallan(String vehicleNumber, ViolationType violation, String location, 
                                LocalDateTime timestamp, double speed, double permittedSpeed) {
        
        // 1. Input Validation Checks
        if (!vehicles.containsKey(vehicleNumber)) {
            throw new InvalidVehicleException("Vehicle profile not registered in the network database system.");
        }
        if (violation == ViolationType.OVER_SPEEDING && speed <= permittedSpeed) {
            throw new IllegalArgumentException("Speed calculation processing error: registered speed must exceed limits.");
        }

        // 2. Prevent Duplicate Challans for exact same operational window events
        List<Challan> history = violationHistory.get(vehicleNumber);
        boolean isDuplicate = history.stream().anyMatch(c -> 
            c.getViolationType() == violation && 
            c.getLocation().equalsIgnoreCase(location) && 
            c.getTimestamp().equals(timestamp)
        );
        if (isDuplicate) {
            throw new DuplicateChallanException("Operation Rejected: Duplicate violation logging event captured.");
        }

        // 3. Process Fine Multipliers depending on historical offense metrics
        long offenseCount = history.size();
        double multiplier = 1.0;
        if (offenseCount >= 3) {
            multiplier = 2.0; // 100% fine surge scaling for extreme recursive behaviors
        } else if (offenseCount >= 1) {
            multiplier = 1.5; // 50% escalation for repeated offenses
        }

        double finalFine = violation.getBaseFine() * multiplier;

        // Extra dynamic scaling for severe speeding offenses
        if (violation == ViolationType.OVER_SPEEDING && (speed - permittedSpeed) > 40) {
            finalFine += 1000.0; 
        }

        String challanId = "CH-" + challanIdCounter.incrementAndGet();
        Challan newChallan = new Challan(challanId, vehicleNumber, violation, location, timestamp, speed, permittedSpeed, finalFine);
        
        history.add(newChallan);
        allChallans.put(challanId, newChallan);
        
        return newChallan;
    }

    public void processPayment(String challanId) {
        Challan challan = allChallans.get(challanId);
        if (challan == null) {
            throw new NoSuchElementException("Target transactional billing matching identity not located.");
        }
        challan.setPaid(true);
    }

    public double calculateTotalOutstandingFines(String vehicleNumber) {
        List<Challan> history = violationHistory.getOrDefault(vehicleNumber, Collections.emptyList());
        return history.stream()
                .filter(c -> !c.isPaid())
                .mapToDouble(Challan.getFineAmount)
                .sum();
    }

    public VehicleClass classifyVehicleProfile(String vehicleNumber) {
        if (!vehicles.containsKey(vehicleNumber)) {
            throw new InvalidVehicleException("Vehicle reference index targeting target identifier context missing.");
        }
        int totalOffenses = violationHistory.getOrDefault(vehicleNumber, Collections.emptyList()).size();
        if (totalOffenses == 0) return VehicleClass.SAFE_DRIVER;
        if (totalOffenses <= 2) return VehicleClass.MODERATE_OFFENDER;
        return VehicleClass.REPEATED_OFFENDER;
    }

    public List<Challan> getUnpaidChallans() {
        return allChallans.values().stream().filter(c -> !c.isPaid()).collect(Collectors.toList());
    }

    public List<Challan> getPaidChallans() {
        return allChallans.values().stream().filter(Challan::isPaid).collect(Collectors.toList());
    }
}
