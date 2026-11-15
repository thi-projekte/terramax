package de.thi.terramax.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Platzhalter: ruft keine Engine auf, loggt nur und liefert eine erfundene Prozessinstanz-ID ({@code stub-…}).
 * <p>
 * TODO(#64-meeting): entfällt, sobald die Anbindung an die process-engine abgestimmt ist
 */
@ApplicationScoped
@Typed(StubProcessEngineClient.class)
public class StubProcessEngineClient implements ProcessEngineClient {

    static final String ID_PREFIX = "stub-";

    private static final Logger LOG = Logger.getLogger(StubProcessEngineClient.class);

    @Override
    public String startTour(long tourId) {
        String id = ID_PREFIX + UUID.randomUUID();
        LOG.infof("Platzhalter: Prozess für Tour %d nicht gestartet, Fake-ID %s", tourId, id);
        return id;
    }

    @Override
    public ProcessStatus getStatus(String processInstanceId) {
        LOG.infof("Platzhalter: Status von %s nicht abgefragt", processInstanceId);
        return ProcessStatus.ACTIVE;
    }
}
