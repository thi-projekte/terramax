package de.thi.terramax.tour;

import java.util.Arrays;

/**
 * Status einer Tour. Jeder Wert ist auf den String in der Datenbank (CHECK-Constraint) und in der API abgebildet.
 * <ul>
 * <li>{@link #PLANNED}, {@link #IN_PROGRESS}: offen</li>
 * <li>{@link #COMPLETED}: regulär abgeschlossen ("Tour abschließen")</li>
 * <li>{@link #DISCARDED}: Plan verworfen ("Tour verworfen")</li>
 * <li>{@link #ABORTED}: abgebrochen (Message {@code TourAbbrechen}), nur über {@code POST /tours/{id}/abort}</li>
 * </ul>
 * Die drei letzten sind endgültig, siehe {@link TourTransitions}.
 */
public enum TourStatus {

    PLANNED("planned"), IN_PROGRESS("in_progress"), COMPLETED("completed"), DISCARDED("discarded"), ABORTED(
            "aborted");

    private final String dbValue;

    TourStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    /** Abgeschlossen, verworfen oder abgebrochen: Status und Halte sind nicht mehr änderbar. */
    public boolean isFinal() {
        return this == COMPLETED || this == DISCARDED || this == ABORTED;
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
