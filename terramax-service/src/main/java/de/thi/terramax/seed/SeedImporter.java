package de.thi.terramax.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.thi.terramax.common.GeoJsonResources;
import de.thi.terramax.customer.Customer;
import de.thi.terramax.dealer.Dealer;
import de.thi.terramax.location.Location;
import de.thi.terramax.location.LocationImporter;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Importiert beim Start Testdaten für Händler und Kunden, damit man lokal und mit den Testnutzern des Realms sofort
 * etwas sieht. Gleiches Muster wie {@link LocationImporter}: Angelegt wird nur, was noch nicht existiert; bestehende
 * Einträge werden nie überschrieben. Ungültige Einträge werden geloggt und übersprungen. Jede Datei läuft in einer
 * eigenen Transaktion.
 * <ul>
 * <li>Händler aus {@code terramax.seed.dealer-files}: jedes Feature mit {@code category = john_deere} oder ohne
 * Kategorie. Die ID ist die {@code properties.id}; gibt es einen Standort mit derselben externen ID, wird er verknüpft
 * ({@code dealer.location_id}). Deshalb läuft dieser Import nach dem Standort-Import.</li>
 * <li>Kunden aus {@code terramax.seed.customer-files}: alle dem Händler {@code terramax.seed.customer-dealer-id}
 * zugeordnet. Die externe ID ist {@code properties.customer_id} (sonst {@code properties.id}).</li>
 * </ul>
 * Abschaltbar über {@code terramax.seed.enabled=false} bzw. {@code TERRAMAX_SEED_ENABLED=false}.
 * <p>
 * TODO(#64-meeting): Woher echte Händler- und Kundendaten kommen (CRM-Import, Admin-Endpunkt), ist noch offen; dann den
 * Import mit TERRAMAX_SEED_ENABLED=false abschalten
 */
@ApplicationScoped
public class SeedImporter {

    /** Nach Flyway und nach dem Standort-Import. */
    public static final int STARTUP_PRIORITY = LocationImporter.STARTUP_PRIORITY + 100;

    static final String JOHN_DEERE_CATEGORY = "john_deere";
    static final int MAX_ID_LENGTH = 64;
    static final int MAX_TEXT_LENGTH = 255;
    static final int MAX_URL_LENGTH = 1024;

    private static final Logger LOG = Logger.getLogger(SeedImporter.class);

    private final ObjectMapper objectMapper;

    @ConfigProperty(name = "terramax.seed.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "terramax.seed.dealer-files", defaultValue = "data/john_deere_at.geojson,data/test_dealers.geojson")
    List<String> dealerFiles;

    @ConfigProperty(name = "terramax.seed.customer-files", defaultValue = "data/mock_customers.geojson")
    List<String> customerFiles;

    @ConfigProperty(name = "terramax.seed.customer-dealer-id", defaultValue = "D-100")
    String customerDealerId;

    @Inject
    SeedImporter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Ergebnis des Imports einer Datei. */
    record ImportResult(int created, int skipped, int invalid) {
    }

    void onStart(@Observes @Priority(STARTUP_PRIORITY) StartupEvent event) {
        if (!enabled) {
            LOG.info("Import der Testdaten ist deaktiviert (terramax.seed.enabled=false)");
            return;
        }
        importAll();
    }

    /** Erst alle Händler, dann alle Kunden. */
    void importAll() {
        dealerFiles.forEach(this::importDealers);
        customerFiles.forEach(file -> importCustomers(file, customerDealerId));
    }

    /**
     * @return neu angelegte, übersprungene (bereits vorhanden oder keine Händler-Kategorie) und ungültige Einträge
     */
    @Transactional
    ImportResult importDealers(String resource) {
        Set<String> seen = new HashSet<>();
        int created = 0;
        int skipped = 0;
        int invalid = 0;
        for (JsonNode feature : GeoJsonResources.readFeatures(objectMapper, resource)) {
            Dealer dealer;
            try {
                dealer = parseDealer(feature);
            } catch (InvalidEntryException e) {
                invalid++;
                LOG.warnf("%s: ungültiger Händler übersprungen: %s", resource, e.getMessage());
                continue;
            }
            if (dealer == null || !seen.add(dealer.id) || Dealer.findById(dealer.id) != null) {
                skipped++;
                continue;
            }
            dealer.location = Location.findByExternalId(dealer.id).orElse(null);
            dealer.persist();
            created++;
        }
        LOG.infof("Händler-Import %s: %d neu, %d übersprungen, %d ungültig", resource, created, skipped, invalid);
        return new ImportResult(created, skipped, invalid);
    }

    /**
     * @return neu angelegte, übersprungene (externe ID bereits vorhanden) und ungültige Einträge; fehlt der Händler,
     *         sind alle Einträge ungültig
     */
    @Transactional
    ImportResult importCustomers(String resource, String dealerId) {
        JsonNode features = GeoJsonResources.readFeatures(objectMapper, resource);
        Dealer dealer = Dealer.findById(dealerId);
        if (dealer == null) {
            LOG.errorf("Kunden-Import %s: Händler %s fehlt, keine Kunden importiert", resource, dealerId);
            return new ImportResult(0, 0, features.size());
        }

        Set<String> seen = new HashSet<>();
        int created = 0;
        int skipped = 0;
        int invalid = 0;
        for (JsonNode feature : features) {
            Customer customer;
            try {
                customer = parseCustomer(feature);
            } catch (InvalidEntryException e) {
                invalid++;
                LOG.warnf("%s: ungültiger Kunde übersprungen: %s", resource, e.getMessage());
                continue;
            }
            if (!seen.add(customer.externalId) || Customer.findByExternalId(customer.externalId).isPresent()) {
                skipped++;
                continue;
            }
            customer.dealer = dealer;
            customer.persist();
            created++;
        }
        LOG.infof("Kunden-Import %s (Händler %s): %d neu, %d übersprungen, %d ungültig", resource, dealerId, created,
                skipped, invalid);
        return new ImportResult(created, skipped, invalid);
    }

    /** @return der Händler oder {@code null}, wenn das Feature eine andere Kategorie hat (z. B. Wettbewerber) */
    static Dealer parseDealer(JsonNode feature) throws InvalidEntryException {
        JsonNode props = feature.path("properties");
        String category = text(props, "category", MAX_TEXT_LENGTH);
        if (category != null && !category.equals(JOHN_DEERE_CATEGORY)) {
            return null;
        }
        Dealer dealer = new Dealer();
        dealer.id = required(props, "id", MAX_ID_LENGTH, null);
        dealer.name = required(props, "name", MAX_TEXT_LENGTH, dealer.id);
        dealer.street = text(props, "street", MAX_TEXT_LENGTH);
        dealer.postcode = text(props, "postcode", 16);
        dealer.city = text(props, "city", MAX_TEXT_LENGTH);
        double[] lonLat = coordinates(feature, dealer.id);
        if (lonLat != null) {
            dealer.longitude = lonLat[0];
            dealer.latitude = lonLat[1];
        }
        return dealer;
    }

    static Customer parseCustomer(JsonNode feature) throws InvalidEntryException {
        JsonNode props = feature.path("properties");
        Customer customer = new Customer();
        customer.externalId = text(props, "customer_id", MAX_ID_LENGTH);
        if (customer.externalId == null) {
            customer.externalId = required(props, "id", MAX_ID_LENGTH, null);
        }
        customer.name = required(props, "name", MAX_TEXT_LENGTH, customer.externalId);
        customer.region = text(props, "region", MAX_TEXT_LENGTH);
        customer.segment = text(props, "segment", MAX_TEXT_LENGTH);
        customer.crmUrl = text(props, "crm_url", MAX_URL_LENGTH);
        double[] lonLat = coordinates(feature, customer.externalId);
        if (lonLat != null) {
            customer.longitude = lonLat[0];
            customer.latitude = lonLat[1];
        }
        return customer;
    }

    /** GeoJSON-Punkt als {@code [Länge, Breite]}; {@code null}, wenn das Feature keine Geometrie hat. */
    private static double[] coordinates(JsonNode feature, String id) throws InvalidEntryException {
        JsonNode geometry = feature.get("geometry");
        if (geometry == null || geometry.isNull()) {
            return null;
        }
        JsonNode c = geometry.path("coordinates");
        if (!c.isArray() || c.size() < 2 || !c.get(0).isNumber() || !c.get(1).isNumber()) {
            throw new InvalidEntryException(id + ": Koordinaten fehlen oder sind ungültig");
        }
        double lon = c.get(0).asDouble();
        double lat = c.get(1).asDouble();
        if (lon < -180 || lon > 180 || lat < -90 || lat > 90) {
            throw new InvalidEntryException(id + ": Koordinaten außerhalb des gültigen Bereichs");
        }
        return new double[] { lon, lat };
    }

    private static String required(JsonNode props, String field, int maxLength, String id)
            throws InvalidEntryException {
        String value = text(props, field, maxLength);
        if (value == null) {
            throw new InvalidEntryException((id == null ? "Eintrag" : id) + ": " + field + " fehlt");
        }
        return value;
    }

    /** Text des Feldes oder {@code null}, wenn es fehlt, null oder leer ist. */
    private static String text(JsonNode props, String field, int maxLength) throws InvalidEntryException {
        JsonNode value = props.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().strip();
        if (text.length() > maxLength) {
            throw new InvalidEntryException(field + " ist länger als " + maxLength + " Zeichen");
        }
        return text.isEmpty() ? null : text;
    }

    /** Ein Eintrag der Datei ist unbrauchbar. */
    static class InvalidEntryException extends Exception {

        InvalidEntryException(String message) {
            super(message);
        }
    }
}
