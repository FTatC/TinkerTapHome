# Roadmap: nästa steg i implementationen av TinkerTapHome

## Status (2026-11-13)

| Fas | Status | PR |
|---|---|---|
| 0 Städning | ✅ Klar | #3 |
| 1.1 Instrumenterade tester lokalt | ✅ Klar | #5 |
| 1.2 Gemensam testdata | ✅ Klar | #6 |
| 1.3 `BleAddress` | ✅ Klar | #7 |
| 1.4 Fel som data i enhetslistan | ✅ Klar | #8 |
| 1.5 Fast plats för migreringar | ✅ Klar | #9 |
| 1.6 Konventionsplugins | ✅ Klar | #10 |
| 1.7 Språk i koden | ✅ Klar | #11 |
| 2.1 Behörigheter | ✅ Klar | #12, #21, #22 |
| 2.2 Skanning bakom ett interface | ✅ Klar | #13 |
| 3 Skanningsskärm och navigation | ✅ Klar | #14, #25 |
| 4 Spara en enhet | ✅ Klar | #15, #19 |
| 5 Anslutning och GATT-kö | ✅ Klar | #27, #28 |
| 6 Styrning via enhetsprotokollet | ⏳ Nästa. Besluten och planen är klara, steg 6.1–6.6 (se fas 6). Fråga 1 och 6 verifieras på hårdvara under fasen. | – |
| 7 Senare | ⬜ Ej planerad i detalj | – |

**Härdning utanför planen**, gjord mellan fas 4 och 5:
- #16: Fel från externa anrop hanteras i stället för att tappas eller krascha appen.
- #17: Enhetsnamn från reklampaket valideras och skanningslistan har ett tak.
- #18: Gradle Plugin Portal får bara leverera de plugins som behövs.
- #20: Tydligare namn och mer lättlästa skanningsflöden.
- #23: Beroendeverifiering med SHA-256 (`gradle/verification-metadata.xml`).
- #24: Signeringsuppgifter och andra hemliga filer ignoreras av git.

## Context

*Ursprunglig bakgrund från när planen skrevs.*

