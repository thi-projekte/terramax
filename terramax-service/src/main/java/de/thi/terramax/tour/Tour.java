package de.thi.terramax.tour;

import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.dealer.DealerEmployee;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Besuchstour eines Händlers. {@code processInstanceId} verknüpft die Tour mit der Prozessinstanz in CIB Seven.
 */
@Entity
@Table(name = "tour")
public class Tour extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dealer_id", nullable = false)
    public Dealer dealer;

    @Column(name = "tour_date", nullable = false)
    public LocalDate tourDate;

    @Column(name = "status", nullable = false)
    public TourStatus status = TourStatus.PLANNED;

    @Column(name = "process_instance_id")
    public String processInstanceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public OffsetDateTime createdAt;

    /** Wer die Tour fährt. */
    @ManyToMany
    @JoinTable(name = "tour_employee", joinColumns = @JoinColumn(name = "tour_id"), inverseJoinColumns = @JoinColumn(name = "employee_id"))
    public Set<DealerEmployee> employees = new HashSet<>();

    /** Besuchte Kunden in Reihenfolge. */
    @OneToMany(mappedBy = "tour", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    public List<TourCustomer> stops = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    /** Alle Touren eines Händlers, neueste zuerst. */
    public static List<Tour> listByDealer(String dealerId) {
        return list("dealer.id", byDate(), dealerId);
    }

    /** Touren eines Händlers, die der Mitarbeiter fährt, neueste zuerst. */
    public static List<Tour> listByEmployee(String dealerId, long employeeId) {
        return list("select t from Tour t join t.employees e where t.dealer.id = ?1 and e.id = ?2 "
                + "order by t.tourDate desc, t.id desc", dealerId, employeeId);
    }

    /** Eine Tour, aber nur, wenn sie zum Händler gehört. */
    public static Optional<Tour> findForDealer(long id, String dealerId) {
        return find("id = ?1 and dealer.id = ?2", id, dealerId).firstResultOptional();
    }

    /** Ob der Mitarbeiter diese Tour fährt. */
    public boolean isDrivenBy(long employeeId) {
        return employees.stream().anyMatch(e -> e.id == employeeId);
    }

    private static Sort byDate() {
        return Sort.descending("tourDate").and("id", Sort.Direction.Descending);
    }
}
