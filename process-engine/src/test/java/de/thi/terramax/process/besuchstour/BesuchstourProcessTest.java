package de.thi.terramax.process.besuchstour;

import de.thi.terramax.process.TestPlans;
import de.thi.terramax.process.security.Roles;
import de.thi.terramax.process.tour.TourPlanClient;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Spielt das BPMN-Modell "Besuchstour" (docs/process/besuchstour.bpmn) mit der echten Engine durch. Nur der
 * tour-service wird ersetzt.
 */
@SpringBootTest
@ActiveProfiles("test")
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

    @MockitoBean
    TourPlanClient tourPlanClient;

    @Test
    void completeTourWithOneSkippedAndTwoDocumentedVisits() {
        ProcessInstance tour = startTour(3);

        Task planPruefen = currentTask(tour);
        assertThat(planPruefen.getTaskDefinitionKey()).isEqualTo("Task_BesuchsplanPruefen");
        assertThat(planPruefen.getAssignee()).isEqualTo(SALES);
        taskService.complete(planPruefen.getId(), Map.of("planBestaetigt", true));

        // Kunde 1 wird übersprungen
        Task vorbereiten1 = currentTask(tour);
        assertThat(kundeId(vorbereiten1)).isEqualTo("K-1");
        taskService.complete(vorbereiten1.getId(),
                Map.of("besuchUeberspringen", true, "ueberspringenGrund", "Betrieb geschlossen"));

        // Kunde 2 und 3 werden besucht und dokumentiert
        Task vorbereiten2 = currentTask(tour);
        assertThat(kundeId(vorbereiten2)).isEqualTo("K-2");
        assertThat(runtimeService.getVariable(tour.getId(), "ueberspringenGrund"))
                .as("Grund von Kunde 1 darf nicht in den nächsten Besuch überlaufen")
                .isNull();
        besuchDokumentieren(tour, vorbereiten2, "Herr Huber");

        Task vorbereiten3 = currentTask(tour);
        assertThat(kundeId(vorbereiten3)).isEqualTo("K-3");
        assertThat(runtimeService.getVariable(tour.getId(), "protokoll"))
                .as("Protokoll von Kunde 2 darf nicht in den nächsten Besuch überlaufen")
                .isNull();
        besuchDokumentieren(tour, vorbereiten3, "Frau Leitner");

        Task abschliessen = currentTask(tour);
        assertThat(abschliessen.getTaskDefinitionKey()).isEqualTo("Task_TourAbschliessen");
        Map<String, Object> uebersicht = map(runtimeService.getVariable(tour.getId(), "touruebersicht"));
        assertThat(statuses(uebersicht)).containsExactly("UEBERSPRUNGEN", "DOKUMENTIERT", "DOKUMENTIERT");
        assertThat(number(uebersicht, "anzahlDokumentiert")).isEqualTo(2);
        assertThat(number(uebersicht, "anzahlUebersprungen")).isEqualTo(1);
        assertThat(besuche(uebersicht).get(0)).containsEntry("grund", "Betrieb geschlossen");
        assertThat(map(besuche(uebersicht).get(1).get("protokoll"))).containsEntry("gespraechspartner", "Herr Huber");

        taskService.complete(abschliessen.getId());
        assertEndedAt(tour, "EndEvent_TourAbgeschlossen");
    }

    @Test
    void endsWithoutTourWhenFewerThanTwoCustomers() {
        ProcessInstance tour = startTour(1);

        assertEndedAt(tour, "EndEvent_KeineTour");
    }

    @Test
    void endsWhenPlanIsRejected() {
        ProcessInstance tour = startTour(2);

        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", false));

        assertEndedAt(tour, "EndEvent_TourVerworfen");
    }

    @Test
    void abortedTourShowsRemainingVisitsAsOpen() {
        ProcessInstance tour = startTour(3);
        taskService.complete(currentTask(tour).getId(), Map.of("planBestaetigt", true));

        runtimeService.createMessageCorrelation("TourAbbrechen").processInstanceId(tour.getId()).correlate();

        Task abschliessen = currentTask(tour);
        assertThat(abschliessen.getTaskDefinitionKey()).isEqualTo("Task_TourAbschliessen");
        Map<String, Object> uebersicht = map(runtimeService.getVariable(tour.getId(), "touruebersicht"));
        assertThat(statuses(uebersicht)).containsExactly("OFFEN", "OFFEN", "OFFEN");
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

    // --- Hilfsmethoden ---

    private ProcessInstance startTour(int customers) {
        when(tourPlanClient.fetchPlan(any())).thenReturn(TestPlans.withCustomers(customers));
        identityService.setAuthentication(SALES, List.of(Roles.SALES_PROFESSIONAL));
        try {
            return runtimeService.startProcessInstanceByKey("Besuchstour", Map.of("tourDatum", "2026-11-10"));
        } finally {
            identityService.clearAuthentication();
        }
    }

    private void besuchDokumentieren(ProcessInstance tour, Task vorbereiten, String gespraechspartner) {
        taskService.complete(vorbereiten.getId(),
                Map.of("besuchUeberspringen", false, "besuchBegonnenAm", "2026-11-10T09:00"));
        taskService.complete(currentTask(tour).getId(), Map.of("besuchAbgeschlossenAm", "2026-11-10T09:45"));
        Task sprachnotiz = currentTask(tour);
        assertThat(sprachnotiz.getTaskDefinitionKey()).isEqualTo("Task_SprachnotizAufnehmen");
        taskService.complete(sprachnotiz.getId(), Map.of("sprachnotizVorhanden", false));
        Task protokollPruefen = currentTask(tour);
        assertThat(protokollPruefen.getTaskDefinitionKey()).isEqualTo("Task_ProtokollPruefen");
        taskService.complete(protokollPruefen.getId(), Map.of("protokoll",
                Map.of("gespraechspartner", gespraechspartner, "ergebnis", "Interesse an 6R")));
    }

    private Task currentTask(ProcessInstance tour) {
        return taskService.createTaskQuery().processInstanceId(tour.getId()).singleResult();
    }

    private String kundeId(Task task) {
        return (String) map(taskService.getVariable(task.getId(), "kunde")).get("kundeId");
    }

    private void assertEndedAt(ProcessInstance tour, String endEventId) {
        assertThat(runtimeService.createProcessInstanceQuery().processInstanceId(tour.getId()).count()).isZero();
        assertThat(historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(tour.getId()).activityId(endEventId).count()).isEqualTo(1);
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
