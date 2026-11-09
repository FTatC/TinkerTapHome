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
| Bygg | Gradle Kotlin DSL + Version Catalog (`gradle/libs.versions.toml`) + konventionsplugins i `build-logic/` | En plats för alla versioner och en plats för gemensam modulkonfiguration. |
| Test | JUnit, Turbine (Flow), MockK, Compose UI-test | Täcker ViewModels, repositories och UI. |

**Min SDK:** 34 (Android 14). **Target SDK:** 37. Alla versioner, även SDK-nivåerna, finns i `gradle/libs.versions.toml`.

**Paket och `applicationId`:** `io.github.ftatc.tinkertaphome`. `applicationId` får aldrig ändras.

## Arkitekturprinciper

### Lager och moduler
Koden delas i tre lager, och varje lager är en egen Gradle-modul. Beroenden går bara inåt: `ui` → `domain` ← `data`. Modulgränserna gör att kompilatorn stoppar beroenden åt fel håll.

- **`:app`** (paket `…tinkertaphome.ui`): Composables och ViewModels. Ingångspunkterna `TinkerTapApplication` och `MainActivity` ligger i rotpaketet `…tinkertaphome`. Ingen direkt åtkomst till BLE, databas eller Android-system-API:er. Enda undantaget är behörighetsflödet (se Regler). Beror på `:domain`, och på `:data` enbart för att Hilt ska hitta dess moduler. Klasser i `:data` används aldrig direkt från `:app`.
- **`:domain`** (Kotlin/JVM-modul, paket `…tinkertaphome.domain`): Modeller, use cases och repository-interface. Ren Kotlin utan Android-importer. Enda tillåtna beroenden är `kotlinx-coroutines-core` och `javax.inject` (för `@Inject` på use cases). Fakes som delas mellan modulernas tester ligger i `src/testFixtures`.
- **`:data`** (Android-biblioteksmodul, paket `…tinkertaphome.data`): Repository-implementationer, BLE-klient, Room-DAO:er, DataStore och Hilt-moduler som binder implementationerna till interfacen i `:domain`. **Allt i `:data` är `internal`**: klasser, interface, DAO:er, entiteter och toppnivåfunktioner. `:app` kommer bara åt datalagret via interfacen i `:domain`, och Hilt sköter kopplingen. Nya deklarationer i `:data` ska också vara `internal`, annars kan UI-lagret gå förbi `domain`.

**Byggkonfiguration:** Gemensamma inställningar (SDK-nivåer, Java-version, testkörare och standardtestberoenden: JUnit, coroutines-test, Turbine, AndroidX Test) ligger i konventionspluginsen i `build-logic/convention/`:

| Plugin | Används av |
|---|---|
| `tinkertap.android.application` | `:app` |
| `tinkertap.android.library` | `:data` och nya Android-moduler |
| `tinkertap.jvm.library` | `:domain` och nya rena Kotlin-moduler |

Nya moduler använder ett av pluginsen. Kopiera inte `compileSdk`, `minSdk`, `compileOptions` eller standardtestberoenden till en moduls `build.gradle.kts`. Modulen sätter bara det som är unikt för den, till exempel namespace och egna beroenden.

### Regler
- **All I/O sker i `data`-lagret.** BLE, databas, filer och nätverk anropas aldrig från `ui` eller `domain`.
- **Inga globala variabler och inga muterbara singletons.** Tillstånd ägs av en ViewModel eller ett repository och injiceras via Hilt.
  - **Förbjudet:** Kotlin-`object` eller `companion object` med muterbart tillstånd, top-level-`var` och statiska fält som nås utan DI.
  - **Tillåtet:** Klasser med `@Singleton`-scope i Hilt som kapslar in sitt tillstånd privat och bara exponerar det via funktioner och `Flow`/`StateFlow`. BLE-klienten är ett sådant fall: den måste vara en enda instans eftersom den äger GATT-kön. Sådana klasser injiceras alltid via konstruktorn och går att ersätta med fakes i tester.
