package de.thi.terramax.tour;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Letzter Besuch je Kunde eines Händlers: spätester Tourtag mit dokumentiertem Halt oder spätestes Besuchsprotokoll.
 * Gemeinsame Regel für die Tourplanung ({@link DefaultTourPlanner}) und die Anzeige der Halte ({@link TourDto.Stop}).
 */
final class LastVisits {

    private LastVisits() {
    }

    /** Kunden-ID → Datum des letzten Besuchs; Kunden ohne Besuch fehlen. */
    static Map<Long, LocalDate> byCustomer(EntityManager em, String dealerId) {
        Map<Long, LocalDate> result = new HashMap<>();
        em.createQuery("select tc.customer.id, max(tc.tour.tourDate) from TourCustomer tc "
                + "where tc.status = ?2 and tc.customer.dealer.id = ?1 group by tc.customer.id", Object[].class)
                .setParameter(1, dealerId).setParameter(2, StopStatus.DOKUMENTIERT).getResultList()
                .forEach(row -> result.merge((Long) row[0], (LocalDate) row[1], LastVisits::later));
        em.createQuery("select p.customer.id, max(p.createdAt) from VisitorProtocol p "
                + "where p.customer.dealer.id = ?1 group by p.customer.id", Object[].class).setParameter(1, dealerId)
                .getResultList().forEach(row -> result.merge((Long) row[0],
                        ((OffsetDateTime) row[1]).toLocalDate(), LastVisits::later));
        return result;
    }

    private static LocalDate later(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }
}
