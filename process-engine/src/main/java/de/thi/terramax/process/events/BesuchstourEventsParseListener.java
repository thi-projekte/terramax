package de.thi.terramax.process.events;

import org.cibseven.bpm.engine.delegate.ExecutionListener;
import org.cibseven.bpm.engine.delegate.TaskListener;
import org.cibseven.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.cibseven.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.cibseven.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.cibseven.bpm.engine.impl.pvm.process.ActivityImpl;
import org.cibseven.bpm.engine.impl.pvm.process.ScopeImpl;
import org.cibseven.bpm.engine.impl.util.xml.Element;

/**
 * Hängt die Listener für die Prozess-Events an die BPMN-Elemente der Besuchstour (Tabelle "Prozess-Events" in
 * docs/kafka/README.md). Die Element-IDs stammen aus docs/process/besuchstour.bpmn.
 */
class BesuchstourEventsParseListener extends AbstractBpmnParseListener {

    private final BesuchstourEvents events;

    BesuchstourEventsParseListener(BesuchstourEvents events) {
        this.events = events;
    }

    @Override
    public void parseStartEvent(Element element, ScopeImpl scope, ActivityImpl activity) {
        if ("StartEvent_TourAngefordert".equals(activity.getId())) {
            // END: Start-Variablen und initiator sind dann sicher gesetzt
            activity.addListener(ExecutionListener.EVENTNAME_END, (ExecutionListener) events::tourStarted);
        }
    }

    @Override
    public void parseEndEvent(Element element, ScopeImpl scope, ActivityImpl activity) {
        ExecutionListener listener = switch (activity.getId()) {
            case "EndEvent_KeineTour" -> events::tourNotPossible;
            case "EndEvent_TourVerworfen" -> events::tourDiscarded;
            case "EndEvent_TourAbgeschlossen" -> events::tourCompleted;
            case "EndEvent_BesuchUebersprungen" -> events::visitSkipped;
            // Erst am Ende: lehnt TerraMax den Abschluss ab (Fehlerpfad), entsteht kein visit.documented
            case "EndEvent_BesuchDokumentiert" -> events::visitDocumented;
            default -> null;
        };
        if (listener != null) {
            activity.addListener(ExecutionListener.EVENTNAME_START, listener);
        }
    }

    @Override
    public void parseBoundaryEvent(Element element, ScopeImpl scope, ActivityImpl activity) {
        if ("BoundaryEvent_TourAbgebrochen".equals(activity.getId())) {
            activity.addListener(ExecutionListener.EVENTNAME_START, (ExecutionListener) events::tourCancelled);
        }
    }

    @Override
    public void parseUserTask(Element element, ScopeImpl scope, ActivityImpl activity) {
        String formKey = element.attributeNS(BpmnParse.CAMUNDA_BPMN_EXTENSIONS_NS, "formKey");
        UserTaskActivityBehavior behavior = (UserTaskActivityBehavior) activity.getActivityBehavior();
        behavior.getTaskDefinition().addTaskListener(TaskListener.EVENTNAME_CREATE,
                task -> events.taskCreated(task, formKey));
    }
}
