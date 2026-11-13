# Image der Process Engine. Vorher bauen: ./mvnw -B verify (erzeugt target/process-engine-*.jar)
FROM eclipse-temurin:25-jre

WORKDIR /app
# Nicht als root laufen; data/ nimmt die lokale H2-Datei auf (auf dem Server besser PostgreSQL)
RUN groupadd --system app && useradd --system --gid app app \
    && mkdir /app/data && chown app:app /app/data
COPY target/process-engine-*.jar app.jar

USER app
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
