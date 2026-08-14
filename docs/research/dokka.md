# Recherche: Dokka unter AGP 9 und Kotlin 2.4

Zu [Issue #22](https://github.com/SirCedric/BottomSheet/issues/22). Stand: 2026-08-14.

Die Kennzeichnungen sind durchgehend:

- **[belegt]** — steht so in einer Primärquelle (Release Notes, offizielle Doku, Quelltext, ausgeliefertes Artefakt); Link oder Fundstelle steht dabei.
- **[verifiziert]** — an diesem Repo praktisch nachgestellt, mit Kommando und Ausgabe.
- **[unbelegt]** — Schlussfolgerung oder Extrapolation, kein Beleg gefunden.

## Ergebnis

Die Frage „trägt Dokka?" hat sich während der Recherche verschoben. **AGP 9 ruft selbst Dokka auf** — dieselbe Version 2.2.0, die wir sonst von Hand applizieren würden. Die Wahl steht also nicht zwischen „Dokka" und „kein Dokka", sondern zwischen **zwei Aufrufwegen desselben Werkzeugs** und, entscheidender, zwischen **zwei Ausgabeformaten**.

Beide Wege liefern echte Doku aus dem KDoc. Ein leeres Formerfüllungs-JAR ist in keinem Fall nötig.

| | Weg A: AGP eingebaut | Weg B: Dokka-Plugin explizit |
| --- | --- | --- |
| Zusätzliches Plugin | **keines** | `org.jetbrains.dokka` |
| Task | `javaDocReleaseGeneration` → `javaDocReleaseJar` | `dokkaGeneratePublicationHtml` + eigene `Jar`-Task |
| Dokka-Version | 2.2.0, in AGP fest verdrahtet | 2.2.0, von uns gewählt |
| Analyse-Engine | **K1** (`analysis-kotlin-descriptors`) — von JetBrains als deprecated geführt | **K2** (`analysis-kotlin-symbols`) — Vorgabe seit 2.1.0 |
| Format | Javadoc | HTML (Javadoc wäre auch möglich) |
| KDoc-Fließtext | vorhanden | vorhanden |
| `@param`-Texte | **nach dem ersten Satz abgeschnitten** (9 von 15 betroffen) | vollständig |
| Signaturen | Kotlin-as-Java: `bottomSheet(Modifier $self, …)`, ohne Defaults, ohne `@Composable` | idiomatisch Kotlin, mit allen Default-Werten |
| Modulname im Titel | `:bottomsheet` (Gradle-Pfad, nicht änderbar) | `bottomsheet`, über `dokka { moduleName }` frei |
| Steuerbarkeit | **keine** — genau zwei Schalter, `withSourcesJar()` / `withJavadocJar()` | volle DGP-Konfiguration |
| Configuration Cache | ja | ja |
| `internal/` ausgeschlossen | ja | ja |

**Empfehlung: Weg B, HTML-Format.** Nicht wegen der Kosmetik, sondern wegen des `@param`-Abschnitts: Weg A wirft die Hälfte der Parameterdokumentation weg, und zwar stillschweigend. Betroffen sind unter anderem die TalkBack-Regel bei `interactiveDismissEnabled`, die Verwerfungsregel für `dragHandle` bei `gesturesEnabled` und die Lebensdauer von `content` — genau die Sätze, die die API erst benutzbar machen. Das ist derselbe Verlust wie bei einem leeren JAR, nur weniger sichtbar.

Wer „ein Plugin weniger" höher gewichtet, bekommt mit Weg A trotzdem eine vertretbare, echte Doku. Es ist ein bewusster Handel, kein Versehen — und deshalb steht er unten in voller Länge.

## Versionsstand

| Artefakt | Aktuell | Quelle |
| --- | --- | --- |
| Dokka Gradle Plugin | **2.2.0**, veröffentlicht 2026-03-26 | [Gradle Plugin Portal, `maven-metadata.xml`](https://plugins.gradle.org/m2/org/jetbrains/dokka/org.jetbrains.dokka.gradle.plugin/maven-metadata.xml) · [GitHub Releases](https://github.com/Kotlin/dokka/releases) |
| Dokka in AGP 9.3.1 | **2.2.0**, fest verdrahtet | AGP-Artefakt, siehe Weg A |
| `com.vanniktech.maven.publish` | **0.37.0**, veröffentlicht 2026-06-21 | [Maven Central, `maven-metadata.xml`](https://repo1.maven.org/maven2/com/vanniktech/gradle-maven-publish-plugin/maven-metadata.xml) |

**[belegt]** 2.2.0 ist die neueste Dokka-Version. Vorgänger: 2.1.0 (2025-10-15), 2.0.0 (2024-12-16). Für Weg B ist 2.2.0 auch die einzig brauchbare, weil die AGP-9-Unterstützung erst dort dazukam.

Gradle 9.7.0 ist vom 2026-08-06 ([`services.gradle.org/versions/all`](https://services.gradle.org/versions/all)) und damit vier Monate **jünger** als Dokka 2.2.0. Getestet haben kann JetBrains diese Kombination nicht; dass sie läuft, ist unten verifiziert.

---

# Weg A: der eingebaute Pfad von AGP

## Was AGP tatsächlich tut

**[belegt]** AGP 9.3.1 liefert eine Task-Klasse aus, die Dokka in einem Gradle-Worker startet. Im ausgelieferten Artefakt `com.android.tools.build:gradle:9.3.1` liegen:

```
com/android/build/gradle/tasks/JavaDocGenerationTask$DokkaWorkAction.class
com/android/build/gradle/tasks/JavaDocJarTask.class
com/android/build/gradle/internal/services/DokkaParallelBuildService.class
```

**[belegt]** Die Konstanten in `JavaDocGenerationTask` nennen Version und Artefakte wörtlich:

```
DOKKA_VERSION        2.2.0
DOKKA_GROUP          org.jetbrains.dokka
DOKKA_CORE           org.jetbrains.dokka:dokka-core:2.2.0
DOKKA_BASE_PLUGIN    org.jetbrains.dokka:dokka-base:2.2.0
DOKKA_JAVADOC_PLUGIN org.jetbrains.dokka:javadoc-plugin:2.2.0
DOKKA_ANALYSIS       org.jetbrains.dokka:analysis-kotlin-descriptors:2.2.0
```

Drei Dinge stehen damit fest:

1. Es ist **exakt Dokka 2.2.0** — dieselbe Version wie in Weg B.
2. Es ist das **Javadoc-Format** (`javadoc-plugin`), nie HTML.
3. Es ist die **K1-Analyse** (`analysis-kotlin-descriptors`), nicht die K2-Analyse (`analysis-kotlin-symbols`), die Dokka seit 2.1.0 als Vorgabe fährt.

**[belegt]** AGP umgeht dabei das Dokka-Gradle-Plugin komplett und spricht die Bootstrap-API direkt an — im `DokkaWorkAction` finden sich `org/jetbrains/dokka/DokkaBootstrapImpl`, `DokkaConfigurationImpl` und `DokkaSourceSetImpl`. Deshalb greift auch der Andock-Fehler nicht, der die ABI-Validierung in diesem Modul lahmlegt: es wird gar nichts an ein Kotlin-Target gehängt.

**[verifiziert]** Im Lauf sichtbar an Dokkas eigener Generator-Ausgabe unter dem AGP-Task-Namen:

```
> Task :bottomsheet:javaDocReleaseGeneration
Transforming documentation model before merging
Merging documentation models
Creating pages
Rendering
Running post-actions
```

und im `--info`-Log an den geladenen Dokka-Plugins:

```
Loaded plugins: [org.jetbrains.dokka.javadoc.JavadocPlugin,
                 org.jetbrains.dokka.analysis.kotlin.descriptors.ide.IdeDescriptorAnalysisPlugin,
                 org.jetbrains.dokka.analysis.kotlin.descriptors.compiler.CompilerDescriptorAnalysisPlugin,
                 org.jetbrains.dokka.analysis.java.JavaAnalysisPlugin,
                 org.jetbrains.dokka.kotlinAsJava.KotlinAsJavaPlugin,
                 org.jetbrains.dokka.base.DokkaBase]
```

## Was im JAR steckt

**[verifiziert]** Probelauf: `com.vanniktech.maven.publish` 0.37.0 an `:bottomsheet` gehängt, **kein** Dokka-Plugin appliziert, `publishToMavenLocal`. Es entstehen `javaDocReleaseGeneration` und `javaDocReleaseJar`, und im lokalen Repository liegt:

```
bottomsheet-0.0.1-research-javadoc.jar   459 KB
```

**[verifiziert]** Ausgepackt: 28 HTML-Seiten im klassischen Javadoc-Layout. Die Typabdeckung ist **vollständig** — jeder öffentliche Typ der Library ist da:

```
BottomSheetColors        BottomSheetDefaults        BottomSheetDetentNames
BottomSheetMotion        BottomSheetScope           PresentationDetent
PresentationDetent.Medium  PresentationDetent.Large
SheetDetents             SheetDetents.Companion
BottomSheetHostKt        BottomSheetModifierKt
```

**[verifiziert]** Kein `internal`-Leck: eine Suche nach `SheetRegistry`, `NestedScrollRules`, `SheetAnchors` und `GesturePolicy` findet im JAR nichts. Dokka dokumentiert per Vorgabe nur `public`.

**[verifiziert]** Der KDoc-**Fließtext** ist echt vorhanden, nicht bloß die Signatur:

> Attaches a bottom sheet to this composable. The owner decides what the sheet shows — it is drawn by the BottomSheetHost, which sits above the entire app. Without a host this fails loudly on the first render.

Das ist die vollständige Beschreibung aus `BottomSheetModifier.kt`, inklusive aller drei Sätze und des aufgelösten `[BottomSheetHost]`-Links. **Die Antwort auf die Kernfrage lautet also: ja, es ist echte Doku — keine leere Hülle und keine reine Java-Sicht ohne Kommentare.**

## Wo es dann doch weh tut

### 1. Die `@param`-Texte werden nach dem ersten Satz abgeschnitten

**[verifiziert]** Das ist der ernste Befund. Quelltext in `BottomSheetModifier.kt`:

```kotlin
 * @param interactiveDismissEnabled whether user gestures may close the sheet. Gated by
 *   [gesturesEnabled]. When `false`, the library offers TalkBack **no** dismiss action; the app
 *   must then place a way to close inside the sheet content itself.
```

Im JAR steht davon:

> `interactiveDismissEnabled` - whether user gestures may close the sheet.

**[verifiziert]** Das trifft **9 der 15** `@param`-Tags von `Modifier.bottomSheet`. Verloren gehen:

| Parameter | verlorener Satz |
| --- | --- |
| `isPresented` | „The state belongs to the app." |
| `onDismissRequest` | „If the app sets `isPresented = false` itself, the callback does not fire." |
| `initialDetent` | „If the value is not part of `presentationDetents`, the smallest contained detent is used." |
| `gesturesEnabled` | „When `false`, a requested `dragHandle` is **discarded** — a handle that does nothing would be a lie." |
| `interactiveDismissEnabled` | die gesamte TalkBack-Regel (siehe oben) |
| `onDismissAttempt` | „Meant for confirming before data loss." |
| `paneTitle` | „Without a value the library announces nothing — it ships no strings of its own." |
| `dragHandle` | „If it is missing and the content scrolls, no reliable drag surface is left, and the library warns." |
| `content` | „It is composed only when the sheet opens and stays alive until the exit animation has finished." |

Das sind durchweg die Sätze, die eine Regel oder eine Falle beschreiben — also der Teil, für den man überhaupt in die Doku schaut.

**[verifiziert]** Wichtig für die faire Bewertung: **das liegt nicht an AGP.** Dokkas eigenes `dokkaGeneratePublicationJavadoc` erzeugt an derselben Stelle zeichengleich denselben gekürzten Text. Es ist eine Eigenschaft des **Javadoc-Ausgabeformats**, das in der offiziellen Doku als **Alpha** geführt wird ([kotlinlang.org/docs/dokka-migration.html](https://kotlinlang.org/docs/dokka-migration.html)).

**[verifiziert]** Nur die **HTML**-Ausgabe behält alles:

> interactiveDismissEnabled whether user gestures may close the sheet. Gated by gesturesEnabled. When false, the library offers TalkBack no dismiss action; the app must then place a way to close inside the sheet content itself.

Damit ist die eigentliche Entscheidung nicht „AGP oder Dokka-Plugin", sondern **„Javadoc-Format oder HTML"**. Und HTML gibt es nur über Weg B, weil AGP fest `javadoc-plugin` verdrahtet.

### 2. Kotlin-as-Java statt Kotlin

**[verifiziert]** Aus der Extension-Funktion wird eine statische Methode auf einer Datei-Fassade, mit Empfänger als `$self`, ohne Default-Werte, ohne `@Composable`:

```java
public final class BottomSheetModifierKt
static Modifier bottomSheet(Modifier $self, Boolean isPresented,
    Function0<Unit> onDismissRequest, SheetDetents presentationDetents, …)
```

Dokka-HTML zeigt dieselbe Funktion so, wie man sie aufruft:

```kotlin
fun Modifier.bottomSheet(
    isPresented: Boolean,
    onDismissRequest: () -> Unit,
    presentationDetents: SheetDetents = SheetDetents.MediumAndLarge,
    initialDetent: PresentationDetent = PresentationDetent.Medium,
    …
): Modifier
```

Für eine Library, deren gesamte API aus Compose-Idiomen mit vielen Default-Werten besteht, ist der Unterschied nicht kosmetisch. Die Default-Werte sind in der Javadoc-Sicht **nirgends** ablesbar.

### 3. Es gibt keine einzige Stellschraube

**[belegt]** `PublishingOptionsImpl` in AGP 9.3.1 hat genau zwei Methoden: `withSourcesJar()` und `withJavadocJar()`. Mehr nicht.

**[belegt]** Und `JavaDocGenerationTask` hat als Eingänge nur `projectPath`, `moduleVersion`, `sources`, `classpath`, `outputDirectory` und die drei Dokka-Classpaths — alle von AGP selbst verdrahtet. Es gibt **keinen** Eingang für Modulname, Sichtbarkeitsfilter, Unterdrückung von Paketen, externe Doku-Links oder `packageOptions`.

Auf die Frage nach der Steuerbarkeit lautet die Antwort damit: **gar nicht.**

**[verifiziert]** Praktisch sichtbarste Folge: der Modulname ist der **Gradle-Pfad**, mitsamt Doppelpunkt. Kopfzeile jeder Seite und `<title>` lauten:

```
:bottomsheet 0.0.1-research API
```

Für ein Artefakt, das auf Maven Central landet, ist `:bottomsheet` eine Panne — und sie ist nicht abstellbar. Dokka-HTML schreibt dort `bottomsheet` und ließe sich über `dokka { moduleName = "BottomSheet" }` frei setzen.

Die Sichtbarkeitsfrage entschärft sich dagegen von selbst: Dokka dokumentiert per Vorgabe nur `public`, `internal/` ist in beiden Wegen draußen — verifiziert. Wir bräuchten den Schalter also gar nicht. Er fehlt trotzdem, falls sich das je ändert.

### 4. Die Analyse-Engine ist deprecated

**[belegt]** AGP verdrahtet `analysis-kotlin-descriptors` — die K1-Analyse. Dazu die Dokka-2.1.0-Release-Notes:

> Dokka's K1 analysis is still available, but it is **deprecated and will be removed in future releases**.

— [Dokka 2.1.0 Release Notes](https://github.com/Kotlin/dokka/releases/tag/v2.1.0)

**[belegt]** In 2.2.0 ist K2 der Normalfall: „K2 analysis is now stable, enabled by default, and fully migrated to the new shared Analysis API" ([2.2.0 Release Notes](https://github.com/Kotlin/dokka/releases/tag/v2.2.0)). Alles, was dort an Analyse-Verbesserungen steht — Auflösung mehrdeutiger KDoc-Links, Links auf Extensions mit Typparametern, Kontextparameter — gilt für K2 und damit **nicht** für den AGP-Pfad.

**[unbelegt]** Wann JetBrains K1 entfernt und was AGP dann tut, ist nicht absehbar. Es ist ein Risiko, das wir auf Weg A nicht selbst in der Hand haben; auf Weg B schon, weil wir die Dokka-Version dort selbst setzen.

## Wie Weg A verdrahtet würde

**[verifiziert]** Über `com.vanniktech.maven.publish` passiert es von allein, ohne eine Zeile Konfiguration — genau das war der Ausgangsbefund. Grund steht im Quelltext des Plugins ([`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt)): ohne Dokka-Plugin fällt `defaultJavaDocOption` auf `JavadocJar.Javadoc()` zurück, und `AndroidSingleVariantLibrary.configure` übersetzt das in AGPs eigenen Schalter ([`Platform.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/Platform.kt)):

```kotlin
library.publishing {
    singleVariant(variant) {
        if (sourcesJar is SourcesJar.Sources) withSourcesJar()
        if (javadocJar is JavadocJar.Javadoc) withJavadocJar()
    }
}
```

**[belegt]** Weg A hängt aber **nicht** am Publishing-Plugin. `withJavadocJar()` ist gewöhnliche AGP-DSL ([`PublishingOptions`, API-Referenz](https://developer.android.com/reference/tools/gradle-api/8.3/null/com/android/build/api/dsl/PublishingOptions)) und funktioniert mit jedem Publishing-Aufbau:

```kotlin
android {
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}
```

**[unbelegt]** Diese Variante ohne vanniktech ist nicht separat nachgestellt worden — verifiziert ist nur der Weg über das Publishing-Plugin, das intern genau diese DSL aufruft.

**[verifiziert]** Configuration Cache: unauffällig. Erster Lauf `Configuration cache entry stored.`, zweiter `Configuration cache entry reused.`

---

# Weg B: Dokka 2.2.0 explizit

Der Verdacht aus R1 — kein Kotlin-Target zum Andocken, wie bei der ABI-Validierung — bestätigt sich hier **nicht**.

## Trägt es unter AGP 9 mit Built-in Kotlin?

**[belegt]** Erste Zeile der 2.2.0-Release-Notes:

> Compatibility with [Android Gradle Plugin 9.0.0](https://developer.android.com/build/releases/agp-9-0-0-release-notes), including [Built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes#android-gradle-plugin-built-in-kotlin). ([#4231](https://github.com/Kotlin/dokka/pull/4231), [#4295](https://github.com/Kotlin/dokka/pull/4295), [#4412](https://github.com/Kotlin/dokka/pull/4412))

— [Dokka 2.2.0 Release Notes](https://github.com/Kotlin/dokka/releases/tag/v2.2.0)

**[belegt]** Dokkas Ticket [#4472](https://github.com/Kotlin/dokka/issues/4472) beschreibt Mechanismus und Grenze:

> AGP with Built-in-Kotlin [doesn't synchronize sources](https://issuetracker.google.com/issues/386221070) into `KotlinSourceSet` anymore. That's why we started to collect sources and classpath from Android variants directly […]

> With Dokka 2.2.0, we correctly support working with […] AGP with Built-in-Kotlin with a **single**-flavor setup (e.g., where there are only two variants: `release` and `debug`), which should cover most cases for Android library authors.

`:bottomsheet` hat keine Flavors — genau der unterstützte Fall.

**[belegt]** Der `AndroidAdapter` hängt sich an die AGP-Plugin-IDs, nicht an ein Kotlin-Target ([`PluginIds.kt`](https://github.com/Kotlin/dokka/blob/v2.2.0/dokka-runners/dokka-gradle-plugin/src/main/kotlin/internal/PluginIds.kt) listet `com.android.library`), und der AGP-9-Zweig ist auf die Major-Version geschaltet, nicht auf ein Maximum ([`AndroidAdapter.kt`](https://github.com/Kotlin/dokka/blob/v2.2.0/dokka-runners/dokka-gradle-plugin/src/main/kotlin/adapters/AndroidAdapter.kt)):

```kotlin
if (currentAgpVersion.startsWith("9.")) {
    sourceRoots.from(androidExt.sourceDirectories(this@dss.name))
    basedOnAndroidVariant.set(androidExt.isBasedOnAndroidVariant(this@dss.name))
}
```

AGP 9.3.1 fällt darunter wie 9.0.0.

**[belegt]** Welche Variante dokumentiert wird, entscheidet ein fester Vergleich ([`AndroidVariantInfo.kt`](https://github.com/Kotlin/dokka/blob/v2.2.0/dokka-runners/dokka-gradle-plugin/src/main/kotlin/adapters/AndroidVariantInfo.kt)):

```kotlin
isPublishable = variant.buildType.equals("release", ignoreCase = true),
```

`release` wird dokumentiert, `debug` und die Test-Source-Sets werden angelegt und unterdrückt.

**[verifiziert]** Am Repo:

```
$ ./gradlew :bottomsheet:dokkaGeneratePublicationHtml
BUILD SUCCESSFUL in 16s
```

38 HTML-Seiten, Paket `dev.sircedric.bottomsheet`, vollständige KDoc-Prosa mit allen `@param`-Sätzen, aufgelöste Querverweise, `internal/` nicht enthalten. **Keine einzige Warnung**, insbesondere kein unaufgelöster KDoc-Link. `index.html` enthält genau einen Filter-Eintrag, `data-filterable-set=":bottomsheet/release"`.

**[verifiziert]** Der Mechanismus, per Init-Script an `taskGraph.whenReady` abgefragt:

```
PROBE kotlin extension  = org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension_Decorated
PROBE kotlin sourceSets = [debug, debugAndroidTest, debugUnitTest, release]
PROBE dokkaSourceSets   = [debug, debugAndroidTest, debugUnitTest, release]
```

AGP 9 mit Built-in Kotlin legt also sehr wohl eine `KotlinAndroidProjectExtension` mit variantenbenannten Source-Sets an; anders als bei der ABI-Validierung fehlt nichts zum Andocken. Dieselbe Abfrage in `afterEvaluate` liefert noch `[]` — die Source-Sets entstehen erst im AGP-Variantenlebenszyklus. Für Dokka folgenlos, weil es durchgehend lazy über Provider arbeitet; eigener Code, der `dokkaSourceSets` in `afterEvaluate` **auflöst**, griffe aber ins Leere.

**[verifiziert]** `./gradlew :bottomsheet:build` läuft mit appliziertem Dokka unverändert durch. Dokka hängt sich nicht in `check` oder `build` ein.

**[belegt]** Mindestversionen laut [kotlinlang.org/docs/dokka-migration.html](https://kotlinlang.org/docs/dokka-migration.html): Gradle 7.6+, AGP 7.0+, KGP 1.9+. Eine Obergrenze wird für keines der drei genannt.

## Configuration Cache

**[belegt]** „DGP v2 fully supports Gradle configuration cache and build cache" — [kotlinlang.org/docs/dokka-migration.html](https://kotlinlang.org/docs/dokka-migration.html).

**[belegt]** 2.2.0 hat dort noch nachgebessert: „Fix DGP reads all Gradle properties, which causes unnecessary CC invalidation" ([#4467](https://github.com/Kotlin/dokka/pull/4467)).

**[verifiziert]** Mit unverändertem `org.gradle.configuration-cache=true`: erster Lauf `Configuration cache entry stored.`, zweiter `Configuration cache entry reused.` Keine Einträge im `problems-report.html`. Die Property kann anbleiben.

**[verifiziert]** Eine Deprecation-Falle gibt es, und sie liegt bei uns, nicht bei Dokka. Gradle 9.7 verwirft die Delegate-Syntax:

> The `val name by registering(Type::class) { }` property delegate syntax has been deprecated. This is scheduled to be removed in Gradle 10.

Also `tasks.register<Jar>("javadocJar")` schreiben, nicht `val javadocJar by tasks.registering(Jar::class)`. Damit ist der Lauf unter `--warning-mode all` warnungsfrei.

## Tasks und JAR

**[belegt]** Task-Namen von DGPv2 ([kotlinlang.org/docs/dokka-migration.html](https://kotlinlang.org/docs/dokka-migration.html)):

| Format | Task |
| --- | --- |
| HTML | `dokkaGeneratePublicationHtml` |
| Javadoc | `dokkaGeneratePublicationJavadoc` |
| beides | `dokkaGenerate` |

**[verifiziert]** `./gradlew :bottomsheet:tasks --all` bestätigt das. Die Dokka-1-Namen existieren nur noch als abgeschaltete Stummel:

```
dokkaHtml    - [V1 tasks disabled] Generates documentation in 'HTML' format.
dokkaJavadoc - [V1 tasks disabled] Generates documentation in 'Javadoc' format.
```

Wer aus einem alten Blogpost abschreibt, landet dort. Die richtigen Namen haben `Publication` in der Mitte.

**[verifiziert]** Das JAR ist ein Dreizeiler; kein `dependsOn` nötig, `from(TaskProvider)` trägt die Abhängigkeit mit:

```kotlin
tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
    from(tasks.dokkaGeneratePublicationHtml)
}
```

Ergebnis: `bottomsheet-javadoc.jar`, 592 KB, 112 Einträge.

## Was `com.vanniktech.maven.publish` dabei abnimmt

Der Ausgang von [Issue #21](https://github.com/SirCedric/BottomSheet/issues/21) ist hier nicht vorweggenommen; beschrieben ist nur, was das Plugin täte, falls es genommen wird.

**[belegt]** Es erkennt Dokka selbst und verdrahtet das JAR ([`MavenPublishBaseExtension.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishBaseExtension.kt)):

```kotlin
private fun defaultJavaDocOption(javadocJar: Boolean): JavadocJar = when {
    !javadocJar -> JavadocJar.None()
    project.plugins.hasPlugin("org.jetbrains.dokka-javadoc") ->
        JavadocJar.Dokka("dokkaGeneratePublicationJavadoc")
    project.plugins.hasPlugin("org.jetbrains.dokka") -> {
        check(project.extensions.findByName("dokka") != null) {
            "Dokka in v2 mode is required when using Dokka"
        }
        JavadocJar.Dokka("dokkaGeneratePublicationHtml")
    }
    !project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform") -> JavadocJar.Javadoc()
    else -> JavadocJar.Empty()
}
```

Drei Konsequenzen:

1. `dokka-javadoc` schlägt `dokka`. Sind beide appliziert, gewinnt das Alpha-Javadoc-Format — also genau das mit der `@param`-Kürzung. **Für HTML nur `org.jetbrains.dokka` applizieren.**
2. Ist nur `org.jetbrains.dokka` da, nimmt es von selbst `dokkaGeneratePublicationHtml`. Weg B braucht dann keine eigene `Jar`-Task.
3. Ohne Dokka landet man auf `JavadocJar.Javadoc()` — und damit auf Weg A.

**[belegt]** Für `com.android.library` wählt es `AndroidSingleVariantLibrary(javadocJar, sourcesJar, "release")`; die Variante ist über die Gradle-Property `ANDROID_VARIANT_TO_PUBLISH` umstellbar. Das deckt sich mit Dokkas eigener Wahl.

**[belegt]** Die Erkennung läuft für `com.android.library` in `androidComponents.finalizeDsl` — „afterEvaluate is too late for AGP" ([`MavenPublishPlugin.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/MavenPublishPlugin.kt)). Überschreiben also im Build-Skript, nicht in `afterEvaluate`.

**[verifiziert]** Die Auto-Erkennung wurde für den Fall **ohne** Dokka nachgestellt (siehe Weg A). Der Fall **mit** appliziertem Dokka ist **[unbelegt]** — nicht nachgestellt, um R1 nicht vorwegzunehmen. Der Code liest sich eindeutig; falls R1 auf vanniktech hinausläuft, gehört es in B1 einmal praktisch geprüft.

---

# Risiken

**[belegt]** Dokkas K2-Analyse steckt auf dem Compiler `2.3.20-dev-7064` ([`gradle/libs.versions.toml` bei v2.2.0](https://github.com/Kotlin/dokka/blob/v2.2.0/gradle/libs.versions.toml)), gebaut ist das Plugin gegen KGP 2.3.0. Wir übersetzen mit Kotlin **2.4.10**. Dokka parst unsere Quellen also mit einem älteren Frontend als der Compiler. **[verifiziert]** Für den heutigen Stand folgenlos — der Lauf ist sauber. **[unbelegt]** Ob das bleibt, hängt daran, ob wir Sprachmittel aufnehmen, die es in 2.3.20 nicht gab. Betrifft beide Wege, Weg A über K1 sogar mit noch älterer Analyse.

**[belegt]** Flavors würden Weg B kippen: der AGP-9-Pfad erzeugt je Variante einen Source-Set mit überlappenden Source-Roots, und die Analysis API verträgt keine Überlappung ([KT-79914](https://youtrack.jetbrains.com/issue/KT-79914), Symptom in [#4458](https://github.com/Kotlin/dokka/issues/4458), Einordnung in [#4472](https://github.com/Kotlin/dokka/issues/4472)). Bei zwei Build-Types ohne Flavors greift das nicht, weil nur `release` publishable ist. Ein Grund mehr, `:bottomsheet` flavorlos zu lassen.

**[unbelegt]** Gradle 9.7.0 ist vier Monate jünger als Dokka 2.2.0 und kann von JetBrains nicht getestet sein. Hier zählt allein der verifizierte Lauf. Bei einem Gradle-Sprung gehört `dokkaGeneratePublicationHtml` auf die Prüfliste.

**[unbelegt]** Dokkas AGP-Zweig prüft `startsWith("9.")`. Ein AGP 10 fiele heraus und liefe wieder in den für AGP 9 als fehlerhaft markierten Pfad.

**[belegt]** Weg A hängt an der K1-Analyse, die JetBrains als deprecated führt und entfernen will (siehe oben). Der Zeitpunkt ist nicht in unserer Hand.

# Rückfall: leeres Javadoc-JAR

Wird nach dieser Recherche nicht gebraucht — beide Wege liefern echte Doku. Falls doch je nötig:

```kotlin
tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
}
```

Ein `Jar` ohne `from(...)` erzeugt ein Archiv mit nichts als `META-INF/MANIFEST.MF`. Maven Central prüft nur, dass ein Artefakt mit Classifier `-javadoc` vorliegt, nicht dessen Inhalt.

Mit `com.vanniktech.maven.publish` ist es `JavadocJar.Empty()`; das Plugin registriert dafür eine Task `emptyJavadocJar` ([`tasks/JavadocJar.kt`](https://github.com/vanniktech/gradle-maven-publish-plugin/blob/0.37.0/plugin/src/main/kotlin/com/vanniktech/maven/publish/tasks/JavadocJar.kt)). Der KDoc dort sagt zu `None` ausdrücklich „This option is not compatible with Maven Central" und zu `Empty` „Creates an empty javadoc jar to satisfy maven central requirements".

Käme es je dazu, gehört es nach Vorgabe des Tickets ausdrücklich in B1 dokumentiert, nicht still eingebaut.

# Reproduktion

Keiner der Probeläufe ist eingecheckt — dieser Branch trägt nur diese Datei.

**Weg A** (kein Dokka-Plugin):

```kotlin
// bottomsheet/build.gradle.kts
android {
    publishing { singleVariant("release") { withSourcesJar(); withJavadocJar() } }
}
```

```bash
./gradlew :bottomsheet:javaDocReleaseJar
```

**Weg B**:

```kotlin
// gradle/libs.versions.toml, [plugins]
dokka = { id = "org.jetbrains.dokka", version = "2.2.0" }

// bottomsheet/build.gradle.kts, plugins { }
alias(libs.plugins.dokka)

// bottomsheet/build.gradle.kts
tasks.register<Jar>("javadocJar") {
    archiveClassifier = "javadoc"
    from(tasks.dokkaGeneratePublicationHtml)
}
```

```bash
./gradlew :bottomsheet:dokkaGeneratePublicationHtml
./gradlew :bottomsheet:javadocJar
./gradlew :bottomsheet:build
```

Nachweis, dass AGP intern Dokka fährt:

```bash
unzip -l ~/.gradle/caches/modules-2/files-2.1/com.android.tools.build/gradle/9.3.1/*/gradle-9.3.1.jar \
  | grep -iE "JavaDocGenerationTask|DokkaParallelBuildService"
```
