package de.thi.terramax.process.events;

import de.thi.terramax.process.FakeTerraMax;
import de.thi.terramax.process.security.Roles;
import org.cibseven.bpm.engine.IdentityService;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Prüft die Prozess-Events der Besuchstour gegen den Vertrag in docs/kafka/README.md. Statt Kafka wird der Publisher
 * ersetzt; das Senden selbst prüft {@link KafkaProcessEventPublisherTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(FakeTerraMax.Config.class)
class ProcessEventsTest {

    private static final String SALES = "sales";
    private static final String PREFIX = ProcessEvent.TYPE_PREFIX;

    @Autowired
    RuntimeService runtimeService;
    @Autowired
    TaskService taskService;
    @Autowired
    IdentityService identityService;

    @Autowired
    FakeTerraMax terraMax;
    @MockitoBean
    ProcessEventPublisher publisher;

    @BeforeEach
    void reset() {
        clearInvocations(publisher);
        terraMax.reset();
    }

    @Test
    void completeTourPublishesEventsInOrder() {
        ProcessInstance tour = startTour(2);
        complete(tour, Map.of("planBestaetigt", true));
        complete(tour, Map.of("besuchUeberspringen", true, "ueberspringenGrund", "Betrieb geschlossen"));
        complete(tour, Map.of("besuchUeberspringen", false, "besuchBegonnenAm", "2026-11-10T09:00:00Z"));
        complete(tour, Map.of());
        complete(tour, Map.of("sprachnotizVorhanden", false));
        complete(tour, Map.of("protokoll", Map.of("ergebnis", "Interesse an 6R")));
        complete(tour, Map.of());

        List<ProcessEvent> events = events();
        assertThat(events).extracting(ProcessEvent::type).containsExactly(
                PREFIX + "tour.started",
                PREFIX + "task.created", // Besuchsplan prüfen
                PREFIX + "task.created", // Besuch vorbereiten Kunde 1
                PREFIX + "visit.skipped",
                PREFIX + "task.created", // Besuch vorbereiten Kunde 2
                PREFIX + "task.created", // Besuch durchführen
                PREFIX + "task.created", // Sprachnotiz aufnehmen
                PREFIX + "task.created", // Protokoll prüfen
                PREFIX + "visit.documented",
                PREFIX + "task.created", // Tour abschließen
                PREFIX + "tour.completed");

        assertThat(events).allSatisfy(event -> {
            assertThat(event.specversion()).isEqualTo("1.0");
            assertThat(event.source()).isEqualTo("/process-engine");
            assertThat(event.subject()).isEqualTo(tour.getId());
            assertThat(event.userid()).isEqualTo(SALES);
            assertThat(event.datacontenttype()).isEqualTo("application/json");
            assertThat(event.data()).containsEntry("processInstanceId", tour.getId());
        });
        assertThat(events).extracting(ProcessEvent::id).doesNotHaveDuplicates();

        long tourId = terraMax.einzigeTour().id;
        assertThat(events.get(0).data()).containsEntry("tourDatum", "2026-11-10");
        assertThat(events.get(10).data()).containsEntry("tourId", tourId);
        assertThat(events.get(1).data())
                .containsEntry("taskDefinitionKey", "Task_BesuchsplanPruefen")
                .containsEntry("name", "Besuchsplan prüfen")
                .containsEntry("formKey", "terramax:besuchsplan-pruefen")
                .containsKey("taskId");
        assertThat(events.get(3).data()).containsEntry("kundeId", "1").containsEntry("position", 1)
                .containsEntry("tourId", tourId)
                .doesNotContainKey("ueberspringenGrund");
        assertThat(events.get(8).data()).containsEntry("kundeId", "2").containsEntry("position", 2)
                .containsEntry("sprachnotizId", null)
                .doesNotContainKey("protokoll");
        Map<String, Object> completed = events.get(10).data();
        assertThat(number(completed, "dokumentiert")).isEqualTo(1);
        assertThat(number(completed, "uebersprungen")).isEqualTo(1);
        assertThat(number(completed, "offen")).isZero();
    }

    @Test
    void tourWithTooFewCustomersIsNotPossible() {
        startTour(1);

        ProcessEvent last = events().getLast();
        assertThat(last.type()).isEqualTo(PREFIX + "tour.not-possible");
        assertThat(last.data()).containsEntry("kundenAnzahl", 1).containsEntry("tourDatum", "2026-11-10");
    }

    @Test
    void rejectedPlanDiscardsTour() {
        ProcessInstance tour = startTour(2);
        complete(tour, Map.of("planBestaetigt", false));

        assertThat(events().getLast().type()).isEqualTo(PREFIX + "tour.discarded");
    }

    @Test
    void cancelledTourPublishesCancelled() {
        ProcessInstance tour = startTour(2);
        complete(tour, Map.of("planBestaetigt", true));

        runtimeService.createMessageCorrelation("TourAbbrechen").processInstanceId(tour.getId()).correlate();

        assertThat(events()).extracting(ProcessEvent::type)
                .contains(PREFIX + "tour.cancelled")
                .last().isEqualTo(PREFIX + "task.created");
    }

    @Test
    void rejectedVisitCompletionPublishesNoDocumentedEvent() {
        ProcessInstance tour = startTour(2);
        complete(tour, Map.of("planBestaetigt", true));
        terraMax.fachlicherFehlerBei("besuchAbschliessen");
        complete(tour, Map.of("besuchUeberspringen", false));
        complete(tour, Map.of());
        complete(tour, Map.of("sprachnotizVorhanden", false));
        clearInvocations(publisher);

        complete(tour, Map.of("protokoll", Map.of("ergebnis", "Interesse an 6R")));

        assertThat(events()).extracting(ProcessEvent::type).doesNotContain(PREFIX + "visit.documented");
    }

    @Test
    void failedStepPublishesNothing() {
        ProcessInstance tour = startTour(2);
        complete(tour, Map.of("planBestaetigt", true));
        clearInvocations(publisher);

        Task vorbereiten = taskService.createTaskQuery().processInstanceId(tour.getId()).singleResult();
        assertThatThrownBy(() -> taskService.complete(vorbereiten.getId(), Map.of("besuchUeberspringen", true)))
                .hasMessageContaining("Grund");

        verify(publisher, never()).publish(any());
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

    private void complete(ProcessInstance tour, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery().processInstanceId(tour.getId()).singleResult();
        taskService.complete(task.getId(), variables);
    }

    private List<ProcessEvent> events() {
        ArgumentCaptor<ProcessEvent> captor = ArgumentCaptor.forClass(ProcessEvent.class);
        verify(publisher, atLeast(1)).publish(captor.capture());
        return captor.getAllValues();
    }

    private static int number(Map<String, Object> map, String key) {
        return ((Number) map.get(key)).intValue();
    }
}
