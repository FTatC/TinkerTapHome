# TinkerTapHome

En Android-app som styr hemmabyggda BLE-enheter direkt från telefonen, utan hubb, molnkonto eller internet.

https://github.com/FTatC/TinkerTapHome

## Vad det gör

Appen söker efter enheter som kör firmwaren TinkerTapNode, till exempel ett ESP32- eller nRF52-kort, och parkopplar och sparar dem med ett namn och ett token. Sparade enheter ansluts automatiskt medan appen syns. Varje enhets utgångar visas med sina aktuella värden och kan styras: reläer och lysdioder slås på och av, PWM-utgångar och servon ställs in med reglage, och allt kan släckas med en knapp.

Exempel på vad appen kan användas till:

- Tända och släcka lampor eller reläer som du har byggt själv.
- Ställa in ljusstyrka eller fläkthastighet via PWM, eller vinkeln på ett servo.
- Se utgångarnas verkliga läge när appen ansluter, även om en annan telefon har ändrat dem under tiden.
- Parkoppla en enhet igen när den har glömt telefonen, till exempel efter att alla parkopplingar på enheten har raderats.

## Kom igång

### Krav

- Android Studio, eller en JDK 17 eller senare. Android Studios inbyggda JDK fungerar.
- Android SDK med plattform 37. Android Studios SDK Manager installerar den.
- En fysisk Android-telefon med Android 14 (API 34) eller senare och Bluetooth LE. BLE fungerar inte i emulatorn.
- Minst en enhet med firmwaren TinkerTapNode, protokollversion 3 (se [`docs/ble-protocol.md`](docs/ble-protocol.md)).
- Inga externa tjänster. Appen använder varken nätverk, molntjänster eller konton.

### Installation

```bash
git clone https://github.com/FTatC/TinkerTapHome
cd TinkerTapHome
./gradlew assembleDebug
```

Gradle kontrollerar SHA-256 för varje beroende mot `gradle/verification-metadata.xml` och stoppar bygget om något inte stämmer.

### Konfiguration

Projektet har ingen `.env`-fil och inga hemligheter som behöver anges vid bygget. Gradle behöver bara hitta JDK:n och Android SDK. Android Studio skriver `local.properties` med `sdk.dir` åt dig. Bygger du från terminalen kan du i stället sätta miljövariablerna, till exempel med Android Studios JDK i Git Bash på Windows:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
```

Enhetens token för `AUTH` anges i appen när enheten sparas. Firmwarens standardtoken `tinker` är förifyllt.

### Kör projektet

Anslut telefonen med USB-felsökning eller trådlös felsökning påslagen, och kör:

```bash
./gradlew installDebug
```

Starta appen, tillåt Enheter i närheten, tryck på Sök och välj din TinkerTapNode.

Tester:

```bash
./gradlew test                   # JVM-tester, även arkitekturtesterna
./gradlew connectedAndroidTest   # Compose-tester och databastester på telefonen
./gradlew lint
```

## Arkitektur

Koden följer MVVM med ett UI-tillstånd per skärm och är uppdelad i tre lager, där varje lager är en egen Gradle-modul. Beroenden går bara inåt: `:app` → `:domain` ← `:data`. `:domain` är ren Kotlin utan Android och innehåller modeller, use cases och de interface som resten av appen bygger på, till exempel för anslutningar och lagring. All I/O ligger i `:data`: BLE-kommunikationen med en GATT-kö som kör en operation i taget, enhetens protokoll, Room och DataStore. Allt i `:data` är `internal` och nås bara via interfacen i `:domain` och Hilt. En egen modul, `:architecture`, kontrollerar sådana regler med Konsist-tester.

```
TinkerTapHome/
├── app/              # Gränssnittet: Compose-skärmar, ViewModels, navigation och behörighetsflödet
├── domain/           # Modeller, use cases och interface, ren Kotlin utan Android
├── data/             # BLE, enhetsprotokollet, Room och DataStore bakom interfacen i domain
├── architecture/     # Arkitekturtester med Konsist, till exempel att allt i data är internal
├── build-logic/      # Gemensam byggkonfiguration som konventionsplugins
├── docs/             # Roadmap, BLE-protokollet och öppna frågor
├── gradle/           # Versionskatalog och beroendeverifiering
├── .github/          # CI som kör tester, lint och byggen på varje pull request
└── CLAUDE.md         # Regler och arbetssätt för kodagenten
```

## Teknisk stack

| Komponent | Val | Anledning |
|---|---|---|
| Språk | Kotlin | Androids förstaspråk, med null-säkerhet och coroutines. |
| UI | Jetpack Compose och Material 3 | Deklarativt UI som passar skärmar där enheternas status ändras hela tiden. |
| Arkitektur | MVVM med enkelriktat dataflöde | Tydlig gräns mellan UI och logik, och ViewModels som är lätta att testa. |
| Asynkronitet | Coroutines och Flow | BLE är händelsedrivet. Flow beskriver skanning, anslutningsstatus och notifieringar naturligt. |
| Beroendeinjektion | Hilt | Standard på Android och fungerar med ViewModels och livscykeln. |
| BLE | Androids `BluetoothLeScanner` och `BluetoothGatt` bakom egna interface | Inga tredjepartsbibliotek för kärnfunktionen, och BLE kan ersättas med fakes i tester. |
| Lagring | Room för enheter och DataStore för inställningar | Allt sparas lokalt. Databasschemat exporteras och varje ändring får en migration. |
| Bygg | Gradle Kotlin DSL, versionskatalog och konventionsplugins | En plats för versioner och en för gemensam modulkonfiguration. |
| Test | JUnit, Turbine, Compose UI-test och Konsist | Täcker logik, Flow, skärmar och arkitekturregler. |
| CI | GitHub Actions | Kör tester, lint och byggen på varje pull request mot `main`. |

## Begränsningar

- **Bara TinkerTapNode.** Andra BLE-enheter visas vid skanning men kan inte sparas eller styras. Bara protokollversion 3 stöds.
- **Ingen anslutning i bakgrunden.** Appen kopplar ner alla enheter när den inte syns, så att andra telefoner kan ansluta, och den ansluter igen när den öppnas.
- **En telefon åt gången.** En nod tar bara emot en anslutning i taget. Medan appen är ansluten kan ingen annan telefon styra noden.
- **Tokenet sparas okrypterat** i appens lokala databas. Det skyddas av Androids sandlåda för appen, men inte mer än så.
- **Inga rum, scener eller scheman än.** Enheterna visas i en enda lista. Rum och scener är planerade, se [`docs/roadmap.md`](docs/roadmap.md).
- **Begränsat testad hårdvara.** Appen är provad manuellt på en telefon (Android 16) mot en nod. Andra telefoner och Bluetooth-stackar kan bete sig annorlunda.
- **Instrumenterade tester körs inte i CI.** Compose- och databastesterna kräver en telefon eller emulator och körs lokalt.
- **Kräver Android 14 eller senare**, och fungerar inte i emulatorn eftersom BLE saknas där.
- **Språk:** gränssnittet finns på engelska och svenska.

## Utvecklat med

Claude Code (agentdriven utveckling) som del av kursen Next-Generation Software Development with AI.
