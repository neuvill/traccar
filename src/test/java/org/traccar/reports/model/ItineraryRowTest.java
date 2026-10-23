package org.traccar.reports.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ItineraryRowTest {

    @Test
    public void testAddressParts() {
        ItineraryRow row = new ItineraryRow();
        row.setFromAddress("Alger ⴷⵣⴰⵢⴻⵔ الجزائر, 16000,  , ⵍⵣⵣⴰⵢⴻⵔ الجزائر");
        assertEquals(List.of("Alger ⴷⵣⴰⵢⴻⵔ الجزائر", "16000", "ⵍⵣⵣⴰⵢⴻⵔ الجزائر"), row.getFromAddressParts());

        row.setToAddress(null);
        assertEquals(List.of(), row.getToAddressParts());
        row.setToAddress(" ");
        assertEquals(List.of(), row.getToAddressParts());
    }

}
