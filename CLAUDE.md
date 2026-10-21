# CLAUDE.md

Instruktioner för Claude Code när den arbetar i det här repot.

## Vad projektet gör

TinkerTapHome är en Android-app för hemautomation över Bluetooth Low Energy (BLE).
Appen hittar, parar och styr hemmabyggda eller kommersiella BLE-enheter, till exempel
reläer, lampor, sensorer och strömbrytare, direkt från telefonen.

**Problemet den löser:** De flesta hemautomationslösningar kräver en hubb, ett molnkonto
eller en leverantörsspecifik app per tillverkare. TinkerTapHome styr enheterna lokalt och
direkt över BLE, utan moln och utan internet, i en och samma app. Den är gjord för den som
bygger egna enheter (ESP32, nRF52 och liknande) och vill ha ett enkelt gränssnitt för dem.

## Teknikstack och varför

| Område | Val | Varför |
|---|---|---|
| Språk | Kotlin | Officiellt förstaspråk för Android; null-säkerhet och coroutines. |
| UI | Jetpack Compose + Material 3 | Deklarativt UI som passar tillståndsdrivna skärmar (enhetsstatus ändras hela tiden). |
| Arkitektur | MVVM + Unidirectional Data Flow | Tydlig separation mellan UI och logik; lätt att testa ViewModels. |
| Asynkronitet | Kotlin Coroutines + Flow | BLE är händelsedrivet; Flow modellerar scan-resultat, anslutningsstatus och notifieringar naturligt. |
| Beroendeinjektion | Hilt | Standard på Android, integrerar med ViewModel och livscykel. |
| BLE | Android-plattformens `BluetoothLeScanner` / `BluetoothGatt` bakom ett eget abstraktionslager | Inga tredjepartsberoenden för kärnfunktionen; abstraktionen gör att BLE kan mockas i tester. |
| Lokal lagring | Room (enheter, rum, scener) + DataStore (inställningar) | Offline-först; inget moln behövs. |
| Bygg | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) | En plats för alla versioner. |
| Test | JUnit, Turbine (Flow), MockK, Compose UI-test | Täcker ViewModels, repositories och UI. |

**Min SDK:** 34 (Android 14). **Target SDK:** senaste stabila.

## Arkitekturprinciper

### Lager
Koden delas i tre lager. Beroenden går bara inåt: `ui` → `domain` ← `data`.

- **`ui/`**: Composables och ViewModels. Ingen direkt åtkomst till BLE, databas eller Android-system-API:er.
- **`domain/`**: Modeller, use cases och repository-interface. Ren Kotlin utan Android-importer.
- **`data/`**: Repository-implementationer, BLE-klient, Room-DAO:er, DataStore.

### Regler
- **All I/O sker i `data`-lagret.** BLE, databas, filer och nätverk anropas aldrig från `ui` eller `domain`.
- **Inga globala variabler och inga muterbara singletons.** Tillstånd ägs av en ViewModel eller ett repository och injiceras via Hilt.
- **Funktioner ska göra en sak.** Är en funktion längre än ungefär 30 rader, eller behöver ett "och" i namnet, så dela upp den.
- **Ett UI-tillstånd per skärm.** Varje ViewModel exponerar en `StateFlow<XxxUiState>` och tar emot händelser via funktioner. Composables är tillståndslösa där det går.
- **BLE-anrop körs i följd.** GATT-operationer (read/write/enable notification) köas i BLE-klienten; Android klarar inte parallella GATT-anrop.
- **Fel är data.** Använd `Result` eller sealed classes för förväntade fel, till exempel "enhet utom räckhåll" eller "behörighet saknas". Kasta inte undantag genom lagren.
- **Behörigheter hanteras på ett ställe.** Runtime-behörigheter för BLE (`BLUETOOTH_SCAN` med `neverForLocation`, `BLUETOOTH_CONNECT`) hanteras i ett dedikerat UI-flöde, inte utspritt i skärmarna.
- **Inga hårdkodade strängar i UI.** Använd `strings.xml`.

