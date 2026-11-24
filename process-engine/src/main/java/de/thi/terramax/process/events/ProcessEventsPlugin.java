package de.thi.terramax.process.events;

import de.thi.terramax.process.terramax.TerraMaxClient;
import org.cibseven.bpm.engine.impl.bpmn.parser.BpmnParseListener;
import org.cibseven.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.cibseven.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Engine-Plugin, das beim Deployen der Modelle die Listener für die Prozess-Events einhängt
 * ({@link BesuchstourEventsParseListener}). Das BPMN-Modell selbst bleibt dadurch unverändert.
 */
public class ProcessEventsPlugin extends AbstractProcessEnginePlugin {

    private final ProcessEventPublisher publisher;
    private final TerraMaxClient terraMax;

    public ProcessEventsPlugin(ProcessEventPublisher publisher, TerraMaxClient terraMax) {
        this.publisher = publisher;
        this.terraMax = terraMax;
    }

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        List<BpmnParseListener> listeners = configuration.getCustomPreBPMNParseListeners() == null
                ? new ArrayList<>()
                : new ArrayList<>(configuration.getCustomPreBPMNParseListeners());
        listeners.add(new BesuchstourEventsParseListener(new BesuchstourEvents(publisher, terraMax)));
        configuration.setCustomPreBPMNParseListeners(listeners);
    }
}
