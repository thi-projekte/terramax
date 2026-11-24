package de.thi.terramax.process.events;

import de.thi.terramax.process.besuchstour.BesuchstourVariablen;
import de.thi.terramax.process.terramax.TerraMaxClient;
import de.thi.terramax.process.terramax.TerraMaxException;
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
import java.util.List;
import java.util.Map;

/**
 * Erzeugt die Prozess-Events der Besuchstour (docs/kafka/README.md, Topic {@code terramax.process.events}).
 * <p>
 * Events werden erst nach dem erfolgreichen Commit der Engine-Transaktion gesendet. Schlägt ein Schritt fehl (z. B.
 * Überspringen ohne Grund), entsteht also kein Event. Events enthalten nur IDs, Status und Zähler, keine
 * Gesprächsinhalte. Zusätzlich zum Vertrag steht in jedem Event die {@code tourId} (TerraMax), sobald es sie gibt.
 */
class BesuchstourEvents {

    /**
     * Keycloak-{@code sub} des Startenden. Wird beim Start gemerkt, weil die Engine sonst nur den Benutzernamen kennt,
     * {@code notifications} aber nach {@code sub} zustellt.
     */
    static final String INITIATOR_ID = "initiatorId";

    private static final String INITIATOR = "initiator";
    /** Lokale Variable des Besuchs (Eingabe aus "Sprachnotiz aufnehmen"). */
    private static final String SPRACHNOTIZ_ID = "sprachnotizId";

    private final ProcessEventPublisher publisher;
    private final TerraMaxClient terraMax;

    BesuchstourEvents(ProcessEventPublisher publisher, TerraMaxClient terraMax) {
        this.publisher = publisher;
        this.terraMax = terraMax;
    }

    void tourStarted(DelegateExecution execution) {
        execution.setVariable(INITIATOR_ID, currentUserId(execution));
        publish("tour.started", execution, tourDatum(execution));
    }

    void tourNotPossible(DelegateExecution execution) {
        Map<String, Object> data = tourDatum(execution);
        Object anzahl = execution.getVariable(BesuchstourVariablen.ANZAHL_HALTE);
        data.put("kundenAnzahl", anzahl instanceof Number n ? n.intValue() : 0);
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
        // Zähler kommen aus TerraMax (Single Source of Truth). Fehlt TerraMax gerade, geht das Event ohne Zähler raus,
        // statt den Abschluss der Tour zu blockieren.
        Integer dokumentiert = null;
        Integer uebersprungen = null;
        Integer offen = null;
        try {
            TerraMaxClient.Touruebersicht uebersicht = terraMax.uebersicht(BesuchstourVariablen.tourId(execution));
            dokumentiert = uebersicht.anzahlDokumentiert();
            uebersprungen = uebersicht.anzahlUebersprungen();
            offen = uebersicht.anzahlOffen();
        } catch (TerraMaxException e) {
            // Zähler bleiben null
        }
        data.put("dokumentiert", dokumentiert);
        data.put("uebersprungen", uebersprungen);
        data.put("offen", offen);
        publish("tour.completed", execution, data);
    }

    void visitSkipped(DelegateExecution execution) {
        publish("visit.skipped", execution, kunde(execution));
    }

    void visitDocumented(DelegateExecution execution) {
        Map<String, Object> data = kunde(execution);
        data.put("sprachnotizId", execution.getVariable(SPRACHNOTIZ_ID));
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
        tourIdHinzufuegen(execution, data);
        return data;
    }

    /** Kunde des aktuellen Besuchs: ID und Position aus den Referenzen im Prozess. */
    private static Map<String, Object> kunde(DelegateExecution execution) {
        long kundeId = BesuchstourVariablen.kundeId(execution);
        Map<String, Object> data = new LinkedHashMap<>();
        tourIdHinzufuegen(execution, data);
        data.put("kundeId", String.valueOf(kundeId));
        data.put("position", position(execution, kundeId));
        return data;
    }

    private static void tourIdHinzufuegen(DelegateExecution execution, Map<String, Object> data) {
        Long tourId = BesuchstourVariablen.tourIdOderNull(execution);
        if (tourId != null) {
            data.put("tourId", tourId);
        }
    }

    private static Integer position(DelegateExecution execution, long kundeId) {
        if (execution.getVariable(BesuchstourVariablen.KUNDEN_IDS) instanceof List<?> ids) {
            for (int i = 0; i < ids.size(); i++) {
                if (ids.get(i) instanceof Number n && n.longValue() == kundeId) {
                    return i + 1;
                }
            }
        }
        return null;
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
