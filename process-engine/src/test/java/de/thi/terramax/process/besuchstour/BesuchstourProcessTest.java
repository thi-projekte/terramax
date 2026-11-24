package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.FakeTerraMax;
import de.thi.terramax.process.security.Roles;
import de.thi.terramax.process.terramax.TerraMaxTechnischerFehler;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spielt das BPMN-Modell "Besuchstour" (docs/process/besuchstour.bpmn) mit der echten Engine durch. Der
 * terramax-service wird durch {@link FakeTerraMax} ersetzt, das die Statusregeln von TerraMax nachbildet.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeTerraMax.Config.class)
class BesuchstourProcessTest {

    private static final String SALES = "sales";

    @Autowired
    RuntimeService runtimeService;
    @Autowired
    TaskService taskService;
    @Autowired
    HistoryService historyService;
    @Autowired
    IdentityService identityService;
    @Autowired
    FakeTerraMax terraMax;

    @BeforeEach
    void reset() {
        terraMax.reset();
    }

    @Test
    void completeTourWithOneSkippedAndTwoDocumentedVisits() {
        terraMax.letzterBesuch(2, "2026-03-12");
        ProcessInstance tour = startTour(3);
        long tourId = terraMax.einzigeTour().id;
        assertThat(terraMax.einzigeTour().processInstanceId).isEqualTo(tour.getId());

        Task planPruefen = currentTask(tour);
        assertThat(planPruefen.getTaskDefinitionKey()).isEqualTo("Task_BesuchsplanPruefen");
        assertThat(planPruefen.getAssignee()).isEqualTo(SALES);
        assertThat(besuchsplan(planPruefen)).extracting(k -> k.get("kundeId")).containsExactly("1", "2", "3");
        assertThat(besuchsplan(planPruefen).get(1)).containsEntry("letzterBesuch", "2026-03-12");
        taskService.complete(planPruefen.getId(), Map.of("planBestaetigt", true));

        // Kunde 1 wird übersprungen
        Task vorbereiten1 = currentTask(tour);
        assertThat(kunde(vorbereiten1)).containsEntry("kundeId", "1").containsEntry("name", "Betrieb 1");
        taskService.complete(vorbereiten1.getId(),
                Map.of("besuchUeberspringen", true, "ueberspringenGrund", "Betrieb geschlossen"));

        // Kunde 2 und 3 werden besucht und dokumentiert
        Task vorbereiten2 = currentTask(tour);
        assertThat(kunde(vorbereiten2)).containsEntry("kundeId", "2").containsEntry("letzterBesuch", "2026-03-12");
        assertThat(taskService.getVariable(vorbereiten2.getId(), "ueberspringenGrund"))
                .as("Grund von Kunde 1 darf nicht in den nächsten Besuch überlaufen").isNull();
        besuchDokumentieren(tour, vorbereiten2, "Herr Huber");
        besuchDokumentieren(tour, currentTask(tour), "Frau Leitner");

        // Status je Halt liegt in TerraMax, nicht in der Engine
        assertThat(terraMax.einzigeTour().statusJeHalt()).containsExactly("UEBERSPRUNGEN", "DOKUMENTIERT",
                "DOKUMENTIERT");
        assertThat(terraMax.einzigeTour().halte.get(0).grund).isEqualTo("Betrieb geschlossen");
        assertThat(terraMax.einzigeTour().halte.get(1).begonnenAm).isEqualTo("2026-11-10T09:00:00Z");
        assertThat(terraMax.einzigeTour().halte.get(1).abgeschlossenAm).isEqualTo("2026-11-10T09:45:00Z");

        Task abschliessen = currentTask(tour);
        assertThat(abschliessen.getTaskDefinitionKey()).isEqualTo("Task_TourAbschliessen");
        Map<String, Object> uebersicht = map(taskService.getVariable(abschliessen.getId(), "touruebersicht"));
        assertThat(statuses(uebersicht)).containsExactly("UEBERSPRUNGEN", "DOKUMENTIERT", "DOKUMENTIERT");
        assertThat(number(uebersicht, "anzahlDokumentiert")).isEqualTo(2);
        assertThat(number(uebersicht, "anzahlUebersprungen")).isEqualTo(1);
        assertThat(besuche(uebersicht).get(0)).containsEntry("grund", "Betrieb geschlossen");

        // Die Engine hält nur Referenzen, Flags und die Ausführung, keine Tourdaten
        // initiator/initiatorId: wer die Tour gestartet hat (Benutzername und Keycloak-sub für die Events)
        assertThat(runtimeService.getVariables(tour.getId()).keySet()).containsExactlyInAnyOrder("initiator",
                "initiatorId", "tourDatum", "tourId", "kundenIds", "anzahlHalte", "planBestaetigt",
                "touruebersicht");
        assertThat(((Number) runtimeService.getVariable(tour.getId(), "tourId")).longValue()).isEqualTo(tourId);

        taskService.complete(abschliessen.getId());
        assertEndedAt(tour, "EndEvent_TourAbgeschlossen");
        assertThat(terraMax.einzigeTour().status).isEqualTo("completed");
    }

