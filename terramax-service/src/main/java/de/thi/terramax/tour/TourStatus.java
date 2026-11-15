package de.thi.terramax.tour;

import java.util.Arrays;

/**
 * Status einer Tour. Jeder Wert ist auf den String in der Datenbank (CHECK-Constraint) und in der API abgebildet.
 */
public enum TourStatus {

    PLANNED("planned"), IN_PROGRESS("in_progress"), COMPLETED("completed"), DISCARDED("discarded");

    private final String dbValue;

    TourStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /**
     * @throws IllegalArgumentException
     *             wenn der Wert unbekannt ist
     */
    public static TourStatus fromDbValue(String value) {
        return Arrays.stream(values()).filter(s -> s.dbValue.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unbekannter Status: " + value));
    }
}
