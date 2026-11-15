package de.thi.terramax.engine;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Map;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Anbindung an CIB Seven über {@code /engine-rest}. Noch nicht Standard, Auswahl über
 * {@code terramax.engine.mode=rest}. Die Tour-ID geht als Business Key und als Prozessvariable {@code tourId} mit.
 * <p>
 * TODO(#64-meeting): Aufrufrichtung und Prozess-Key mit process-engine klären; CIB Seven auf dem Server kommt mit #63
 */
@ApplicationScoped
@Typed(RestProcessEngineClient.class)
public class RestProcessEngineClient implements ProcessEngineClient {

    private final CibSevenApi api;
    private final String processKey;

    @Inject
    public RestProcessEngineClient(@RestClient CibSevenApi api,
            @ConfigProperty(name = "terramax.engine.process-key", defaultValue = "Besuchstour") String processKey) {
        this.api = api;
        this.processKey = processKey;
    }

    @Override
    public String startTour(long tourId) {
        var request = new CibSevenApi.StartRequest(String.valueOf(tourId),
                Map.of("tourId", new CibSevenApi.Variable(tourId, "Long")));
        CibSevenApi.StartedInstance started = call(() -> api.start(processKey, request));
        if (started == null || started.id() == null) {
            throw new ProcessEngineException("CIB Seven liefert keine Prozessinstanz");
        }
        return started.id();
    }

    @Override
    public ProcessStatus getStatus(String processInstanceId) {
        CibSevenApi.InstanceState state;
        try {
            state = call(() -> api.processInstance(processInstanceId));
        } catch (NotFound e) {
            // Beendete Instanzen liefert /process-instance nicht mehr
            return ProcessStatus.ENDED;
        }
        if (state.ended()) {
            return ProcessStatus.ENDED;
        }
        return state.suspended() ? ProcessStatus.SUSPENDED : ProcessStatus.ACTIVE;
    }

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            if (status == 404) {
                throw new NotFound();
            }
            throw new ProcessEngineException("CIB Seven antwortet mit Status " + status);
        } catch (ProcessingException e) {
            throw new ProcessEngineException("CIB Seven nicht erreichbar");
        }
    }

    /** 404 der Engine, nur intern. */
    private static final class NotFound extends ProcessEngineException {

        NotFound() {
            super("CIB Seven antwortet mit Status 404");
        }
    }
}