- **Funktioner ska göra en sak.** Är en funktion längre än ungefär 30 rader, eller behöver ett "och" i namnet, så dela upp den.
- **Ett UI-tillstånd per skärm.** Varje ViewModel exponerar en `StateFlow<XxxUiState>` och tar emot händelser via funktioner. Composables är tillståndslösa där det går.
- **BLE-anrop körs i följd.** GATT-operationer (read/write/enable notification) köas i BLE-klienten; Android klarar inte parallella GATT-anrop.
- **Fel är data.** Förväntade fel, till exempel "enhet utom räckhåll" eller "behörighet saknas", modelleras som sealed classes/interfaces i `domain`. Kasta inte undantag genom lagren.
  - **Standard:** En egen resultattyp med typat fel, till exempel `sealed interface ConnectResult { data class Success(...); sealed interface Failure }` eller en generisk `Outcome<T, E>`. Då måste anroparen hantera varje feltyp och `when` blir uttömmande.
  - **Kotlins `Result<T>` används inte i `domain`-API:er**, eftersom felet bara är en otypad `Throwable`. Den får användas lokalt inom `data` (till exempel med `runCatching` runt ett plattforms-API) men ska översättas till ett domänfel innan värdet lämnar lagret.
  - Oväntade fel (programmeringsfel, brutna invarianter) får kasta som vanligt. `CancellationException` ska aldrig fångas och sväljas.
- **Behörigheter hanteras på ett ställe.** Runtime-behörigheter för BLE (`BLUETOOTH_SCAN` med `neverForLocation`, `BLUETOOTH_CONNECT`) hanteras i ett dedikerat UI-flöde, inte utspritt i skärmarna.
  - **Kontrollen** av om en behörighet är beviljad ligger bakom `BlePermissionChecker` i `:domain`, som implementeras av `AndroidBlePermissionChecker` i `:data`. ViewModels läser behörighetsstatus via interfacet och kan därmed testas med en fake.
  - **Själva förfrågan** (`rememberLauncherForActivityResult` med `RequestMultiplePermissions`) kräver en `Activity` och är därför det enda tillåtna undantaget från regeln att `ui` inte använder Android-system-API:er. Den lever bara i `BlePermissionGate` (`ui/permissions/`) och rapporterar resultatet som en händelse till `BlePermissionViewModel`. Skärmar som kräver BLE läggs innanför `BlePermissionGate` och ber aldrig om behörigheter själva.
- **Inga hårdkodade strängar i UI.** Använd `strings.xml`.

### Språk
| Vad | Språk | Exempel |
|---|---|---|
| Identifierare (klasser, funktioner, variabler, paket, filer) | Engelska | `ObserveDevicesUseCase`, `toDeviceListUiState()` |
| KDoc och kommentarer | Svenska | `/** Strömmar sparade enheter sorterade på namn. */` |
| Testnamn | Engelska, som mening i backticks (instrumenterade tester i camelCase) | `` `shows error state when storage cannot be read` `` |
| Testdata | Svenska går bra | `testDevice(name = "Lampa")` |
| Commit-meddelanden och branchnamn | Engelska | `Normalize BLE addresses with a BleAddress value class` |
| UI-strängar | Engelska i `values/`, svenska i `values-sv/`. Varje ny sträng läggs in i båda. | `devices_empty` |
| CLAUDE.md och dokument i `docs/` | Svenska | – |

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
- **Databasmigreringar testas.** Varje Room-migration får ett test med `MigrationTestHelper` som migrerar från föregående schemaversion och verifierar datan (se Databas nedan).
- **En ändring är inte klar förrän rätt testsvit passerar.** Rapportera tydligt om något test inte kunde köras eller fallerar.
  - **Alltid:** `./gradlew test` (JVM-enhetstester: ViewModels, use cases, repositories med fakes).
  - **Dessutom `./gradlew connectedAndroidTest`** när ändringen rör Composables, navigation, Room-DAO:er eller migreringar, eller annan kod vars tester ligger i `androidTest`. Compose UI-tester och `MigrationTestHelper` är instrumenterade och körs **inte** av `./gradlew test`.
  - Finns ingen enhet eller emulator tillgänglig: säg uttryckligen att de instrumenterade testerna inte har körts. Påstå inte att ändringen är verifierad.

