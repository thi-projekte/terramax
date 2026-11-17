package de.thi.terramax.process.events;

import de.thi.terramax.process.besuchstour.BesuchstourVariablen;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.DelegateTask;
import org.cibseven.bpm.engine.delegate.VariableScope;
import org.cibseven.bpm.engine.impl.cfg.TransactionState;
import org.cibseven.bpm.engine.impl.context.Context;
import org.cibseven.bpm.engine.impl.interceptor.CommandContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Erzeugt die Prozess-Events der Besuchstour (docs/kafka/README.md, Topic {@code terramax.process.events}).
 * <p>
 * Events werden erst nach dem erfolgreichen Commit der Engine-Transaktion gesendet. Schlägt ein Schritt fehl (z. B.
 * Überspringen ohne Grund), entsteht also kein Event. Events enthalten nur IDs, Status und Zähler, keine
 * Gesprächsinhalte.
 */
class BesuchstourEvents {

    /**
     * Keycloak-{@code sub} des Startenden. Wird beim Start gemerkt, weil die Engine sonst nur den Benutzernamen kennt,
     * {@code notifications} aber nach {@code sub} zustellt.
     */
    static final String INITIATOR_ID = "initiatorId";

    private static final String INITIATOR = "initiator";

    private final ProcessEventPublisher publisher;

    BesuchstourEvents(ProcessEventPublisher publisher) {
        this.publisher = publisher;
    }

    void tourStarted(DelegateExecution execution) {
        execution.setVariable(INITIATOR_ID, currentUserId(execution));
        publish("tour.started", execution, tourDatum(execution));
    }

    void tourNotPossible(DelegateExecution execution) {
        Map<String, Object> data = tourDatum(execution);
        data.put("kundenAnzahl", BesuchstourVariablen.besuchsplan(execution).size());
        publish("tour.not-possible", execution, data);
    }

    void tourDiscarded(DelegateExecution execution) {
        publish("tour.discarded", execution, tourDatum(execution));
    }

    void tourCancelled(DelegateExecution execution) {
        publish("tour.cancelled", execution, tourDatum(execution));
    }

    void tourCompleted(DelegateExecution execution) {
        Map<String, Object> data = tourDatum(execution);
        Map<?, ?> uebersicht = execution.getVariable(BesuchstourVariablen.TOURUEBERSICHT) instanceof Map<?, ?> map
                ? map
                : Map.of();
        data.put("dokumentiert", uebersicht.get("anzahlDokumentiert"));
        data.put("uebersprungen", uebersicht.get("anzahlUebersprungen"));
        data.put("offen", uebersicht.get("anzahlOffen"));
        publish("tour.completed", execution, data);
    }

    void visitSkipped(DelegateExecution execution) {
        publish("visit.skipped", execution, kunde(execution));
    }

    void visitDocumented(DelegateExecution execution) {
        Map<String, Object> data = kunde(execution);
        data.put("sprachnotizId", execution.getVariable(BesuchstourVariablen.SPRACHNOTIZ_ID));
        publish("visit.documented", execution, data);
    }

    void taskCreated(DelegateTask task, String formKey) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", task.getId());
        data.put("taskDefinitionKey", task.getTaskDefinitionKey());
        data.put("name", task.getName());
        data.put("formKey", formKey);
        Object userId = task.getVariable(INITIATOR_ID);
        publishAfterCommit("task.created", task.getProcessInstanceId(),
                userId != null ? userId.toString() : task.getAssignee(), data);
    }

    // --- Hilfsmethoden ---

    private void publish(String type, DelegateExecution execution, Map<String, Object> data) {
        publishAfterCommit(type, execution.getProcessInstanceId(), userId(execution), data);
    }

    private void publishAfterCommit(String type, String processInstanceId, String userId,
            Map<String, Object> fields) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("processInstanceId", processInstanceId);
        data.putAll(fields);
        ProcessEvent event = ProcessEvent.of(type, processInstanceId, userId, data);

        CommandContext commandContext = Context.getCommandContext();
        if (commandContext == null) {
            publisher.publish(event);
            return;
        }
        commandContext.getTransactionContext().addTransactionListener(TransactionState.COMMITTED,
                committed -> publisher.publish(event));
    }

    private static Map<String, Object> tourDatum(DelegateExecution execution) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("tourDatum", execution.getVariable(BesuchstourVariablen.TOUR_DATUM));
        return data;
    }

    private static Map<String, Object> kunde(DelegateExecution execution) {
        Map<String, Object> kunde = BesuchstourVariablen.kunde(execution);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kundeId", kunde.get("kundeId"));
        data.put("position", kunde.get("position"));
        return data;
    }

    private static String userId(VariableScope scope) {
        Object userId = scope.getVariable(INITIATOR_ID);
        if (userId == null) {
            userId = scope.getVariable(INITIATOR);
        }
        return userId == null ? null : userId.toString();
    }

    /**
     * {@code sub} aus dem Keycloak-Token des aktuellen Requests. Ohne Token (z. B. in Tests) der in der Engine
     * angemeldete Benutzer.
     */
    private static String currentUserId(DelegateExecution execution) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token && token.getToken().getSubject() != null) {
            return token.getToken().getSubject();
        }
        var engineAuthentication = execution.getProcessEngineServices().getIdentityService()
                .getCurrentAuthentication();
        if (engineAuthentication != null && engineAuthentication.getUserId() != null) {
            return engineAuthentication.getUserId();
        }
        return userId(execution);
    }
}
