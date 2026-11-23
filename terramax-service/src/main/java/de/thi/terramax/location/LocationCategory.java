package de.thi.terramax.location;

import java.util.Arrays;

/**
 * Kategorie eines Standorts. Jeder Wert ist auf den in der Datenbank (CHECK-Constraint) und im GeoJSON verwendeten
 * String abgebildet.
 */
public enum LocationCategory {

    COMPETITOR("competitor"), MIXED_COMPETITOR("mixed_competitor"), JOHN_DEERE("john_deere");

    private final String dbValue;

    LocationCategory(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /**
     * Liefert die Kategorie zum gegebenen String (z. B. "mixed_competitor").
     *
     * @throws IllegalArgumentException
     *             wenn der Wert unbekannt ist
     */
    public static LocationCategory fromDbValue(String value) {
        return Arrays.stream(values()).filter(c -> c.dbValue.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Kategorie: " + value));
    }
}
