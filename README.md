# TerraMax

Monorepo für TerraMax. Die einzelnen Projekte liegen in eigenen Ordnern,
z. B. `frontend/` und `hello-service/`, und haben jeweils ein eigenes README.

## Zusammenarbeit

Wie wir mit Branches, Pull Requests, Reviews, Dependabot und GitHub Actions
arbeiten, steht in [docs/dev-workflow.md](docs/dev-workflow.md).

## Java-Microservices

Die Java-Microservices basieren auf Quarkus und liegen jeweils in einem
eigenen Ordner direkt unter dem Repository-Root (z. B. `hello-service/`).

### Code-Formatierung

Java-Code wird mit dem
[formatter-maven-plugin](https://code.revelc.net/formatter-maven-plugin/)
formatiert. Das gemeinsame Eclipse-Formatter-Profil für alle Services liegt
unter `config/formatter/eclipse-java.xml`. Es entspricht dem Standardprofil
des Plugins, behält aber manuelle Zeilenumbrüche bei
(`join_wrapped_lines=false`), damit z. B. Fluent-API-Aufrufketten lesbar
bleiben.

Die Formatierung wird bei jedem Maven-Build geprüft (`compile`, `test`,
`verify`, `package`, …). Ist eine Datei nicht korrekt formatiert, schlägt der
Build fehl – auch in der CI.

Code formatieren (im Ordner des jeweiligen Service):

```bash
./mvnw formatter:format
```

Nur prüfen, ohne Dateien zu ändern:

```bash
./mvnw formatter:validate
```

Am besten vor jedem Commit `./mvnw formatter:format` ausführen.

### Neuen Service anbinden

Neue Services binden das Plugin in ihrer `pom.xml` genauso ein wie
`hello-service`:

```xml
<plugin>
    <groupId>net.revelc.code.formatter</groupId>
    <artifactId>formatter-maven-plugin</artifactId>
    <version>${formatter-plugin.version}</version>
    <configuration>
        <configFile>${project.basedir}/../config/formatter/eclipse-java.xml</configFile>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>validate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Dazu in den `<properties>` die Plugin-Version setzen, z. B.
`<formatter-plugin.version>2.29.0</formatter-plugin.version>`.
Der relative Pfad setzt voraus, dass der Service direkt unter dem
Repository-Root liegt.

## Release: Images versionieren

Jeder Service wird einzeln versioniert ([SemVer](https://winfprojekt.de/docs/technik/semver),
vor dem ersten stabilen Release `0.x.y`). Der Git-Tag bestimmt Service und Version:

| Service | Git-Tag | Image |
| --- | --- | --- |
| frontend | `v1.2.3` | `terramax-frontend` (siehe [frontend/README.md](frontend/README.md)) |
| terramax-service | `terramax-1.2.3` | `terramax-terramax-service` |
| speech-service | `speech-1.2.3` | `terramax-speech-service` |
| notification-service | `notification-1.2.3` | `terramax-notification-service` |
| process-engine | `process-engine-1.2.3` | `terramax-process-engine` |
| tour-service | `tour-1.2.3` | `terramax-tour-service` |
| hello-service | `hello-1.2.3` | `terramax-hello-service` |

```sh
git checkout main && git pull
git tag speech-0.2.0 && git push origin speech-0.2.0   # Image-Tags 0.2.0, 0.2, latest, sha-<commit>
```

Den Tag erst nach dem Merge auf `main` setzen („Rebase and merge“ ändert die Commit-Hashes).
Danach in der Root-`docker-compose.yml` die neue Version eintragen, per PR mergen und in
Portainer den Stack `terramax` neu deployen. `:main` zeigt weiter auf den letzten Stand von `main`.
