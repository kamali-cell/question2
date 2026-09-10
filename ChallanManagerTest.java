package com.traffic.service;

import com.traffic.exception.DuplicateChallanException;
import com.traffic.exception.InvalidVehicleException;
import com.traffic.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class ChallanManagerTest {
    private ChallanManager manager;
    private final LocalDateTime eventTime = LocalDateTime.of(2026, 9, 10, 10, 0);

    @BeforeEach
    public void setUp() {
        manager = new ChallanManager();
        manager.registerVehicle(new Vehicle("DL-1CA-1234", "John Smith", "Car"));
    }

    @Test
    public void testNormalChallanGenerationAndPaymentFlow() {
        Challan challan = manager.issueChallan("DL-1CA-1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", eventTime, 0, 0);
        
        assertEquals(1000.0, challan.getFineAmount());
        assertFalse(challan.isPaid());
        assertEquals(1000.0, manager.calculateTotalOutstandingFines("DL-1CA-1234"));

        manager.processPayment(challan.getChallanId());
        assertTrue(challan.isPaid());
        assertEquals(0.0, manager.calculateTotalOutstandingFines("DL-1CA-1234"));
    }

    @Test
    public void testBoundarySpeedConditionValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            manager.issueChallan("DL-1CA-1234", ViolationType.OVER_SPEEDING, "Highway 1", eventTime, 60, 60);
        });
    }

    @Test
    public void testRepeatedViolationPenaltyScalingAndClassification() {
        // Offense 1: Base (1000)
        manager.issueChallan("DL-1CA-1234", ViolationType.SIGNAL_VIOLATION, "Loc 1", eventTime, 0, 0);
        // Offense 2: 1.5x Multiplier (500 * 1.5 = 750)
        manager.issueChallan("DL-1CA-1234", ViolationType.ILLEGAL_PARKING, "Loc 2", eventTime.plusHours(1), 0, 0);
        // Offense 3: 2.0x Multiplier (1000 * 2.0 = 2000)
        Challan third = manager.issueChallan("DL-1CA-1234", ViolationType.SIGNAL_VIOLATION, "Loc 3", eventTime.plusHours(2), 0, 0);

        assertEquals(2000.0, third.getFineAmount());
        assertEquals(VehicleClass.REPEATED_OFFENDER, manager.classifyVehicleProfile("DL-1CA-1234"));
    }

    @Test
    public void testInvalidInputSubmissionHandling() {
        assertThrows(InvalidVehicleException.class, () -> {
            manager.registerVehicle(new Vehicle("", "Anonymous", "Bike"));
        });
        assertThrows(InvalidVehicleException.class, () -> {
            manager.issueChallan("UNREGISTERED-99", ViolationType.ILLEGAL_PARKING, "Loc X", eventTime, 0, 0);
        });
    }

    @Test
    public void testDuplicateChallanExceptionScenarios() {
        manager.issueChallan("DL-1CA-1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", eventTime, 0, 0);
        
        assertThrows(DuplicateChallanException.class, () -> {
            manager.issueChallan("DL-1CA-1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", eventTime, 0, 0);
        });
    }
}
