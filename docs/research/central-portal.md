# Central Portal: von „kein Account" zu „`de.cedrickummer` verifiziert + Token"

Recherche zu [#23](https://github.com/SirCedric/BottomSheet/issues/23). Stand: 2026-08-14.

Recherchiert wurde ausschließlich gegen Primärquellen: die offizielle Dokumentation des
Sonatype Central Portal (`central.sonatype.org`, das Doku-Portal zu `central.sonatype.com`)
und die offizielle Hetzner-Dokumentation (`docs.hetzner.com`, `status.hetzner.com`).
Keine Blogposts, keine Stack-Overflow-Antworten, keine Gradle-Plugin-READMEs.

Jede Behauptung unten trägt ihren Quelllink. Was nicht belegt werden konnte, steht
gesammelt im Abschnitt [Nicht belegt](#nicht-belegt) — nichts davon gehört ungeprüft in
eine Schritt-für-Schritt-Führung.

---

## Die Falle vorweg: OSSRH ist tot

Der Großteil der Anleitungen im Netz beschreibt **OSSRH** — Sonatype-JIRA-Ticket unter
`issues.sonatype.org` aufmachen, danach in `oss.sonatype.org` bzw. `s01.oss.sonatype.org`
ein Staging-Repository schließen und releasen. Dieser Weg existiert nicht mehr:

- `issues.sonatype.org` wurde am 2024-01-09 zur Abschaltung angekündigt; Registrierung
  läuft seither über das Central Portal, Support über E-Mail statt JIRA —
  [FAQ: What Happened to issues.sonatype.org?](https://central.sonatype.org/faq/what-happened-to-issues-sonatype-org/)
- „As of June 30, 2025 OSSRH has reached end of life and has been shut down." Alle
  OSSRH-Namespaces wurden ins Central Portal migriert —
  [OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/)

**Erkennungsmerkmale einer veralteten Anleitung** (alles davon ist heute falsch):
ein JIRA-Ticket als erster Schritt; `oss.sonatype.org` oder `s01.oss.sonatype.org` als
Host; „Nexus Staging"-Plugin als einziger Weg; ein Verification Key im Format
`OSSRH-12345`. Zum letzten Punkt siehe die Warnung in
[Schritt 4](#schritt-4-verification-key-holen) — dieses Format steht sogar noch in einer
FAQ-Seite von Sonatype selbst.

Zweite Falle, unabhängig davon: Für **Hetzner** beschreibt fast jede Anleitung die alte
DNS Console unter `dns.hetzner.com`. Auch die ist weg, siehe
[Schritt 5](#schritt-5-txt-record-bei-hetzner-setzen).

---

## Der Ablauf, in abarbeitbarer Reihenfolge

Wartepunkte auf Dritte sind mit ⏳ markiert.

| # | Schritt | Wo | Dauer |
| --- | --- | --- | --- |
| 1 | Account am Central Portal anlegen | `central.sonatype.com` | Minuten |
| 2 | ⏳ Bestätigungsmail abwarten und Adresse verifizieren | Mailkonto | Minuten |
| 3 | Namespace `de.cedrickummer` anlegen (Status *Unverified*) | Portal | Minuten |
| 4 | Verification Key kopieren | Portal | Sekunden |
| 5 | TXT-Record auf `cedrickummer.de` setzen | Hetzner Console | Minuten |
| 6 | ⏳ DNS-Propagierung abwarten, per `dig` prüfen | Terminal | TTL-abhängig |
| 7 | „Verify Namespace" auslösen | Portal | Sekunden |
| 8 | ⏳ Verifikation abwarten (Status *Verification Pending* → *Verified*) | Portal | Minuten bis 2 Werktage |
| 9 | User Token erzeugen und sicher ablegen | `central.sonatype.com/usertoken` | Minuten |
| 10 | Optional: TXT-Record wieder entfernen | Hetzner Console | Minuten |

Die einzige harte Unbekannte im Zeitplan ist Schritt 8. Sonatype nennt für den
Normalfall „a few minutes", hält sich aber die Möglichkeit manueller Prüfung offen und
verspricht dafür „within two business days" —
[Register a Namespace](https://central.sonatype.org/register/namespace/),
[FAQ: Why the wait?](https://central.sonatype.org/faq/a-human/).
Für die Planung heißt das: **das HITL-Ticket muss mindestens zwei Werktage vor dem
ersten geplanten Release angefasst werden**, und der Schritt sollte an einem Wochentag
laufen — der Support antwortet „Monday through Friday during standard North American
business hours" ([FAQ: Why the wait?](https://central.sonatype.org/faq/a-human/)).

---

## Schritt 1–2: Registrierung

Die Anmeldung läuft über den „Sign In"-Link oben rechts auf `https://central.sonatype.com`.
Drei Wege stehen zur Wahl: Social Login über **Google**, Social Login über **GitHub**,
oder **eigener Benutzername plus Passwort**. Bei Social Login erhält Sonatype Zugriff auf
die zum Konto gehörende E-Mail-Adresse
([Register to Publish Via the Central Portal](https://central.sonatype.org/register/central-portal/)).

Fallstricke, alle belegt auf derselben Seite:

- **Die E-Mail-Adresse muss erreichbar sein.** Der Signup wird erst durch Bestätigung der
  Adresse abgeschlossen — „provide a valid email when signing up" und „need access to that
  email address to complete the signup process".
- **Der Benutzername ist unveränderlich.** „There is no way to rename, update or change
  your username. If you want a different username you will need to create a new account."
  Wer den Namen später anders haben will, muss ein zweites Konto anlegen und die
  Namespace-Zuordnung per Support-Ticket umziehen lassen.
- **Veröffentlichungen sind endgültig.** „Once released/published, you will not be able to
  remove/update/modify your components."
  ([FAQ: Can I change … a component?](https://central.sonatype.org/faq/can-i-change-a-component/))

**Entscheidung mit Nebenwirkung:** Wer sich über **GitHub** anmeldet, bekommt den
Namespace `io.github.<github username>` automatisch verifiziert dazu, ohne DNS
([Register a Namespace](https://central.sonatype.org/register/namespace/)).
Für `de.cedrickummer` ändert das nichts — der Namespace muss so oder so über DNS
verifiziert werden —, aber es liefert kostenlos einen zweiten, sofort nutzbaren Namespace
(`io.github.SirCedric`) als Rückfallebene, falls die DNS-Verifikation hängt. Das ist der
einzige Grund, den GitHub-Login dem Passwort-Login vorzuziehen.

Der Zugang zum Mailkonto muss dauerhaft erhalten bleiben, nicht nur einmalig
([FAQ: Why do I have to maintain access to my email account?](https://central.sonatype.org/faq/publisher-email-addresses/)).

---

## Schritt 3: Namespace anlegen

Weg im Portal: oben rechts auf Benutzername/E-Mail klicken → **„View Namespaces"**.
Die Seite liegt unter `https://central.sonatype.com/publishing/namespaces`. Dort
**„Add Namespace"**, `de.cedrickummer` eintragen, **„Submit"**. Der Eintrag erscheint
danach im Status **„Unverified"**
([Register a Namespace](https://central.sonatype.org/register/namespace/)).

Warum `de.cedrickummer` zulässig ist: „If you are the owner or maintainer of a domain
name, you can use any groupId starting with the reverse domain name" — `cedrickummer.de`
umgedreht ist `de.cedrickummer` (ebd.).

Ein einmal verifizierter Namespace deckt alle Kinder mit ab: „If you are authorized to
publish on the `com.example` namespace, you may publish a `groupId` of `com.example`,
`com.example.child`, `com.example.child.subproject`, or any other namespace that begins
with `com.example`"
([FAQ: groupId vs. namespace](https://central.sonatype.org/faq/namespaces-vs-groupids/)).
Für dieses Repo heißt das: **einmal `de.cedrickummer` verifizieren genügt**, ein eigener
Namespace `de.cedrickummer.bottomsheet` ist weder nötig noch anzumelden.

Weitere Aktionen im Drei-Punkte-Menü des Namespace-Eintrags, alle namentlich in der Doku:
**„View ID"** (zeigt den Verification Key), **„Verify Namespace"**, **„Cancel Verification"**
(setzt zurück auf *Unverified*), **„View History"**, **„View Users"**, **„Remove Namespace"**
(ebd.).

---

## Schritt 4: Verification Key holen

Im Drei-Punkte-Menü **„View ID"** wählen; neben dem Verification Key sitzt ein
**Clipboard-Icon**, „click the clipboard icon next to the Verification Key to copy the
value to your computer's clipboard"
([Register a Namespace](https://central.sonatype.org/register/namespace/)).

> **Warnung für die Führung.** Die FAQ-Seite
> [Why do I need to verify project ownership?](https://central.sonatype.org/faq/verify-ownership/)
> nennt als Beispielformat noch `OSSRH-00000`. Das ist ein Überbleibsel aus der
> JIRA-Ära — damals war der Key die Ticketnummer. Welches Format das Portal heute
> ausgibt, ist **nicht dokumentiert** (die Registrierungsseite zeigt an dieser Stelle nur
> Screenshots). Die Führung darf deshalb **kein Format vorgeben und kein Beispiel
> zeigen**, sondern muss zum wörtlichen Kopieren des angezeigten Werts auffordern.

---

## Schritt 5: TXT-Record bei Hetzner setzen

### Der Record, exakt

| Feld | Wert |
| --- | --- |
| Zone | `cedrickummer.de` |
| Typ | `TXT` |
| Name / Host | `@` (Zone-Apex) |
| Value | der Verification Key, wörtlich und allein — bei Hetzner in doppelte Anführungszeichen gesetzt |
| TTL | mindestens `60`; Empfehlung `300` (siehe unten, *nicht* von Sonatype vorgegeben) |

Belege im Einzelnen:

- **Apex, kein Subdomain-Label.** „The automated system checks the *exact* domain for the
  namespace for the TXT record. For the `com.example` namespace, the registration process
  checks `example.com`. It does not check `com.example.com`, `maven-central.example.com`,
  or any other variation."
  ([Register a Namespace](https://central.sonatype.org/register/namespace/),
  wortgleich in [FAQ: How do I set the TXT record …](https://central.sonatype.org/faq/how-to-set-txt-record/))
  Für `de.cedrickummer` wird also `cedrickummer.de` abgefragt — kein `_sonatype`, kein
  `_maven-central`, kein `de.cedrickummer.cedrickummer.de`.
- **Value ist nur der Key.** „prove domain ownership by adding a DNS TXT record with a
  value set to the Verification Key" (ebd.). Kein Präfix, kein `sonatype-verification=`,
  kein Schlüssel-Wert-Paar.
- **Hetzner verlangt Anführungszeichen.** „The value has to be quoted."
  ([TXT record](https://docs.hetzner.com/networking/dns/record-types/txt-record/))
  Ein Sub-String darf maximal 255 Zeichen haben (ebd.) — für einen Verification Key
  unkritisch.
- **`@` steht für die Root.** Das Name-Feld ist relativ zur Zone: „No period at the end of
  the (sub)domain: The provided entry is not the full domain. The zone itself is appended
  automatically at the end of the entry."
  ([FAQ: Records](https://docs.hetzner.com/networking/dns/faq/records/)); für die Root
  ist `@` einzutragen
  ([TXT record](https://docs.hetzner.com/networking/dns/record-types/txt-record/)).
- **TTL-Minimum 60 Sekunden.** Records müssen „at least 60 seconds as TTL value" haben
  ([DNS Overview](https://docs.hetzner.com/networking/dns/overview/)).
  Sonatype gibt **keine** TTL vor — auf keiner der beiden einschlägigen Seiten steht ein
  TTL-Wert. Die Empfehlung `300` ist eine eigene Ableitung, siehe
  [Nicht belegt](#nicht-belegt).

### Die Oberfläche: `console.hetzner.com`, nicht `dns.hetzner.com`

Das ist der Hetzner-Teil der Falle. Die alte DNS Console ist abgeschaltet:

- Ab 2025-11-10 konnten dort keine neuen Zonen mehr angelegt werden
  ([Status: Creation of new zones will be disabled](https://status.hetzner.com/incident/6c173f57-b7c6-4cd7-8554-51ca21b5ada8)).
- Am 2026-05-20 ging sie in den Read-only-Modus, am 2026-05-27 wurde sie entfernt:
  „The DNS console and API was removed. `dns.hetzner.com` redirects as of now to
  `console.hetzner.com`" … „As of now, no zones are available in the old system anymore."
  ([Status: Shutdown of DNS Console in May 2026](https://status.hetzner.com/incident/c2146c42-6dd2-4454-916a-19f07e0e5a44))

Der Weg heute, in der Hetzner Console:

1. `https://console.hetzner.com` öffnen, das Projekt wählen, in dem die Zone liegt.
2. Links im Menü **„DNS"** (Bereich *Network & Security*)
   ([DNS Overview](https://docs.hetzner.com/networking/dns/overview/)).
3. Die Zone `cedrickummer.de` auswählen. Existiert sie nicht, zuerst **„Add zone"** →
   Domainname → **„Create an empty zone"**
   ([Getting started](https://docs.hetzner.com/dns-console/dns/general/getting-started-dns/)).
   In dem Fall muss die Domain zusätzlich beim Registrar auf Hetzners autoritative
   Nameserver delegiert sein — `hydrogen.ns.hetzner.com`, `oxygen.ns.hetzner.com`,
   `helium.ns.hetzner.de`
   ([Hetzner authoritative name servers](https://docs.hetzner.com/dns-console/dns/general/authoritative-name-servers/),
   [Updating name servers of external domains](https://docs.hetzner.com/networking/dns/getting-started/delegate-to-hetzner/)).
4. **„Type"** aufklappen, **TXT** wählen, die Felder ausfüllen, **„Add"**
   ([Adding records](https://docs.hetzner.com/networking/dns/getting-started/adding-records/)).

Grenzen der Doku: Hetzner beschreibt die Feldnamen als *Type*, *Name*, *Value*, *TTL*
([TXT record](https://docs.hetzner.com/networking/dns/record-types/txt-record/)); ob die
neue Console zusätzlich optionale Felder (*Comment*, *Labels*) im selben Dialog zeigt,
ist nur indirekt belegt
([Features and differences](https://docs.hetzner.com/networking/dns/migration-to-hetzner-console/features-and-differences/)
nennt Comment-Felder und Labels als Neuerungen). Die Führung sollte diese Felder als
„falls vorhanden, leer lassen" behandeln.

---

## Schritt 6: Vor dem Verifizieren selbst prüfen

Sonatype nennt die Prüfkommandos explizit
([FAQ: How do I set the TXT record …](https://central.sonatype.org/faq/how-to-set-txt-record/)):

```
dig -t txt cedrickummer.de      # macOS und Linux
host -t txt cedrickummer.de     # Linux
```

Erst wenn der Key hier auftaucht, lohnt Schritt 7. Das erspart den Umweg über
*Verification Pending* → Fehlschlag → *Cancel Verification*.

---

## Schritt 7–8: Verifizieren und warten

Im Namespace-Menü **„Verify Namespace"**; ein Bestätigungsdialog fragt, ob man bereit ist.
Der Status wechselt auf **„Verification Pending"** und bei Erfolg auf **„Verified"**
([Register a Namespace](https://central.sonatype.org/register/namespace/)).

Zur Dauer, wörtlich: „If you have set up your DNS TXT record correctly, it should only
take a few minutes for us to verify your namespace. You can refresh the Namespace page to
see if the verification status has changed." (ebd.)

**Automatisch oder Mensch?** Beides ist vorgesehen. Der Regelfall ist Automatik — die
Doku spricht durchgehend vom „automated system", das die Domain prüft (ebd.). Zugleich
schreibt Sonatype, die Verifikation erlaube es „either the automation or support staff",
den Domain-Besitz festzustellen
([FAQ: Why do I need to verify project ownership?](https://central.sonatype.org/faq/verify-ownership/)),
und für Fälle, die ein Mensch anfassen muss: „A member of our support staff may need to
review or assist with your request … We are committed to turning around your new project
request within two business days", bei erfüllten Voraussetzungen „within a few hours or
less"
([FAQ: Why the wait?](https://central.sonatype.org/faq/a-human/)).

Bleibt der Status hängen, führt der Weg über `central-support@sonatype.com`; die
Support-Seite listet „account registration and namespace registration errors"
ausdrücklich als Anlass
([Support](https://central.sonatype.org/pages/support/)). Dort steht außerdem der Satz,
der in die Führung gehört: „Central support will never request your credentials" —
mitgeschickte Tokens gelten als kompromittiert und werden widerrufen (ebd.).

---

## Schritt 9: Publishing-Token

Weg: eingeloggt auf `https://central.sonatype.com/usertoken`
([Generating a Portal Token for Publishing](https://central.sonatype.org/publish/generate-portal-token/)).
Wörtlich von dort:

- „You must use a user token to publish artifacts to the Central Repository via the
  Central Publisher Portal."
- „Press the **Generate User Token** button"
- „Specify a display name and an expiration for the token"
- „Save the generated credentials for use in your publishing setup"
- „Tokens cannot be retrieved once the modal closes, so it is a publisher's responsibility
  to maintain control of the token or generate a replacement as needed."

**Wie die beiden Werte heißen:** `username` und `password`. Belegt über die Publisher-API,
die das Paar zum Bearer-Token verrechnet: „Given a user token of a `username` of
`example_username` and a `password` of `example_password`, the token would be calculated
by base64 encoding the two values joined with a `:`"
([Publish Portal API](https://central.sonatype.org/publish/publish-portal-api/)):

```
printf "example_username:example_password" | base64
# → Authorization: Bearer ZXhhbXBsZV91c2VybmFtZTpleGFtcGxlX3Bhc3N3b3Jk
```

Es sind also **nicht** die Anmeldedaten des Portal-Kontos, sondern ein separat erzeugtes
Paar. Beim Erzeugen sind zusätzlich ein **Anzeigename** und ein **Ablaufdatum**
anzugeben — der Token ist damit von Haus aus endlich, was für die CI-Einrichtung in
[#26](https://github.com/SirCedric/BottomSheet/issues/26) bedeutet: Ablaufdatum notieren,
sonst bricht der Release irgendwann ohne Vorwarnung.

**Widerrufbar?** Teilweise belegt: Sonatype widerruft Tokens von sich aus, wenn sie an
den Support geschickt wurden — „they will be considered compromised and reset/revoked"
([Support](https://central.sonatype.org/pages/support/)). Dass ein Token existiert, das
widerrufen werden *kann*, ist damit belegt; ein dokumentierter Selbstbedienungs-Knopf
dafür ist es nicht, siehe [Nicht belegt](#nicht-belegt).

---

## Schritt 10: Aufräumen

Der TXT-Record darf nach erfolgreicher Verifikation weg: die DNS-TXT-Records „can be
deleted after we have verified your project"
([FAQ: Why do I need to verify project ownership?](https://central.sonatype.org/faq/verify-ownership/)).
Achtung, dieselbe Seite trägt noch die OSSRH-Handschrift (siehe Schritt 4) — der Satz ist
aber unabhängig vom Verfahren formuliert. Wer auf Nummer sicher gehen will, lässt den
Record stehen; er kostet nichts und schadet nichts.

---

## `.de` und Namespaces jenseits von `io.github.*`

Kurz: **keine belegten Sonderregeln.** Was zu prüfen war:

- **ccTLD.** Nirgends in der Namespace-Dokumentation wird zwischen gTLD und ccTLD
  unterschieden; die Regel lautet schlicht „the reverse domain name", inklusive
  Bindestrichen, mit `www.springframework.org → org.springframework` als Beispiel
  ([Register a Namespace](https://central.sonatype.org/register/namespace/)).
  `cedrickummer.de → de.cedrickummer` folgt derselben Regel. Auch die Begründungsseite zu
  DNS-gebundenen Namespaces kennt keine TLD-Unterscheidung
  ([FAQ: Why are namespaces tied to DNS?](https://central.sonatype.org/faq/namespaces-and-dns/)).
- **Persönliche statt organisatorischer Domain.** Ebenfalls keine Regel dagegen; die
  Verifikation prüft Kontrolle über die Domain, nicht deren Charakter (ebd.).
- **Der reale Unterschied zu `io.github.*`** ist nur der Verifikationsweg: Bei
  Code-Hosting-Namespaces legt man ein temporäres öffentliches Repository an, dessen Name
  der Verification Key ist; bei GitHub-Signup entsteht `io.github.<username>` sogar ohne
  jeden Schritt. Beim Domain-Weg läuft es über DNS — mehr Latenz, mehr Fehlerquellen (das
  Apex-Detail), aber derselbe Zielzustand
  ([Register a Namespace](https://central.sonatype.org/register/namespace/)).

Der eine Punkt, der aus dem Domain-Weg eine Dauerverpflichtung macht: Der Namespace hängt
an der Domain, weil Sonatype die Vertrauensfrage an DNS delegiert
([FAQ: Why are namespaces tied to DNS?](https://central.sonatype.org/faq/namespaces-and-dns/)).
`cedrickummer.de` muss also gehalten werden, solange unter `de.cedrickummer`
veröffentlicht wird. Was bei Ablauf oder Inhaberwechsel der Domain mit einem bereits
verifizierten Namespace passiert, steht nirgends — siehe unten.

---

## Nicht belegt

Alles hier ist plausibel, aber **nicht** durch eine Primärquelle gedeckt. Keine dieser
Aussagen darf in der Führung als Tatsache auftreten.

- **Format des Verification Key im Portal.** Die Registrierungsseite zeigt an der Stelle
  nur Screenshots; das einzige dokumentierte Beispiel (`OSSRH-00000`) stammt sichtbar aus
  der JIRA-Ära. Konsequenz: wörtlich kopieren, nicht abtippen, kein Beispiel vorgeben.
- **TTL-Empfehlung `300`.** Sonatype nennt keine TTL, Hetzner nur das Minimum von 60
  Sekunden. `300` ist eine eigene Abwägung: niedrig genug, um nach einem Tippfehler
  schnell nachzubessern, hoch genug, um nicht ohne Not Last auf die Resolver zu geben.
  Jeder Wert ≥ 60 funktioniert.
- **Default-TTL der Hetzner Console.** Welcher Wert im Formular vorbelegt ist, sagt die
  Doku nicht. Die Führung muss den TTL-Wert explizit setzen lassen, statt sich auf eine
  Vorbelegung zu verlassen.
- **Widerruf-Knopf für Portal-Tokens.** Dass Sonatype Tokens widerruft, ist belegt; dass
  die Oberfläche unter `central.sonatype.com/usertoken` eine Revoke-Aktion für den
  Publisher selbst anbietet, ist es nicht. **Vorsicht bei der Recherche:** Suchtreffer zu
  „revoke user token" führen fast immer auf `help.sonatype.com` — das ist die Doku zu
  **Sonatype Nexus Repository Pro**, einem anderen Produkt, und gilt hier nicht.
  Faktisch abgesichert ist nur der von der Doku genannte Weg, „generate a replacement as
  needed" ([Generating a Portal Token](https://central.sonatype.org/publish/generate-portal-token/)).
  Für die Führung: an dieser Stelle einfach in der Oberfläche nachsehen und das Ergebnis
  hier nachtragen.
- **Zulässige Ablauf-Fristen für Tokens.** Dass ein „expiration" anzugeben ist, steht in
  der Doku; welche Werte zur Auswahl stehen und ob „nie" dabei ist, nicht.
- **Ob mehrere Tokens gleichzeitig gültig sein können.** Nicht dokumentiert. Relevant für
  #26, falls lokal und in CI getrennte Tokens gewünscht sind.
- **Verhalten bei Domainablauf oder Inhaberwechsel** nach erfolgter Verifikation. Keine
  Aussage in der Dokumentation.
- **Ob die Verifikation die TXT-Records aller Nameserver oder nur einen Resolver prüft.**
  Nicht dokumentiert; praktische Folge nur, dass man vor Schritt 7 selbst per `dig`
  nachsehen sollte.
- **Zwei-Faktor-Authentisierung am Portal.** Weder als Pflicht noch als Option
  dokumentiert.

---

## Quellen

Sonatype (Primärdoku zum Central Portal):

- [Register to Publish Via the Central Portal](https://central.sonatype.org/register/central-portal/)
- [Register a Namespace](https://central.sonatype.org/register/namespace/)
- [Generating a Portal Token for Publishing](https://central.sonatype.org/publish/generate-portal-token/)
- [Publish Portal API](https://central.sonatype.org/publish/publish-portal-api/)
- [Support](https://central.sonatype.org/pages/support/)
- [OSSRH Sunset](https://central.sonatype.org/pages/ossrh-eol/)
- FAQ: [How do I set the TXT record …](https://central.sonatype.org/faq/how-to-set-txt-record/) ·
  [Why do I need to verify project ownership?](https://central.sonatype.org/faq/verify-ownership/) ·
  [Why are namespaces tied to DNS?](https://central.sonatype.org/faq/namespaces-and-dns/) ·
  [Why the wait?](https://central.sonatype.org/faq/a-human/) ·
  [groupId vs. namespace](https://central.sonatype.org/faq/namespaces-vs-groupids/) ·
  [What happened to issues.sonatype.org?](https://central.sonatype.org/faq/what-happened-to-issues-sonatype-org/) ·
  [Why do I have to maintain access to my email account?](https://central.sonatype.org/faq/publisher-email-addresses/) ·
  [Can I change … a component?](https://central.sonatype.org/faq/can-i-change-a-component/)

Hetzner:

- [DNS Overview](https://docs.hetzner.com/networking/dns/overview/)
- [Adding records](https://docs.hetzner.com/networking/dns/getting-started/adding-records/)
- [TXT record](https://docs.hetzner.com/networking/dns/record-types/txt-record/)
- [FAQ: Records](https://docs.hetzner.com/networking/dns/faq/records/)
- [Getting started (DNS)](https://docs.hetzner.com/dns-console/dns/general/getting-started-dns/)
- [Features and differences](https://docs.hetzner.com/networking/dns/migration-to-hetzner-console/features-and-differences/)
- [Hetzner authoritative name servers](https://docs.hetzner.com/dns-console/dns/general/authoritative-name-servers/)
- [Updating name servers of external domains](https://docs.hetzner.com/networking/dns/getting-started/delegate-to-hetzner/)
- [Status: Shutdown of DNS Console in May 2026](https://status.hetzner.com/incident/c2146c42-6dd2-4454-916a-19f07e0e5a44)
- [Status: Creation of new zones disabled](https://status.hetzner.com/incident/6c173f57-b7c6-4cd7-8554-51ca21b5ada8)
