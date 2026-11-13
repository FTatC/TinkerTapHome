# Öppna frågor

Beslut som agenten inte får ta själv (se CLAUDE.md). Varje fråga har ett svarsfält. När en fråga är besvarad skrivs svaret och datumet in, och beslutet förs in i det dokument som frågan gäller, till exempel `docs/ble-protocol.md` eller `docs/roadmap.md`.

| # | Fråga | Gäller | Status |
|---|---|---|---|
| 1 | [Parkoppling och identitet](#1-parkoppling-och-identitet) | Fas 6, beslut 1.3 | 🔶 Öppen |
| 2 | [Var tokenet kommer ifrån och var det sparas](#2-var-tokenet-kommer-ifrån-och-var-det-sparas) | Fas 6, databasschema | 🔶 Öppen |
| 3 | [Token i telefonen](#3-token-i-telefonen) | Fas 6 | 🔶 Öppen |
| 4 | [Vilka protokollversioner appen stöder](#4-vilka-protokollversioner-appen-stöder) | Fas 6 | 🔶 Öppen |
| 5 | [`autoConnect` och en anslutning åt gången](#5-autoconnect-och-en-anslutning-åt-gången) | Fas 5 (uppföljning), fas 6 | ✅ Besvarad 2026-11-13 |
| 6 | [Ordningen mellan Result och skrivbekräftelsen](#6-ordningen-mellan-result-och-skrivbekräftelsen) | Fas 6 | 🔶 Öppen, kräver test på enhet |
| 7 | [Parkopplingsknappen och glömda parkopplingar i appen](#7-parkopplingsknappen-och-glömda-parkopplingar-i-appen) | Fas 6 | 🔶 Öppen |

---

## 1. Parkoppling och identitet

**Bakgrund:** Enligt beslut 1.3 i roadmappen (2026-11-01) ska noderna bondas så att BLE-adressen blir en stabil identitet. Firmware `0.4.0` (protokoll 3, mergat till Arduino-repots `main`) **kräver** nu parkoppling. Command kräver krypterad länk, och nya parkopplingar godtas bara under ett fönster på 30 s som öppnas med en knapp på noden. Tiden för AUTH står still medan fönstret är öppet, så dialogen hinner inte äta upp de 10 sekunderna. Namnsuffixet `TinkerTapNode-XXXX` bygger på nodens adress.

**Kvar att reda ut:**
- Fungerar hela parkopplingsflödet på kortet? Två fel i ArduinoBLE som fick parkopplingen att misslyckas har hittats och rättats på kortet (se `docs/ble-protocol.md`), men repot säger inte uttryckligen att flödet nu är verifierat. Testsketchen `tests/PairingTest` har en testgång för nRF Connect.
- Är nodens egen adress stabil, alltså fast och publik, efter omstart och omflashning? Adressen är fortfarande appens identitet för noden.
- Fungerar flödet med *Insufficient Encryption* och Androids automatiska parkoppling och omsändning mot ArduinoBLE? Eller ska appen alltid anropa `createBond()` uttryckligen innan första skrivningen?

**Svar:** –

## 2. Var tokenet kommer ifrån och var det sparas

**Bakgrund:** Varje nod kan ha sitt eget token för AUTH. Fel token gör att noden kopplar ner direkt, så appen får inte försöka igen automatiskt.

**Frågor:**
- Ska användaren ange tokenet när noden sparas, eller först när det behövs?
- Ska firmwarens standardtoken `tinker` användas som förval?
- Ska tokenet sparas per nod? Då behövs en ny kolumn i `devices`, alltså databasversion 2 med migration och migreringstest.
- Hur ska appen bete sig efter `AUTH_FAILED`? Förslag: visa ett fel och be om ett nytt token.

**Svar:** –

## 3. Token i telefonen

**Bakgrund:** Med protokoll 3 skickas tokenet över en krypterad länk och kan inte avlyssnas. Den svagaste länken blir då hur tokenet lagras på telefonen. Samtidigt kräver noden redan parkoppling, som bara kan göras fysiskt vid noden, så tokenet är ett andra skydd ovanpå den.

**Fråga:** Ska tokenet lagras krypterat, till exempel med Android Keystore, eller räcker det i Room eller DataStore? Ett nytt bibliotek, som `androidx.security:security-crypto`, kräver godkännande.

**Svar:** –

## 4. Vilka protokollversioner appen stöder

**Bakgrund:** Info innehåller `proto`. Versionerna skiljer sig så här:

| Version | Skillnad mot versionen före |
|---|---|
| 1 | – |
| 2 | Tid för AUTH, nedkoppling vid fel token och att `ALL_OFF` kräver AUTH |
| 3 | Parkoppling och krypterad länk för Command |

Firmware `0.1.0` saknar både `proto` och Result. Protokoll 3 är mergat till Arduino-repots `main`. Noder som inte har flashats om sedan dess kör fortfarande protokoll 2 eller äldre.

**Fråga:** Ska appen bara stödja `proto` = `3` och visa ett tydligt fel för andra versioner, så att äldre noder måste uppdateras? Det är förslaget. Alternativet är att också stödja `2` tills alla noder är uppdaterade. Skillnaden för appen är i princip om parkoppling krävs, och det står i fältet `pair`.

**Svar:** –

## 5. `autoConnect` och en anslutning åt gången

**Bakgrund:** Firmwaren rekommenderar `connectGatt(autoConnect = true)` för kända noder, så att Android återansluter själv. Fas 5-koden (`AndroidGattConnector`) använder `autoConnect = false`. Noden tar bara en anslutning åt gången och syns inte för andra telefoner medan appen är ansluten.

**Frågor:**
- Ska fas 5-koden byta till `autoConnect = true` för sparade noder?
- Ska appen koppla från när den inte syns, så att andra telefoner kan ansluta? Det påverkar planen för anslutningar i bakgrunden i fas 7.
- Ska appen begära `requestMtu(247)`, som firmwaren rekommenderar? Det behövs inte för protokollet men gör att Info läses snabbare.

**Svar (2026-11-13):** Appen använder `autoConnect = true` **i förgrunden**.
- Sparade noder ansluts med `autoConnect = true` medan appen syns, så att Android ansluter när noden kommer inom räckhåll. En ny nod som inte har sparats ansluts fortfarande med `autoConnect = false`.
- När appen går till bakgrunden kopplar den ner alla noder. Då annonserar noderna igen och andra telefoner kan ansluta. När appen syns igen ansluter den på nytt.
- Anslutningar i bakgrunden, med en foreground service, görs inte nu. De ligger kvar i fas 7.
- Appen begär `requestMtu(247)` efter anslutningen och före `discoverServices()`, som firmwaren rekommenderar. Får appen ett lägre värde fungerar protokollet ändå, så ett avslag eller ett lägre MTU-värde är inget fel.

**Följder för koden:**
- `CONNECT_TIMEOUT` på 15 s gäller inte för `autoConnect = true`. Anslutningen behöver ett tillstånd för att vänta på noden, till exempel "väntar på noden", som inte är ett fel.
- Signalen om förgrund och bakgrund kommer från gränssnittet, till exempel med `LifecycleStartEffect` från `lifecycle-runtime-compose`, som redan finns i `:app`. Den skickas som en händelse till en ViewModel, som anropar ett use case i `:domain`. Inget nytt beroende behövs.

## 6. Ordningen mellan Result och skrivbekräftelsen

**Bakgrund:** Protokollet saknar sekvensnummer. Firmwaren förlitar sig på att ArduinoBLE skickar Result-notifieringen innan skrivningen bekräftas, så att `onCharacteristicChanged` kommer före `onCharacteristicWrite` i appen.

**Fråga:** Stämmer det på en riktig enhet? Det kan provas med nRF Connect eller med appen när fas 6 är på plats. Om det inte stämmer behöver appen vänta på Result separat, eller så behöver firmwaren ett sekvensnummer i en ny protokollversion.

**Svar:** –

## 7. Parkopplingsknappen och glömda parkopplingar i appen

**Bakgrund:** Med protokoll 3 måste användaren trycka på nodens parkopplingsknapp innan telefonen kan parkopplas, och fönstret är öppet i 30 s. Noden sparar högst 4 telefoner och ersätter den äldsta utan att säga något. Alla parkopplingar kan också raderas med knappen vid uppstart. Android tror då fortfarande att telefonen är parkopplad, men krypteringen misslyckas.

**Frågor:**
- När ska appen be användaren trycka på knappen? När noden sparas första gången, eller först när den första skrivningen kräver det?
- Räcker en text, eller behövs en bild av knappen och matrisens symboler?
- Hur ska appen upptäcka att noden har glömt telefonen? Till exempel genom att krypteringen eller skrivningen misslyckas fast `getBondState()` är `BOND_BONDED`. Och vad ska den visa? Appen kan inte ta bort en bond själv med publika API:er, så användaren måste göra det i Bluetooth-inställningarna.
- Räcker 4 telefoner per nod för hushållet?

**Svar:** –