### Databas (Room)
- **Schemat exporteras från första versionen.** `@Database(exportSchema = true)` och Room-pluginens `room { schemaDirectory("$projectDir/schemas") }` i `data/build.gradle.kts`. Katalogen `data/schemas/` checkas in. Den räknas inte som en genererad fil som ska ignoreras.
- **Varje schemaändring höjer `TinkerTapDatabase.VERSION` och får en migration** i samma ändring: en `AutoMigration` när Room klarar det, annars en manuell `Migration` som läggs till i `ALL_MIGRATIONS` (`data/.../local/Migrations.kt`). Den exporterade JSON-filen för den nya versionen checkas in tillsammans med migrationen.
- **Databasen byggs bara via `TinkerTapDatabase.build()`.** Både Hilt och `MigrationTest` använder den, så testet provar exakt den konfiguration som appen kör med.
- **`MigrationTest` (instrumenterat) ska passera efter varje schemaändring.** Det skapar en databas med den första schemaversionen och kontrollerar att den kan uppgraderas till nuvarande version, att schemat stämmer och att data finns kvar. Bygg modulen en gång efter att versionen höjts, så att den nya schema-JSON:en exporteras innan testerna körs.
- **Ändra aldrig en redan incheckad schema-JSON** eller en migration som har funnits i en release. Rätta i stället med en ny version och en ny migration.

## Git-arbetsflöde

- **Inga commits direkt på `main`.** Allt arbete sker i en feature-branch.
- **Skapa branchen innan första ändringen.** Står du på `main` när ett arbete börjar, skapa och byt till en ny branch innan någon fil ändras.
- **Namnge brancher efter typ och innehåll:** `feature/<kort-beskrivning>`, `fix/<kort-beskrivning>` eller `chore/<kort-beskrivning>`, till exempel `feature/device-scan-screen`.
- **En branch per avgränsad uppgift.** Blanda inte orelaterade ändringar i samma branch.
- **Ändringar når `main` endast via pull request**, aldrig genom lokal merge eller push.

## Saker agenten INTE ska göra

- **Lägg inte till externa beroenden** (bibliotek, Gradle-plugins) utan att fråga först. Det gäller särskilt BLE-bibliotek som Nordic BLE eller RxAndroidBle.
- **Stäng inte av och kringgå inte beroendeverifieringen** (`gradle/verification-metadata.xml`, se Vanliga kommandon). Ett fel om en checksumma som inte stämmer kan betyda att ett beroende har manipulerats. Undersök det och fråga mig innan filen ändras för att få bygget grönt.
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
./gradlew connectedAndroidTest   # Instrumenterade tester: Compose UI, Room, migreringar (kräver enhet/emulator)
./gradlew lint                   # Lint
./gradlew :domain:test           # Bara en modul
```

**Beroendeverifiering:** Gradle kontrollerar SHA-256 för varje beroende mot `gradle/verification-metadata.xml` och stoppar bygget om något saknas eller inte stämmer. När ett beroende har lagts till eller uppdaterats (efter godkännande), uppdatera filen med samma uppgifter som annars körs, så att även lint och de instrumenterade testernas verktyg kommer med:

```bash
./gradlew --write-verification-metadata sha256 --refresh-dependencies --no-configuration-cache \
    test lint assembleDebug assembleRelease assembleDebugAndroidTest connectedAndroidTest
```

Granska diffen innan den committas: nya poster ska bara gälla de beroenden som ändrades.

Gradle behöver en JDK och Android SDK. Om `JAVA_HOME` eller `ANDROID_HOME` saknas i miljön, sätt dem för kommandot, till exempel med Android Studios inbyggda JDK:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="$HOME/AppData/Local/Android/Sdk"
```

> Obs: BLE fungerar inte i emulatorn. Allt som rör riktig BLE-kommunikation måste testas på fysisk enhet.
