package de.thi.terramax.tour;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.dealer.DealerEmployee;
import de.thi.terramax.security.CurrentUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Welche Touren der angemeldete Nutzer sieht: {@code dealer_principal} alle seines Händlers, {@code sales_professional}
 * nur die, die er laut {@code tour_employee} fährt. Fremde Touren sind wie unbekannte Touren (404), damit niemand
 * erfährt, welche IDs es bei anderen gibt.
 */
@RequestScoped
public class TourAccess {

    private final CurrentUser user;

    @Inject
    public TourAccess(CurrentUser user) {
        this.user = user;
    }

    /** Alle sichtbaren Touren, neueste zuerst. */
    public List<Tour> visibleTours() {
        String dealerId = user.requireDealerId();
        if (user.isDealerPrincipal()) {
            return Tour.listByDealer(dealerId);
        }
        return user.employee().map(e -> Tour.listByEmployee(dealerId, e.id)).orElse(List.of());
    }

    /**
     * @throws jakarta.ws.rs.NotFoundException
     *             wenn die Tour unbekannt oder für den Nutzer nicht sichtbar ist
     */
    public Tour visibleTour(long tourId) {
        String dealerId = user.requireDealerId();
        Optional<Tour> tour = Tour.findForDealer(tourId, dealerId);
        if (!user.isDealerPrincipal()) {
            Optional<DealerEmployee> employee = user.employee();
            tour = tour.filter(t -> employee.isPresent() && t.isDrivenBy(employee.get().id));
        }
        return tour.orElseThrow(() -> ApiErrors.notFound("Unbekannte Tour: " + tourId));
    }
}