    @Test
    void loadedDataIsRemovedWhenTaskEnds() {
        ProcessInstance tour = startTour(2);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));

        Task vorbereiten = currentTask(tour);
        assertThat(taskService.getVariable(vorbereiten.getId(), "kunde")).isNotNull();
        taskService.complete(vorbereiten.getId(),
                Map.of("besuchUeberspringen", false, "besuchBegonnenAm", "2026-11-10T09:00:00Z"));

        Task durchfuehren = currentTask(tour);
        assertThat(durchfuehren.getTaskDefinitionKey()).isEqualTo("Task_BesuchDurchfuehren");
        assertThat(runtimeService.getVariable(tour.getId(), "besuchsplan"))
                .as("besuchsplan aus 'Besuchsplan prüfen' ist entfernt").isNull();
        assertThat(kunde(durchfuehren)).containsEntry("kundeId", "1");
    }

    @Test
    void reviewedPlanIsTransferredToTerraMax() {
        ProcessInstance tour = startTour(3);

        // Kunde 2 streichen, Reihenfolge 3 vor 1
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true, "besuchsplan",
                List.of(Map.of("kundeId", "3"), Map.of("kundeId", "1"))));

        assertThat(terraMax.einzigeTour().kundenIds()).containsExactly(3L, 1L);
        assertThat(runtimeService.getVariable(tour.getId(), "anzahlHalte")).isEqualTo(2);
        assertThat(kunde(currentTask(tour))).containsEntry("kundeId", "3");
        taskService.complete(currentTask(tour).getId(),
                Map.of("besuchUeberspringen", true, "ueberspringenGrund", "Kein Bedarf"));
        assertThat(kunde(currentTask(tour))).containsEntry("kundeId", "1");
    }

    @Test
    void reviewWithFewerThanTwoCustomersShowsTaskAgain() {
        ProcessInstance tour = startTour(3);

        taskService.complete(currentTask(tour).getId(),
                Map.of("planBestaetigt", true, "besuchsplan", List.of(Map.of("kundeId", "2"))));

        assertThat(currentTask(tour).getTaskDefinitionKey()).isEqualTo("Task_BesuchsplanPruefen");
        assertThat(besuchsplan(currentTask(tour))).extracting(k -> k.get("kundeId")).containsExactly("2");
    }

    @Test
    void endsWithoutTourWhenFewerThanTwoCustomers() {
        ProcessInstance tour = startTour(1);

        assertEndedAt(tour, "EndEvent_KeineTour");
        assertThat(terraMax.anzahlTouren()).as("keine Tour in TerraMax anlegen").isZero();
    }

    @Test
    void endsWhenPlanIsRejected() {
        ProcessInstance tour = startTour(2);

        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", false));

        assertEndedAt(tour, "EndEvent_TourVerworfen");
        assertThat(terraMax.einzigeTour().status).isEqualTo("discarded");
    }

    @Test
    void abortedTourShowsRemainingVisitsAsOpen() {
        ProcessInstance tour = startTour(3);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));

        runtimeService.createMessageCorrelation("TourAbbrechen").processInstanceId(tour.getId())
                .setVariable("abbruchGrund", "Unwetter").correlate();

        assertThat(terraMax.einzigeTour().status).isEqualTo("aborted");
        assertThat(terraMax.einzigeTour().abbruchGrund).isEqualTo("Unwetter");
        Task abschliessen = currentTask(tour);
        assertThat(abschliessen.getTaskDefinitionKey()).isEqualTo("Task_TourAbschliessen");
        Map<String, Object> uebersicht = map(taskService.getVariable(abschliessen.getId(), "touruebersicht"));
        assertThat(statuses(uebersicht)).containsExactly("OFFEN", "OFFEN", "OFFEN");

        taskService.complete(abschliessen.getId());
        assertThat(terraMax.einzigeTour().status).as("abgebrochen bleibt abgebrochen").isEqualTo("aborted");
    }

    @Test
    void skippingRequiresAReason() {
        ProcessInstance tour = startTour(2);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));
        Task vorbereiten = currentTask(tour);

        assertThatThrownBy(() -> taskService.complete(vorbereiten.getId(), Map.of("besuchUeberspringen", true)))
                .hasMessageContaining("Grund");
        assertThat(currentTask(tour).getId()).as("Aufgabe bleibt offen").isEqualTo(vorbereiten.getId());
    }

    // --- Fehlerbehandlung: fachliche Fehler -> BPMN-Fehler, technische -> Rollback ---

    @Test
    void rejectedPlanEndsWithoutTour() {
        terraMax.fachlicherFehlerBei("planHolen");

        ProcessInstance tour = startTour(3);

        assertEndedAt(tour, "EndEvent_KeineTour");
        assertPassed(tour, "BoundaryEvent_BesuchsplanFehlgeschlagen");
    }

    @Test
    void rejectedSkipEndsOnlyThisVisit() {
        ProcessInstance tour = startTour(2);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));
        terraMax.fachlicherFehlerBei("besuchUeberspringen");

        taskService.complete(currentTask(tour).getId(),
                Map.of("besuchUeberspringen", true, "ueberspringenGrund", "Betrieb geschlossen"));

        assertPassed(tour, "EndEvent_UeberspringenAbgelehnt");
        assertThat(kunde(currentTask(tour))).as("Tour geht mit dem nächsten Kunden weiter").containsEntry("kundeId",
                "2");
        assertThat(terraMax.einzigeTour().statusJeHalt()).containsExactly("OFFEN", "OFFEN");
    }

    @Test
    void rejectedVisitCompletionEndsOnlyThisVisit() {
        ProcessInstance tour = startTour(2);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));
        terraMax.fachlicherFehlerBei("besuchAbschliessen");

        besuchDokumentieren(tour, currentTask(tour), "Herr Huber");

        assertPassed(tour, "EndEvent_AbschlussAbgelehnt");
        assertThat(kunde(currentTask(tour))).containsEntry("kundeId", "2");
    }

    @Test
    void technicalErrorRollsBackAndTaskCanBeRetried() {
        ProcessInstance tour = startTour(2);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));
        Task vorbereiten = currentTask(tour);
        Map<String, Object> ueberspringen = Map.of("besuchUeberspringen", true, "ueberspringenGrund", "Krank");

        terraMax.nichtErreichbar(true);
        assertThatThrownBy(() -> taskService.complete(vorbereiten.getId(), ueberspringen))
                .isInstanceOf(TerraMaxTechnischerFehler.class).hasMessageContaining("nicht erreichbar");
        assertThat(currentTask(tour).getId()).as("Aufgabe bleibt offen").isEqualTo(vorbereiten.getId());
        assertThat(terraMax.einzigeTour().statusJeHalt()).containsExactly("OFFEN", "OFFEN");

        terraMax.nichtErreichbar(false);
        taskService.complete(vorbereiten.getId(), ueberspringen);
        assertThat(terraMax.einzigeTour().statusJeHalt()).containsExactly("UEBERSPRUNGEN", "OFFEN");
    }

    @Test
    void technicalErrorAtStartCreatesNoProcess() {
        terraMax.nichtErreichbar(true);

        long vorher = runtimeService.createProcessInstanceQuery().processDefinitionKey("Besuchstour").count();

        assertThatThrownBy(() -> startTour(3)).isInstanceOf(TerraMaxTechnischerFehler.class);
        assertThat(runtimeService.createProcessInstanceQuery().processDefinitionKey("Besuchstour").count())
                .as("Start wird zurückgerollt").isEqualTo(vorher);
        assertThat(terraMax.anzahlTouren()).isZero();
    }

    // --- Hilfsmethoden ---

    private ProcessInstance startTour(int customers) {
        terraMax.planSetzen(FakeTerraMax.planMitKunden(customers));
        identityService.setAuthentication(SALES, List.of(Roles.SALES_PROFESSIONAL));
        try {
            return runtimeService.startProcessInstanceByKey("Besuchstour", Map.of("tourDatum", "2026-11-10"));
        } finally {
            identityService.clearAuthentication();
        }
    }

    private void besuchDokumentieren(ProcessInstance tour, Task vorbereiten, String gespraechspartner) {
        taskService.complete(vorbereiten.getId(),
                Map.of("besuchUeberspringen", false, "besuchBegonnenAm", "2026-11-10T09:00:00Z"));
        taskService.complete(currentTask(tour).getId(), Map.of("besuchAbgeschlossenAm", "2026-11-10T09:45:00Z"));
        Task sprachnotiz = currentTask(tour);
        assertThat(sprachnotiz.getTaskDefinitionKey()).isEqualTo("Task_SprachnotizAufnehmen");
        taskService.complete(sprachnotiz.getId(), Map.of("sprachnotizVorhanden", false));
        Task protokollPruefen = currentTask(tour);
        assertThat(protokollPruefen.getTaskDefinitionKey()).isEqualTo("Task_ProtokollPruefen");
        assertThat(kunde(protokollPruefen)).isNotNull();
        taskService.complete(protokollPruefen.getId(), Map.of("protokoll",
                Map.of("gespraechspartner", gespraechspartner, "ergebnis", "Interesse an 6R")));
    }

    private Task currentTask(ProcessInstance tour) {
        return taskService.createTaskQuery().processInstanceId(tour.getId()).singleResult();
    }

    private Map<String, Object> kunde(Task task) {
        return map(taskService.getVariable(task.getId(), "kunde"));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> besuchsplan(Task task) {
        return (List<Map<String, Object>>) taskService.getVariable(task.getId(), "besuchsplan");
    }

    private void assertEndedAt(ProcessInstance tour, String endEventId) {
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(tour.getId()).count()).isZero();
        assertPassed(tour, endEventId);
    }

    private void assertPassed(ProcessInstance tour, String activityId) {
        assertThat(historyService.createHistoricActivityInstanceQuery().processInstanceId(tour.getId())
                .activityId(activityId).count()).as(activityId).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> besuche(Map<String, Object> uebersicht) {
        return (List<Map<String, Object>>) uebersicht.get("besuche");
    }

    private static List<Object> statuses(Map<String, Object> uebersicht) {
        return besuche(uebersicht).stream().map(b -> b.get("status")).toList();
    }

    private static int number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }
}