Skelettet är på plats och mergat till `main` (PR #2). Det har tre moduler, `:app`, `:domain` och `:data`, och en enhetslista som läser från Room. Synlighetsfixen för `:data` ligger på `fix/data-module-visibility` och väntar på PR.

Själva kärnfunktionen saknas fortfarande: att hitta, spara, ansluta till och styra BLE-enheter. Dessutom finns teknisk skuld från analysen som blir dyrare för varje BLE-funktion som byggs ovanpå.

Planen gör därför först de grundläggande rättelserna (fas 1) och bygger sedan BLE-funktionen lager för lager. Varje fas är en egen branch och en egen PR, och varje fas lämnar `main` byggbar och testad.

**Beslut som krävs från dig** är markerade med 🔶. Enligt CLAUDE.md får de inte tas av agenten.

---

## ✅ Fas 0: Städning (innan något nytt påbörjas)

| Steg | Vad | Filer |
|---|---|---|
| 0.1 | ✅ Mergea PR:en för `fix/data-module-visibility` | – |
| 0.2 | ✅ (beslut: behålls, PR #3) Bestäm vad som ska göras med Android Studios ocommittade ändringar: foojay-pluginen och `org.gradle.tooling.parallel`. Om de ska vara kvar läggs de i `chore/gradle-jdk-toolchain`. | `settings.gradle.kts`, `gradle.properties`, `gradle/gradle-daemon-jvm.properties` |

---

## Fas 1: Grunden (teknisk skuld från analysen)

Ordningen är vald så att datamodellen är rättad innan BLE-koden börjar använda den. CI väntar till senare (fas 7). Tills dess körs alla tester lokalt.

### ✅ 1.1 Kör de instrumenterade testerna lokalt: inga kodändringar
- Starta en emulator och kör `./gradlew connectedAndroidTest`. Då körs `DeviceDaoTest` och `DeviceListContentTest`, som hittills bara har kompilerats.
- Om något test fallerar rättas det på en `fix/`-branch innan fas 1.2 påbörjas.

### ✅ 1.2 Gemensam testdata: `chore/test-fixtures`
- **Ny:** `domain/src/testFixtures/.../domain/model/TestDevices.kt` med `fun testDevice(id: Long = 1, name: String = "Lampa", address: String = …)`.
- **Ersätt de upprepade `Device(...)`-raderna i:**
  - `ObserveDevicesUseCaseTest.kt`
  - `RoomDeviceRepositoryTest.kt`
  - `DeviceListViewModelTest.kt`
  - `DeviceListContentTest.kt`
  - `DeviceListScreen.kt` (preview). Gäller bara om `testFixtures` görs tillgänglig för `debug`. Annars får previewn behålla sin egen data.
- **`DeviceListViewModelTest.kt`:** byt testet "shows devices sorted by name" mot "passes devices through in use case order", så att ViewModel-testet inte längre testar use casets sortering.
- **`RoomDeviceRepositoryTest.kt`:** ta bort `FakeDeviceDao.insert`, som inte används, eller låt den kasta `NotImplementedError` tills den behövs.
- **`DeviceListViewModel.kt`:** gör `toUiState` till `private` och döp om den till `List<Device>.toDeviceListUiState()`.

### ✅ 1.3 `BleAddress` som value class: `fix/ble-address-normalization`
- **Ny:** `domain/.../domain/model/BleAddress.kt`
  - `@JvmInline value class BleAddress private constructor(val value: String)`
  - `companion fun parse(raw: String): BleAddress?` normaliserar till versaler och kontrollerar formatet `XX:XX:XX:XX:XX:XX`.
- **Ändra:** `Device.address: String` → `BleAddress`.
- **Ändra i `:data`:**
  - `DeviceEntity.toDomain()` använder `BleAddress.parse`. En ogiltig rad hoppas över och loggas inte som ett fel som stoppar listan.
  - En ny `Device.toEntity()` behövs för fas 4.
- **Schemat ändras inte**, eftersom kolumnen fortfarande är `TEXT`. Adresser normaliseras när de skrivs, så det behövs ingen migration.
- **Tester:** `BleAddressTest` med gemener, fel längd, fel avgränsare och giltig adress. Mappningstesterna uppdateras.
- **Regressionstest:** `"aa:bb:…"` och `"AA:BB:…"` ger samma `BleAddress`.
- ✅ **Beslut (2026-11-01): enheterna parkopplas (bondas).** Android ger då en stabil identitetsadress, så BLE-adressen används som enhetens identitet och är unik i databasen. Parkopplingen görs när enheten ansluts i fas 5. Ingen egen identifierare i reklampaketet behövs.

### ✅ 1.4 Fel som data i enhetslistan: `fix/device-list-error-state`
- **Ny:** `domain/.../domain/model/DeviceError.kt`: `sealed interface DeviceError { data object StorageUnavailable }`.
- **`DeviceRepository.observeDevices()`** returnerar `Flow<Outcome<List<Device>, DeviceError>>`. Det kräver en ny `domain/.../domain/Outcome.kt` (sealed: `Success` och `Failure`), som är den gemensamma resultattyp CLAUDE.md beskriver.
- **`RoomDeviceRepository`:** `.catch { e -> if (e is CancellationException) throw e; emit(Failure(StorageUnavailable)) }`.
- **`DeviceListUiState`:** ett nytt tillstånd `Error`. `DeviceListScreen.kt` får `ErrorState` och en ny sträng i `values/` och `values-sv/`.
- **Tester:**
  - Repositoryt översätter undantag till `Failure`.
  - ViewModeln går över till `Error`.
  - `CancellationException` sväljs inte.
  - Ett Compose-test för feltillståndet.

### ✅ 1.5 Fast plats för migreringar: ingår i 1.4 eller görs separat
- **Ny:** `data/.../data/local/Migrations.kt` med `internal val ALL_MIGRATIONS: Array<Migration> = emptyArray()`.
- **`DatabaseModule.kt`:** `.addMigrations(*ALL_MIGRATIONS)`.
- **Ny:** `data/src/androidTest/.../MigrationTest.kt` med `MigrationTestHelper` och `runMigrationsAndValidate` för alla migreringar i `ALL_MIGRATIONS`. Så länge listan är tom gör testet ingenting.

### ✅ 1.6 Konventionsplugins: `chore/build-logic` (görs innan fas 2 lägger till fler moduler)
- **Ny:** `build-logic/` som included build med `tinkertap.android.library`, `tinkertap.android.application` och `tinkertap.jvm.library`. Pluginsen sätter compileSdk, minSdk, jvmTarget och testberoenden.
- **Ändra:** `settings.gradle.kts` (`includeBuild("build-logic")`), `app/`, `data/` och `domain/build.gradle.kts` krymper.
- Det kommer inga nya externa beroenden. Pluginsen använder AGP och KGP, som redan finns i version catalog.

### ✅ 1.7 Språk i koden
- Bestäm vilket språk som gäller för KDoc, kommentarer, testnamn och commits.
- Lägg in beslutet i CLAUDE.md. Ingen kod ändras retroaktivt.

---

## Fas 2: Abstraktion för BLE och behörighetsflödet

### ✅ 2.1 Behörigheter: `feature/ble-permissions`
- ✅ **`AndroidManifest.xml`:** godkänt och infört i PR #12. Ändringarna och skälen:
  - `BLUETOOTH_SCAN` med `usesPermissionFlags="neverForLocation"` för att kunna skanna utan platsbehörighet.
  - `BLUETOOTH_CONNECT` för att ansluta och läsa enheternas namn.
  - `<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>`.
- **`:domain`:**
  - `model/BlePermissionState.kt` (`Granted` och `Denied(permanently: Boolean)`)
  - `repository/PermissionChecker.kt` (interface)
- **`:data`:** `permission/AndroidPermissionChecker.kt` (`internal`, använder `ContextCompat.checkSelfPermission`) och en bindning i `di/`.
- **`:app`:**
  - `ui/permissions/BlePermissionGate.kt` är den enda platsen där `rememberLauncherForActivityResult(RequestMultiplePermissions)` används, vilket är undantaget i CLAUDE.md.
  - `ui/permissions/PermissionViewModel.kt` och `PermissionUiState.kt`.
- **Tester:** ViewModel med `FakePermissionChecker` i domains `testFixtures`, och ett Compose-test av gatens tre tillstånd.

### ✅ 2.2 Skanning bakom ett interface: `feature/ble-scan-core`
- **`:domain`:**
  - `model/ScannedDevice.kt` (`address: BleAddress`, `name: String?`, `rssi: Int`)
  - `model/BleError.kt` (sealed: `BluetoothOff`, `PermissionMissing`, `ScanFailed(code)`)
  - `repository/DeviceScanner.kt`: `fun scan(): Flow<Outcome<List<ScannedDevice>, BleError>>`
  - `usecase/ScanForDevicesUseCase.kt`: filtrerar bort enheter som redan är sparade, genom att kombinera med `DeviceRepository`, och sorterar på RSSI.
- **`:data`:**
  - `ble/BluetoothAdapterSource.kt` är ett internt interface runt `BluetoothManager` och `BluetoothLeScanner`, så att skannern kan testas utan riktig Bluetooth-stack.
  - `ble/AndroidDeviceScanner.kt` använder `callbackFlow` runt `ScanCallback`, tar bort dubbletter per adress och stoppar skanningen i `awaitClose`.
  - `ble/ScanResultMapper.kt` är en ren funktion från `ScanResult` till `ScannedDevice` som kan testas separat.
  - `di/BleModule.kt`.
- **Tester:**
  - Use caset testas med `FakeDeviceScanner` och `FakeDeviceRepository`.
  - `AndroidDeviceScanner` testas med en fake `BluetoothAdapterSource`.
  - Mappern testas.
  - Inga tester mot den riktiga BLE-stacken, enligt CLAUDE.md.
- **Injicera** `CoroutineDispatcher` och en tidskälla (för skanningens timeout) via en ny `di/DispatchersModule.kt` med qualifier-annotationer.

---

## ✅ Fas 3: Skanningsskärm och navigation: `feature/device-scan-screen`

- ✅ **Beslut: typsäkra rutter med `kotlinx-serialization`.** Navigation Compose behöver pluginen `kotlinx-serialization` för typsäkra rutter. Det är ett nytt Gradle-plugin och kräver godkännande. Alternativet är strängbaserade rutter utan pluginen.
- **`:app`:**
  - `ui/navigation/TinkerTapNavHost.kt` och `Routes.kt`
  - `ui/scan/ScanScreen.kt`, `ScanViewModel.kt` och `ScanUiState.kt` (`Idle`, `Scanning(results)`, `Error(BleError)`)
  - FAB på `DeviceListScreen.kt` som leder till skanningen
  - Nya strängar på engelska och svenska
- **`MainActivity.kt`:** `setContent { TinkerTapTheme { BlePermissionGate { TinkerTapNavHost() } } }`.
- **Tester:**
  - ViewModel: skanning startar och stoppar med livscykeln, och fel blir rätt UI-tillstånd.
  - Compose-tester för varje tillstånd.
  - Ett navigationstest.
- **Manuell verifiering på fysisk enhet.** BLE fungerar inte i emulatorn.

---

## ✅ Fas 4: Spara en enhet (första skrivningen): `feature/save-device`

- **`:domain`:**
  - `usecase/SaveDeviceUseCase.kt`
  - `DeviceRepository.save(address, name): Outcome<Device, SaveDeviceError>` där `SaveDeviceError` har `AlreadySaved` och `StorageUnavailable`
- **`:data`:** `RoomDeviceRepository.save` fångar `SQLiteConstraintException` och översätter till `AlreadySaved`. Här löses punkt 3 i analysen. `DeviceDao.insert` används nu i produktionskoden.
- **`:app`:**
  - Ett tryck på en skannad enhet öppnar en dialog för namn, med standardnamnet från reklampaketet.
  - När enheten är sparad går appen tillbaka till listan.
- **Tester:**
  - Översättningen från undantag till fel.
  - Use case.
  - ViewModel.
  - `DeviceDaoTest` finns redan och täcker dubbletter.

---

## ✅ Fas 5: Anslutning och GATT-kö: `feature/gatt-connection`

- **`:domain`:**
  - `model/ConnectionState.kt` (`Disconnected`, `Connecting`, `Connected`, `Failed(BleError)`)
  - `repository/DeviceConnection.kt`: `connect`, `disconnect`, `observeState`, `read`, `write`, `enableNotifications`, alla som `suspend` och med `Outcome`
- **`:data`:**
  - `ble/GattOperationQueue.kt` är kärnan. Den är ren Kotlin med en `Channel` eller `Mutex`, kör en operation i taget, väntar på callback via `CompletableDeferred`, har timeout via den injicerade tidskällan och översätter GATT-status till `BleError`.
  - `ble/GattHandle.kt` är ett internt interface runt `BluetoothGatt`, så att kön kan testas med en fake.
  - `ble/AndroidDeviceConnection.kt` är en `@Singleton` som håller en kö per enhet. Det är tillåtet enligt CLAUDE.md eftersom tillståndet är inkapslat.
- **Tester, som är viktigast i den här fasen:**
  - Kön kör aldrig två operationer samtidigt.
  - Timeout.
  - Felstatus.
  - Anslutningen bryts mitt i en operation.
  - Avbrott.

  Allt testas med fake `GattHandle` och `StandardTestDispatcher`.
- **UI:** visa anslutningsstatus på varje rad i `DeviceListScreen` och lägg till en detaljskärm `ui/device/`.
- **Uppföljning efter protokollspecifikationen** (fråga 5 i `docs/open-questions.md`): firmwaren rekommenderar `autoConnect = true` för kända noder och `requestMtu(247)`. `AndroidGattConnector` använder i dag `autoConnect = false` och begär ingen MTU. Write With Response och en GATT-operation i taget stämmer redan. Beslutet om `autoConnect` (2026-11-13) genomförs i fas 6.

---

## Fas 6: Styrning via enhetsprotokollet

Underlag: `docs/ble-protocol.md` (protokollversion 3, firmware TinkerTapNode `0.4.0`, Arduino-repots `main`, commit `7632768`) och `docs/open-questions.md`. Krockarna med fas 5-koden kommer från firmwarens `docs/app-flow.md`, se "Jämfört med appen i dag" i `docs/ble-protocol.md`.

### Beslut

- ✅ **Bara TinkerTapNode** (beslut 2026-11-13): i fas 6 går det bara att spara noder som annonserar TinkerTapNodes service-UUID `7d3c0000-…`. Andra BLE-enheter visas i skanningen men kan inte sparas. Alla sparade enheter är därmed noder, så databasen behöver ingen kolumn för enhetstyp. Stöd för andra enhetstyper får vänta till en senare fas.
- ✅ **Protokollversion** (fråga 4): bara `proto` = `3` stöds. Andra versioner ger ett domänfel som skiljer på för gammal nod och för gammal app.
- ✅ **Parkoppling** (fråga 7): appen ber om nodens knapp när noden sparas, med en text och utan bild. Noden sparas först när parkopplingen har lyckats. Senare anslutningar parkopplar inte. Har noden glömt telefonen, vilket syns som GATT-status `5` eller `15`, visar appen en infotext om att ta bort noden i Bluetooth-inställningarna och parkoppla igen.
- ✅ **Token** (fråga 2 och 3): tokenet anges när noden sparas, förifyllt med `tinker`, och noden sparas först när `AUTH` har lyckats. Det sparas per nod i Room utan kryptering. Efter `AUTH_FAILED` försöker appen inte igen automatiskt. Detaljvyn ber om ett nytt token.
- ✅ **Databasversion 2** (steg 6.4): kolumnen `auth_token` i `devices` kan vara tom och läggs till med en `AutoMigration` från version 1. Tokenet ligger inte i `Device`, utan läses och ändras med `DeviceRepository.authToken()` och `updateAuthToken()`, så att det inte följer med till listorna i gränssnittet. `DeviceEntity.toString()` visar aldrig tokenet.
- ✅ **Anslutning** (fråga 5): sparade noder ansluts med `autoConnect = true` medan appen syns, och alla noder kopplas ner när appen går till bakgrunden. Appen begär `requestMtu(247)`.
- ✅ **Info-JSON:** tolkas med `kotlinx-serialization-json` (samma version som `kotlinx-serialization-core`) och `ignoreUnknownKeys = true`. Biblioteket och pluginet `kotlin-serialization` läggs till i `:data` i steg 6.1, och `gradle/verification-metadata.xml` uppdateras i samma ändring.
- 🔶 **Bestäms under arbetet:** hur en sparad nod parkopplas igen (spara-flödet ger `AlreadySaved`).
- ✅ **Detaljvyn utan knappar för att ansluta** (steg 6.3, beslut 2026-11-13): anslutningen sköts automatiskt, så knapparna Anslut och Koppla ner är borttagna. När ett fel har stoppat anslutningen visar detaljvyn "Försök igen". Efter ett avbrott ansluter appen igen efter 5 s. Fel som kräver något av användaren, som `PairingMissing` och `BluetoothOff`, stoppar försöken tills användaren trycker på "Försök igen" eller appen visas på nytt.
- ✅ **Väntan på Result** (steg 6.2): högst 2 s efter att skrivningen har bekräftats (`GattSession.REPLY_TIMEOUT`). Noden skickar Result före bekräftelsen, så tiden är bara marginal. Kommer inget svar stängs anslutningen med `ConnectionError.NoReply`.
- 🔶 **Verifieras på hårdvara under fasen:** fråga 1 (parkopplingen med Android 16) och fråga 6 (ordningen mellan Result och skrivbekräftelsen).

### Steg

Varje steg är en egen branch och en egen PR, i den här ordningen (beslut 2026-11-13).

| Steg | Branch | Innehåll | Tester |
|---|---|---|---|
| 6.1 | `feature/node-protocol` | **Protokollagret.** `:data/ble/protocol/`: kodning av Command, avkodning av Result och State, tolkning av Info. `:domain`: utgångstyperna `Digital`, `Pwm` och `Servo`, Info som domänmodell, fel från Result och protokollversion som sealed typer. | JVM-tester mot exemplen i `docs/ble-protocol.md`, bit för bit. De skyddar mot att protokollet ändras av misstag. |
| 6.2 | `fix/gatt-node-errors` | **Fixar i GATT-lagret.** Status `5` och `15` blir ett eget `ConnectionError` för "noden har glömt telefonen". Omförsök efter 1–2 s vid status `133`. `requestMtu(247)` före `discoverServices()`, där ett lägre värde inte är ett fel. Ett sätt att skriva och vänta på ett matchande Result, eftersom `write()` kan returnera innan notifieringen har behandlats. | JVM-tester för `GattSession` med fake `GattHandle`. |
| 6.3 | `feature/node-connection` | **Anslutningen delas upp.** `GattSession.connect()` delas i parkoppling, som används när noden sparas, och vanlig anslutning, som ger ett fel om parkopplingen saknas. `autoConnect = true` för sparade noder med ett nytt tillstånd för att vänta på noden i stället för `CONNECT_TIMEOUT`. Gränssnittet signalerar förgrund och bakgrund med `LifecycleStartEffect`, och appen kopplar ner alla noder i bakgrunden. **Ändrar fas 5-koden**: planen för uppdelningen beskrivs i PR:en innan den görs. | JVM-tester för `GattSession` och ViewModel. Manuellt på telefon. |
| 6.4 | `feature/device-token-column` | **Databasversion 2.** En kolumn för token i `devices` som kan vara tom, en migration i `ALL_MIGRATIONS`, schema-JSON för version 2 och `DeviceRepository` med token. | `MigrationTest` och DAO-tester med `connectedAndroidTest`. |
| 6.5 | `feature/save-node` | **Spara-flödet.** Skanningen läser service-UUID:t och markerar TinkerTapNode, och bara de kan sparas. Flödet: text om knappen, parkoppling, Info med kontroll av `proto`, `AUTH` med tokenet och sedan sparas noden med token. | JVM-tester för use case och ViewModel, Compose-tester för dialogen, manuellt på telefon. |
| 6.6 | `feature/device-control` | **Styrning.** En session efter anslutningen som inom `ato` läser Info, slår på Result och State, läser State och skickar `AUTH`. `ObserveOutputsUseCase`, `SetOutputUseCase` och `AllOffUseCase`. Detaljvyn visar utgångarna från State, `ALL_OFF`, fel och fält för nytt token. `PING` om `hb` > 0. | JVM-tester för use cases och ViewModel, Compose-tester, manuellt mot en riktig nod. Här verifieras fråga 1 och 6. |

### Förberedelser

- **Noden** flashas med firmware från Arduino-repots `main` (`7632768` eller senare), byggd med `--library lib/ArduinoBLE`. Det behövs från steg 6.3.
- **Telefonen:** `ContextPermissionStatusTest.reportsAGrantedPermissionAsGranted` fallerar på ColorOS, eftersom systemet hindrar testet från att ge behörigheter. Slå på "Disable permission monitoring" i Utvecklaralternativ innan steg 6.4, där `connectedAndroidTest` måste passera för migreringstestet.

---

## Fas 7: Senare (planeras inte i detalj nu)
- **CI:** `.github/workflows/ci.yml` som kör `test`, `lint` och `assemble*` på varje PR, kanske också `connectedAndroidTest` på en emulator (🔶 tredjeparts-Action), samt branch protection på `main`.
- **Rum och scener:** nya Room-tabeller, var och en med en migration.
- **Inställningar:** DataStore, till exempel för skanningens längd och tema.
- **Anslutningar i bakgrunden:** en foreground service. 🔶 Kräver manifestbehörigheten `FOREGROUND_SERVICE_CONNECTED_DEVICE`. Fram till dess kopplar appen ner när den går till bakgrunden (fråga 5).
- 🔶 **Arkitekturtest:** Konsist, ett nytt beroende, för att automatiskt upptäcka nya `public`-klasser i `:data`.

---

## Beroenden mellan faserna

```
Fas 0 ─▶ 1.1 Lokala instrumenterade tester ─▶ 1.2 ─▶ 1.3 BleAddress ─▶ 1.4/1.5 Fel och migreringar ─▶ 1.6 build-logic
                                                                              │
                         2.1 Behörigheter ◀────────────────────────────────────┘
                              │
                         2.2 Skanning (core) ─▶ 3 Skanningsskärm ─▶ 4 Spara ─▶ 5 GATT ─▶ 6 Styrning
```

1.7 (språk) kan göras när som helst, men gärna innan fas 2.

## Verifiering (lokalt, gäller varje fas)
1. Sätt `JAVA_HOME` och `ANDROID_HOME`, som beskrivs i CLAUDE.md.
2. `./gradlew test lint assembleDebug assembleDebugAndroidTest`: allt ska vara grönt innan commit.
3. `./gradlew connectedAndroidTest` med en emulator igång, för faser som rör Compose eller Room.
4. Från fas 2.2 även manuell verifiering på en **fysisk** telefon med en riktig BLE-enhet (ESP32 eller nRF52), eftersom BLE inte fungerar i emulatorn.
5. PR-beskrivningen anger vilka testsviter som körts lokalt. Om något inte har kunnat köras, till exempel för att ingen enhet fanns, ska det stå uttryckligen.
