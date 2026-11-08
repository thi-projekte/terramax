package de.thi.terramax.process;

import de.thi.terramax.process.tour.TourPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Testdaten: Besuchspläne, wie sie der tour-service liefern würde.
 */
public final class TestPlans {

    private TestPlans() {
    }

    /** Plan mit {@code count} Kunden K-1 … K-n für den 10.11.2026. */
    public static TourPlan withCustomers(int count) {
        List<TourPlan.Visit> visits = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            visits.add(new TourPlan.Visit(i,
                    new TourPlan.Customer("K-" + i, "Betrieb " + i, "Ort " + i,
                            new TourPlan.Position(48.0 + i / 10.0, 14.0), null),
                    5.0 * i));
        }
        return new TourPlan("2026-11-10", visits, 5.0 * count);
    }
}
