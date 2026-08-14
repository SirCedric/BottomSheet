# Publishing-Plugin für AGP 9 und das Central Portal

Recherche zu [#21](https://github.com/SirCedric/BottomSheet/issues/21). Stand: 2026-08-14.

Untersucht wurde, womit `:bottomsheet` gebaut und nach Maven Central hochgeladen wird und ob das
unter dem Toolchain-Stand des Repos trägt: AGP 9.3.1, Gradle 9.7.0, Kotlin 2.4.10 über AGP,
Configuration Cache an.

Belege sind durchgehend Primärquellen: die Dokumentation von Sonatype Central, die Gradle-
Userguide, die Android-Release-Notes und der Quelltext des vanniktech-Plugins am Tag `0.37.0`.
Wo etwas nur plausibel ist, steht es unter [Nicht belegt](#nicht-belegt).

## Empfehlung

`com.vanniktech.maven.publish` in Version **0.37.0**, konfiguriert mit
`publishToMavenCentral(automaticRelease = true)`. Der Release-Workflow wird damit **ein Stück**:
ein einziger Gradle-Aufruf lädt hoch, wartet die Validierung ab und gibt frei, ohne dass jemand
das Portal öffnet.

`maven-publish` von Hand ist möglich, aber der Weg endet nicht dort, wo er anfängt: Gradle kann
nur in ein Maven-Repository schreiben, das Central Portal nimmt Releases jedoch als ZIP-Bundle
über ein REST-API entgegen (siehe [Frage 2](#2-das-central-portal-upload-api)). Den Teil müsste
man selbst bauen — Bundle zippen, hochladen, Status pollen, freigeben, im Fehlerfall droppen.
Genau das ist der Teil, den das Plugin abnimmt.

## Das Risiko vorweg: die Versionslücke

**Das Plugin ist nicht gegen unseren Toolchain-Stand getestet.** 0.37.0 nennt als
„Compatibility tested up to" unter anderem AGP 9.3.0-rc01 und Gradle 9.7.0-milestone-1
([CHANGELOG 0.37.0](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/CHANGELOG.md)).
Wir fahren AGP **9.3.1** und Gradle **9.7.0 final** — beides jeweils eine Stufe jenseits der
getesteten Matrix. Die Mindestversionen (JDK 17, Gradle 9.0.0, AGP 8.13.0, KGP 2.2.0) sind
erfüllt.

Das ist deshalb eine ernstzunehmende Lücke und keine Fußnote, weil dieses Repo an derselben
Stelle schon einmal getroffen wurde: laut `CLAUDE.md` registriert weder die KGP-eigene
ABI-Validierung noch der `binary-compatibility-validator` in `:bottomsheet` überhaupt Tasks, weil
Kotlin über AGP kommt und es kein konventionelles Kotlin-Target zum Andocken gibt.

**Praktisch verifiziert — es trägt.** Ich habe das Plugin auf diesem Branch versuchsweise an
`:bottomsheet` gehängt und wieder entfernt. Ergebnis:

| Prüfung | Ergebnis |
| --- | --- |
| `:bottomsheet:publishToMavenLocal --configuration-cache` | `BUILD SUCCESSFUL`, `Configuration cache entry stored.` |
| derselbe Aufruf ein zweites Mal | `BUILD SUCCESSFUL in 1s`, `Configuration cache entry reused.` |
| erzeugte Artefakte | `.aar`, `-sources.jar`, `-javadoc.jar`, `.pom`, `.module` |
| `:bottomsheet:tasks --group publishing` mit `publishToMavenCentral()` | alle Central-Tasks registriert |

Die Sorge trifft hier also nicht zu, und der Grund dafür ist strukturell: Das Plugin dockt **nicht
an einem Kotlin-Target an**, sondern an der Plugin-ID `com.android.library` und an AGPs eigener
`androidComponents.finalizeDsl`-Schnittstelle
([`MavenPublishPlugin.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishPlugin.kt),
Zeilen 24–31). Die Auswahl der Plattform prüft in `configureBasedOnAppliedPlugins` ausschließlich
`project.plugins.hasPlugin("com.android.library")`
([`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt),
Zeile 487). Ob KGP als eigenständiges Plugin appliziert ist, spielt keine Rolle. Das ist der
Unterschied zur ABI-Validierung, die ein Kotlin-Target braucht.

Bewertung: **belegt für heute, nicht auf Dauer garantiert.** Beim nächsten AGP- oder
Gradle-Sprung ist die Lücke neu zu prüfen; der `publishToMavenLocal`-Probelauf oben ist ein
billiger und aussagekräftiger Rauchtest dafür.

Der erzeugte POM trägt die Konstruktion des Moduls korrekt nach außen: die Compose-BOM steht als
`<dependencyManagement>`-Import drin, `api`-Abhängigkeiten landen in `compile`,
`implementation`-Abhängigkeiten in `runtime`, und AGP fügt `kotlin-stdlib` in 2.4.10 hinzu. Die
`api`/BOM-Konstruktion überlebt das Publizieren also unbeschadet.

## 1. vanniktech vs. `maven-publish` von Hand

### Version und Configuration Cache

- **0.37.0**, veröffentlicht am 2026-06-21, ist die aktuelle Version
  ([CHANGELOG](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/CHANGELOG.md)).
- Configuration-Cache-Support fürs Publizieren kam in **0.34.0**
  ([CHANGELOG 0.34.0](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/CHANGELOG.md)).
  Der Zustand über die Build-Grenze hinweg liegt in einem `BuildService`
  ([`MavenCentralBuildService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/central/MavenCentralBuildService.kt)),
  nicht in Task-Feldern — das ist die Bauform, die der Configuration Cache verlangt.
- Praktisch bestätigt durch den Probelauf oben, inklusive Wiederverwendung des Cache-Eintrags.

### Was es abnimmt

- **Android-Variant-Auswahl.** Es ruft AGPs `singleVariant("release") { withSourcesJar();
  withJavadocJar() }` auf und legt daraus die `MavenPublication` an
  ([`Platform.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/Platform.kt),
  Zeilen 184–213). Die Variante ist per `ANDROID_VARIANT_TO_PUBLISH` überschreibbar, Default ist
  `release`.
- **Sources- und Javadoc-JAR.** Beide entstehen über AGP. Im Probelauf hat AGPs
  `javaDocReleaseGeneration` die Doku erzeugt — ein Dokka-Plugin muss dafür auf unserer Seite
  nicht appliziert werden. Central verlangt beide JARs; Platzhalter wären erlaubt, sind hier aber
  nicht nötig
  ([Requirements](https://central.sonatype.org/publish/requirements/)).
- **POM.** Name, Beschreibung, URL, Lizenz, Developer und SCM sind Pflicht
  ([Requirements](https://central.sonatype.org/publish/requirements/)); das Plugin bietet dafür
  DSL und `gradle.properties`
  ([Docs, Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)).
- **Signing.** `signAllPublications()` appliziert Gradles `signing`-Plugin, verdrahtet den
  In-Memory-Key und setzt `required` auf „alles außer `-SNAPSHOT`"
  ([`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt),
  Zeilen 232–246).
- **Bundle und Portal-Upload.** Es sammelt das lokal erzeugte Repository in eine ZIP, lädt sie
  ans Portal, pollt den Status, gibt frei und räumt im Fehlerfall auf — der ganze Block in
  `runEndOfBuildActions`
  ([`MavenCentralBuildService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/central/MavenCentralBuildService.kt),
  Zeilen 137–197).
- **Überflüssige Checksums.** Seit 0.37.0 werden `.asc`-Checksums sowie `sha256`/`sha512`
  standardmäßig nicht mitpubliziert, weil weder Gradle noch Central sie lesen
  ([CHANGELOG 0.37.0](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/CHANGELOG.md)).

### Registrierte Tasks

Mit `publishToMavenCentral()` in der DSL entstehen unter anderem — im Probelauf per
`tasks --group publishing` gesehen:

- `publishToMavenCentral` — lädt hoch
- `publishAndReleaseToMavenCentral` — lädt hoch und gibt frei
- `dropMavenCentralDeployment` — zieht ein Deployment per ID zurück
- `publishAllPublicationsToMavenCentralRepository`, `publishMavenPublicationToMavenLocal`, …

### Der Handbetrieb

`maven-publish` allein deckt Schritt 1 ab und hört dann auf. Für Central bräuchte man zusätzlich
`singleVariant`-Konfiguration, POM-Befüllung, `signing` mit In-Memory-Key, das Zippen des Bundles
und einen eigenen Client gegen das Portal-REST-API samt Status-Polling und Fehleraufräumen.
Sonatype selbst führt Gradles `maven-publish` nur unter der Kompatibilitätsschicht „OSSRH Staging
API" auf, und dort ausdrücklich mit dem Zusatz „Requires using the manual endpoints"
([OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/)).
Das ist ein Migrationspfad für Altbestand, kein Ziel für einen neuen Workflow — Sonatype schreibt
selbst, man hoffe, dass Plugins „over time … adopt the Portal API rather than rely on this
service".

## 2. Das Central-Portal-Upload-API

**OSSRH ist zum 30. Juni 2025 abgeschaltet worden**, alle Namespaces sind ins Central Publisher
Portal migriert ([OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/)).

Der geltende Weg ist ein Bundle-Upload per REST
([Portal Publisher API](https://central.sonatype.org/publish/publish-portal-api/)):

| Schritt | Aufruf |
| --- | --- |
| Hochladen | `POST /api/v1/publisher/upload`, `multipart/form-data`, Part `bundle`, liefert die Deployment-ID |
| Status | `POST /api/v1/publisher/status?id=<deploymentId>` |
| Freigeben | `POST /api/v1/publisher/deployment/<deploymentId>` |
| Verwerfen | `DELETE /api/v1/publisher/deployment/<deploymentId>` |

Authentifiziert wird mit einem **User Token** aus dem Portal, base64-kodiert als
`username:password`, im Header `Authorization: Bearer <base64>`. Das ist ausdrücklich **nicht**
das Login-Passwort.

### Ja, es gibt einen Modus ohne zweiten Handgriff

Der Query-Parameter `publishingType` beim Upload kennt zwei Werte:

- **`AUTOMATIC`** — „a deployment will go through validation and, if it passes, automatically
  proceed to publish to Maven Central"
- **`USER_MANAGED`** — Default, „require the user to manually publish it via the Portal UI"

Damit ist die Kernfrage des Tickets beantwortet: **ein vollautonomer Release ist möglich.**

### Wie das Plugin es einstellt

Der `publishingType` wird nicht direkt gesetzt, sondern folgt daraus, ob die Freigabe Teil des
Builds ist:

```kotlin
val publishingType = if (actions.contains(EndOfBuildAction.Publish)) AUTOMATIC else USER_MANAGED
```

([`MavenCentralBuildService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/central/MavenCentralBuildService.kt),
Zeilen 165–169). `EndOfBuildAction.Publish` wird von `enableAutomaticPublishing` hinzugefügt,
also genau dann, wenn `automaticRelease` aktiv ist.

**Welcher Wert wofür sorgt:**

| Konfiguration | `publishingType` | Was am Ende passiert |
| --- | --- | --- |
| `publishToMavenCentral()` bzw. Task `publishToMavenCentral` | `USER_MANAGED` | Deployment landet auf `VALIDATED` und **bleibt dort liegen**, bis jemand im Portal auf „Publish" klickt |
| `publishToMavenCentral(automaticRelease = true)` oder Task `publishAndReleaseToMavenCentral` oder `mavenCentralAutomaticPublishing=true` | `AUTOMATIC` | Portal validiert und publiziert selbstständig weiter — **kein menschlicher Handgriff** |

Alle drei Varianten der zweiten Zeile sind gleichwertig
([Docs, Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)).

### `DeploymentValidation` — wie lange der Build wartet

Orthogonal dazu steht, wie lange der Gradle-Build dem Deployment hinterherschaut
([`MavenCentralBuildService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/central/MavenCentralBuildService.kt),
Zeilen 96–110):

| Wert | Verhalten | Was liegen bleibt |
| --- | --- | --- |
| `VALIDATED` (Default) | wartet bis `VALIDATED`, bricht bei `FAILED` den Build ab | nichts, aber der Build endet, bevor Central fertig hochgeladen hat |
| `PUBLISHED` | wartet bis `PUBLISHED` | nichts; teuerste, aber ehrlichste Variante für ein CI-Gate |
| `NONE` | kehrt direkt nach dem Upload zurück | ein Fehlschlag der Validierung fällt dem Build **nicht** auf |

Gepollt wird alle 5 Sekunden (`SONATYPE_POLL_INTERVAL_SECONDS`), Timeout 60 Minuten
(`SONATYPE_CLOSE_TIMEOUT_SECONDS`)
([Docs, Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)).

**Wichtig für die Erwartungshaltung:** Auch bei `AUTOMATIC` und `PUBLISHED` ist das Artefakt nicht
sofort auflösbar. Die Doku nennt für den letzten Schritt — Verteilung nach Maven Central — 10 bis
30 Minuten
([Docs, Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)).

**Empfehlung für den Release-Workflow:** `automaticRelease = true` mit
`validateDeployment = DeploymentValidation.PUBLISHED`. Dann ist der grüne Build gleichbedeutend
mit „liegt auf Central", und ein rotes CI ist ein echtes Signal statt eines Rätsels.

Für Snapshots gilt ein anderer Weg: `-SNAPSHOT`-Versionen gehen per gewöhnlichem Maven-Upload an
`https://central.sonatype.com/repository/maven-snapshots/`, ohne Bundle, ohne Freigabe, und
Signing ist dort nicht verpflichtend
([Docs, Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/);
[`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt),
Zeilen 114–128).

## 3. In-Memory-Signing

Das Plugin reicht den Key unverändert an Gradles `signing`-Plugin durch:

```kotlin
project.gradleSigning.useInMemoryPgpKeys(inMemoryKeyId.orNull, inMemoryKey.get(), inMemoryKeyPassword.get())
```

([`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt),
Zeile 244). Maßgeblich ist damit, was Gradle erwartet: **den geheimen Schlüssel im
ASCII-armored-Format**, kein base64-Wrapper
([Gradle 9.7.0, Signing Plugin](https://docs.gradle.org/9.7.0/userguide/signing_plugin.html)).

Drei Properties, jeweils auch als Umgebungsvariable mit Präfix `ORG_GRADLE_PROJECT_` nutzbar
([Docs, Secrets](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)):

| Property | Pflicht | Inhalt |
| --- | --- | --- |
| `signingInMemoryKey` | ja | ASCII-armored privater Schlüssel, erzeugt mit `gpg --export-secret-keys --armor <key id>` |
| `signingInMemoryKeyId` | nein | Key-ID; laut Gradle-Doku nur nötig, wenn ein **Subkey** verwendet wird |
| `signingInMemoryKeyPassword` | nein | Passphrase, sofern der Key eine hat; Default ist der Leerstring |

Dazu die Portal-Credentials `mavenCentralUsername` / `mavenCentralPassword` — das sind
**User-Token-Werte aus dem Portal**, nicht die Login-Daten.

### Widerspruch in den Quellen, ungeklärt

Die Doku-Seite sagt zum exportierten Block: „Make sure to copy this string in its entirety",
also samt `-----BEGIN PGP PRIVATE KEY BLOCK-----` und Zeilenumbrüchen
([Docs, In memory GPG key](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)).
Das KDoc derselben Version sagt das Gegenteil: „The exported key is taken without the first line
and without the last 2 lines, all line breaks should be removed as well"
([`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt),
Zeilen 225–227).

Die Gradle-Doku spricht schlicht von „ascii-armored format", was für den vollständigen Block
spricht; das KDoc wirkt wie ein Rest aus älteren Zeiten. **Ich konnte das nicht praktisch
entscheiden** — siehe [Nicht belegt](#nicht-belegt). Für T2 heißt das: den vollständigen Block als
Secret hinterlegen und beim ersten Release-Durchlauf zusehen; schlägt die Signatur fehl, ist die
gestrippte Variante der nächste Versuch.

### Keyserver

Central akzeptiert genau drei ([GPG-Requirements](https://central.sonatype.org/publish/requirements/gpg/)):

- `keyserver.ubuntu.com`
- `keys.openpgp.org`
- `pgp.mit.edu`

Hochgeladen wird mit `gpg --keyserver <server> --send-keys <keyid>`. Das SKS-Netz ist deprecated
und keine Option mehr.

### Namespace

`dev.sircedric` ist ein DNS-basierter Namespace und muss vor dem ersten Release im Portal über die
Domain verifiziert werden
([Namespace registrieren](https://central.sonatype.org/register/namespace/)). Ein
`io.github.SirCedric` wäre der Weg ohne eigene Domain — bei Anmeldung über GitHub wird er meist
automatisch provisioniert.

## 4. Fehlerverhalten

**Die Versionsnummer ist nicht verbrannt, solange nichts publiziert wurde.** Es gibt genau einen
Punkt ohne Wiederkehr, und der liegt hinter der Freigabe.

Die Zustände eines Deployments
([Portal Publisher API](https://central.sonatype.org/publish/publish-portal-api/)):

| Zustand | Bedeutung |
| --- | --- |
| `PENDING` | hochgeladen, wartet auf die Validierung |
| `VALIDATING` | wird validiert |
| `VALIDATED` | hat die Validierung bestanden, wartet auf Freigabe |
| `PUBLISHING` | freigegeben, wird nach Central hochgeladen |
| `PUBLISHED` | liegt auf Central |
| `FAILED` | Fehler, Details im Feld `errors` |

`VALIDATED` ist der **zurücknehmbare Zwischenzustand** zwischen „hochgeladen" und „freigegeben".
Deployments in `VALIDATED` oder `FAILED` lassen sich per `DELETE` verwerfen; nichts davon hat
Central je erreicht, dieselbe Versionsnummer kann erneut hochgeladen werden.

Ab `PUBLISHED` ist Schluss: „In order to provide reliable access to open source components we do
not remove or modify components once they are publicly available." Der einzige vorgesehene Weg
nach einem Fehler ist ein neues Release mit erhöhter Versionsnummer
([Immutability](https://central.sonatype.org/publish/requirements/immutability/)).

### Das Plugin räumt selbst auf

Das ist der beruhigende Teil für CI. Beim Registrieren eines Projekts trägt das Plugin sofort eine
Aufräumaktion ein:

```kotlin
endOfBuildActions += EndOfBuildAction.Upload
endOfBuildActions += EndOfBuildAction.Drop(runAfterFailure = true)
```

Am Buildende wird nach Erfolg oder Misserfolg unterschieden:

```kotlin
override fun close() {
  if (buildIsSuccess) {
    runEndOfBuildActions(endOfBuildActions.filter { !it.runAfterFailure })
  } else {
    try { runEndOfBuildActions(endOfBuildActions.filter { it.runAfterFailure }) } catch (_: IOException) {}
  }
}
```

([`MavenCentralBuildService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/central/MavenCentralBuildService.kt),
Zeilen 85–96 und 127–135)

Daraus folgt:

- **Build schlägt vor dem Upload fehl** — es wird gar nicht erst hochgeladen, es gibt kein
  Deployment.
- **Build schlägt nach dem Upload fehl**, etwa weil die Validierung `FAILED` meldet — das
  Deployment wird per `DELETE` verworfen. Die Version bleibt frei.
- **Abgebrochener Build**, bei dem `close()` nicht mehr läuft — dann bleibt ein Deployment in
  `PENDING`/`VALIDATED` stehen. Dafür gibt es `./gradlew dropMavenCentralDeployment
  --deployment-id=<id>`; die ID wird seit 0.37.0 nach dem Upload geloggt
  ([CHANGELOG 0.37.0](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/CHANGELOG.md)),
  und im Portal ist sie ohnehin sichtbar.

Ein Hinweis von Sonatype zum Aufräumen: bei einem `FAILED`-Deployment, für das man Support
anfragen will, **nicht** droppen — die Dateien sind für die Diagnose nützlich
([Portal Publisher API](https://central.sonatype.org/publish/publish-portal-api/)).

## Nicht belegt

Sauber getrennt: alles Folgende ist plausibel, aber nicht durch eine Primärquelle oder ein
Experiment abgesichert.

- **Das Key-Format im Streitfall.** Ob Gradles `useInMemoryPgpKeys` neben dem vollständigen
  Armored-Block auch die im KDoc beschriebene gestrippte Einzeiler-Form akzeptiert, konnte ich
  nicht ausprobieren: die Erzeugung eines Wegwerf-Keys scheiterte daran, dass `gpg-agent` unter dem
  langen Worktree-Pfad keinen Socket anlegen kann („File name too long"). Meine Vermutung ist,
  dass beide Formen funktionieren, weil die zugrundeliegende BouncyCastle-Implementierung tolerant
  parst — **das ist eine Vermutung, kein Befund.**
- **Ein echter Upload wurde nie ausgeführt.** Der Probelauf ging gegen `mavenLocal`. Alles zum
  Verhalten des Portals stammt aus Sonatypes Dokumentation und aus dem Client-Code, nicht aus
  einem beobachteten Release. Insbesondere ungetestet: ob eine `AUTOMATIC`-Freigabe tatsächlich
  ohne jeden Portal-Besuch durchläuft, wenn der Namespace frisch verifiziert ist.
- **Doppelter Upload derselben Version.** Was passiert, wenn zu einer Version bereits ein
  Deployment in `VALIDATED` liegt und ein zweites mit derselben Version hochgeladen wird, sagt
  weder die Portal-Doku noch der Client-Code. Plausibel ist, dass es angenommen wird und zwei
  Deployments nebeneinander stehen — nachgesehen habe ich es nicht.
- **Verhalten künftiger AGP-Versionen.** Der Nachweis oben gilt für AGP 9.3.1 und Gradle 9.7.0.
  Dass die Verdrahtung über `androidComponents.finalizeDsl` auch AGP 9.4 überlebt, ist
  wahrscheinlich, aber unbelegt.
- **Ob `explicitApi()` das Publizieren berührt.** Im Probelauf gab es keinerlei Reibung, aber ich
  habe nicht gezielt geprüft, ob strict-Modus und Sources-JAR in irgendeinem Randfall
  interagieren.

## Quellen

Sonatype Central:

- [Portal Publisher API](https://central.sonatype.org/publish/publish-portal-api/)
- [OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/)
- [OSSRH Staging API](https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/)
- [Requirements](https://central.sonatype.org/publish/requirements/)
- [GPG-Requirements](https://central.sonatype.org/publish/requirements/gpg/)
- [Immutability](https://central.sonatype.org/publish/requirements/immutability/)
- [Namespace registrieren](https://central.sonatype.org/register/namespace/)

Gradle und Android:

- [Gradle 9.7.0, Signing Plugin](https://docs.gradle.org/9.7.0/userguide/signing_plugin.html)
- [AGP 9.0 Release Notes — Built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes)
- [AGP 9.3.0 Release Notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes)

vanniktech `gradle-maven-publish-plugin`, Tag `0.37.0`:

- [CHANGELOG](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/CHANGELOG.md)
- [Doku, Maven Central](https://vanniktech.github.io/gradle-maven-publish-plugin/central/)
- [Doku, Was publiziert wird](https://vanniktech.github.io/gradle-maven-publish-plugin/what/)
- [`MavenPublishPlugin.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishPlugin.kt)
- [`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt)
- [`Platform.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/Platform.kt)
- [`MavenCentralBuildService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/central/MavenCentralBuildService.kt)
- [`SonatypeCentralPortal.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/central-portal/src/main/kotlin/com/vanniktech/maven/publish/portal/SonatypeCentralPortal.kt)
- [`SonatypeCentralPortalService.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/central-portal/src/main/kotlin/com/vanniktech/maven/publish/portal/SonatypeCentralPortalService.kt)
