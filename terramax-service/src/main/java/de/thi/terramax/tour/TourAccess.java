package de.thi.terramax.tour;

import de.thi.terramax.common.ApiErrors;
import de.thi.terramax.dealer.DealerEmployee;
import de.thi.terramax.security.CurrentUser;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Optional;

/**
 * Welche Touren der Aufrufer sieht: {@code dealer_principal} alle seines Händlers, {@code sales_professional} nur die,
 * die er laut {@code tour_employee} fährt, das Service-Konto der process-engine ({@code process_engine}) jede. Fremde
 * Touren sind wie unbekannte Touren (404), damit niemand erfährt, welche IDs es bei anderen gibt.
 */
@RequestScoped
public class TourAccess {

    private final CurrentUser user;

    @Inject
    public TourAccess(CurrentUser user) {
        this.user = user;
    }

    /**
     * Prüft vorab, ob der Aufrufer überhaupt Touren sehen kann: Nutzer brauchen {@code dealer_id}, das Service-Konto
     * nicht.
     *
     * @throws jakarta.ws.rs.ForbiddenException
     *             für einen Nutzer ohne {@code dealer_id}
     */
    public void requireCaller() {
        if (!user.isServiceAccount()) {
            user.requireDealerId();
        }
    }

    /** Alle sichtbaren Touren eines Nutzers, neueste zuerst. Nicht für das Service-Konto (kein Händler). */
    public List<Tour> visibleTours() {
        String dealerId = user.requireDealerId();
        if (user.isDealerPrincipal()) {
            return Tour.listByDealer(dealerId);
        }
        return user.employee().map(e -> Tour.listByEmployee(dealerId, e.id)).orElse(List.of());
    }

    /**
     * @throws jakarta.ws.rs.NotFoundException
     *             wenn die Tour unbekannt oder für den Aufrufer nicht sichtbar ist
     */
    public Tour visibleTour(long tourId) {
        requireCaller();
        return Tour.<Tour> findByIdOptional(tourId).filter(this::isVisible)
                .orElseThrow(() -> ApiErrors.notFound("Unbekannte Tour: " + tourId));
    }

    /**
     * @throws jakarta.ws.rs.NotFoundException
     *             wenn es zur Prozessinstanz keine Tour gibt oder sie für den Aufrufer nicht sichtbar ist
     */
    public Tour visibleTourByProcess(String processInstanceId) {
        requireCaller();
        return Tour.findByProcessInstanceId(processInstanceId).filter(this::isVisible)
                .orElseThrow(() -> ApiErrors.notFound("Keine Tour zur Prozessinstanz: " + processInstanceId));
    }

    /** Ob der Aufrufer die Tour sehen darf. */
    public boolean isVisible(Tour tour) {
        if (user.isServiceAccount()) {
            return true;
        }
        Optional<String> dealerId = user.dealerId();
        if (dealerId.isEmpty() || !tour.dealer.id.equals(dealerId.get())) {
            return false;
        }
        if (user.isDealerPrincipal()) {
            return true;
        }
        Optional<DealerEmployee> employee = user.employee();
        return employee.isPresent() && tour.isDrivenBy(employee.get().id);
    }
}