### Tester
- **Tester skrivs samtidigt som koden.** Varje ny eller ändrad funktion, klass eller ViewModel får nya eller uppdaterade tester i samma ändring. Tester skjuts aldrig upp till senare.
- **Ändrat beteende kräver ändrade tester.** Rör en ändring befintlig logik ska tillhörande tester uppdateras så att de verifierar det nya beteendet. Gamla tester får inte bara fortsätta passera av en slump.
- **Buggfixar får ett regressionstest** som fallerar före fixen och passerar efter.
- **Otestbar kod skrivs om tills den går att testa.** Är en kodsnutt svår att testa ska den struktureras om innan eller samtidigt som testerna skrivs. Vanliga grepp:
  - Injicera beroenden via konstruktorn i stället för att skapa dem internt.
  - Lägg Android- och system-API:er (BLE, `Context`, klocka, `Dispatchers`) bakom interface som kan ersättas med fakes.
  - Bryt ut ren logik ur Composables, callbacks och livscykelmetoder till rena funktioner eller use cases.
  - Injicera `CoroutineDispatcher` och tidskälla så att tester kan styra dem.
- **BLE mockas via interfacet**, aldrig via den riktiga stacken.
- **En ändring är inte klar förrän `./gradlew test` passerar.** Rapportera tydligt om något test inte kunde köras eller fallerar.

## Git-arbetsflöde

- **Inga commits direkt på `main`.** Allt arbete sker i en feature-branch.
- **Skapa branchen innan första ändringen.** Står du på `main` när ett arbete börjar, skapa och byt till en ny branch innan någon fil ändras.
- **Namnge brancher efter typ och innehåll:** `feature/<kort-beskrivning>`, `fix/<kort-beskrivning>` eller `chore/<kort-beskrivning>`, till exempel `feature/device-scan-screen`.
- **En branch per avgränsad uppgift.** Blanda inte orelaterade ändringar i samma branch.
- **Ändringar når `main` endast via pull request**, aldrig genom lokal merge eller push.

## Saker agenten INTE ska göra

- **Lägg inte till externa beroenden** (bibliotek, Gradle-plugins) utan att fråga först. Det gäller särskilt BLE-bibliotek som Nordic BLE eller RxAndroidBle.
- **Byt inte databas eller lagringslösning** (Room/DataStore) och byt inte DI-ramverk, UI-ramverk eller arkitekturmönster.
- **Lägg inte till molnfunktioner, analys, telemetri eller nätverksanrop.** Appen är lokal och offline med flit.
- **Ändra inte `minSdk`, `targetSdk`, Kotlin- eller AGP-version** utan att fråga.
- **Ändra inte behörigheter i `AndroidManifest.xml`** utan att fråga och motivera varför.
- **Ändra inte befintliga BLE-protokoll** (service-/characteristic-UUID:er, byteformat). Riktiga enheter ute i världen förlitar sig på dem.
- **Skriv inte om databasschemat utan en migration.** Använd aldrig `fallbackToDestructiveMigration()`.
- **Committa inte hemligheter**, keystores, `local.properties` eller genererade filer.
- **Stäng inte av, hoppa inte över och ta inte bort tester eller lint-regler** för att få bygget grönt.
- **Gör inga stora omstruktureringar eller filflyttar** utan att först beskriva planen och få ett godkännande.
- **Committa eller pusha inte** om jag inte uttryckligen ber om det.
- **Committa, merga eller pusha aldrig direkt till `main`**, inte ens när jag ber om en commit. Gör den på en feature-branch.
- **Använd inte `git push --force`** mot delade brancher och skriv inte om historiken på `main`.

## Vanliga kommandon

```bash
./gradlew assembleDebug          # Bygg debug-APK
./gradlew test                   # Enhetstester
./gradlew connectedAndroidTest   # Instrumenterade tester (kräver enhet/emulator)
./gradlew lint                   # Lint
```

> Obs: BLE fungerar inte i emulatorn. Allt som rör riktig BLE-kommunikation måste testas på fysisk enhet.
