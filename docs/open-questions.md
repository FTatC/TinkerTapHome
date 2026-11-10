# Öppna frågor

Beslut som agenten inte får ta själv (se CLAUDE.md). Varje fråga har ett svarsfält. När en fråga är besvarad skrivs svaret och datumet in, och beslutet förs in i det dokument som frågan gäller, till exempel `docs/ble-protocol.md` eller `docs/roadmap.md`.

| # | Fråga | Gäller | Status |
|---|---|---|---|
| 1 | [Parkoppling och identitet](#1-parkoppling-och-identitet) | Fas 6, beslut 1.3 | 🔶 Öppen |
| 2 | [Var tokenet kommer ifrån och var det sparas](#2-var-tokenet-kommer-ifrån-och-var-det-sparas) | Fas 6, databasschema | 🔶 Öppen |
| 3 | [Token i telefonen](#3-token-i-telefonen) | Fas 6 | 🔶 Öppen |
| 4 | [Vilka protokollversioner appen stöder](#4-vilka-protokollversioner-appen-stöder) | Fas 6 | 🔶 Öppen |
| 5 | [`autoConnect` och en anslutning åt gången](#5-autoconnect-och-en-anslutning-åt-gången) | Fas 5 (uppföljning), fas 7 | 🔶 Öppen |
| 6 | [Ordningen mellan Result och skrivbekräftelsen](#6-ordningen-mellan-result-och-skrivbekräftelsen) | Fas 6 | 🔶 Öppen, kräver test på enhet |

---

## 1. Parkoppling och identitet

**Bakgrund:** Enligt beslut 1.3 i roadmappen (2026-11-01) ska noderna bondas så att BLE-adressen blir en stabil identitet. Firmwaren TinkerTapNode (protokoll 2) kräver ingen parkoppling, och bondning är inte verifierad mot UNO R4 WiFi (ArduinoBLE). Namnsuffixet `TinkerTapNode-XXXX` bygger på samma adress.

**Frågor:**
- Fungerar `createBond()` mot noden?
- Om inte: är adressen ändå stabil, till exempel för att den är en fast publik adress? Räcker det då att använda adressen som identitet utan bondning?
- Om parkopplingen visar en dialog kan den ta upp en stor del av de 10 sekunder (`ato`) som noden ger för AUTH. Ska bondning i så fall göras i en separat anslutning innan den vanliga?

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

**Bakgrund:** Tokenet skickas i klartext över BLE, så krypterad lagring skyddar bara mot den som kommer åt telefonens filer.

**Fråga:** Ska tokenet lagras krypterat, till exempel med Android Keystore, eller räcker det i Room eller DataStore? Ett nytt bibliotek, som `androidx.security:security-crypto`, kräver godkännande.

**Svar:** –

## 4. Vilka protokollversioner appen stöder

**Bakgrund:** Info innehåller `proto`. Protokoll 2 skiljer sig från 1 på flera punkter: tid för AUTH, nedkoppling vid fel token och att `ALL_OFF` kräver AUTH. Firmware `0.1.0` saknar både `proto` och Result.

**Fråga:** Ska appen bara stödja `proto` = `2` och visa ett tydligt fel för andra versioner, så att äldre noder måste uppdateras? Det är förslaget.

**Svar:** –

## 5. `autoConnect` och en anslutning åt gången

**Bakgrund:** Firmwaren rekommenderar `connectGatt(autoConnect = true)` för kända noder, så att Android återansluter själv. Fas 5-koden (`AndroidGattConnector`) använder `autoConnect = false`. Noden tar bara en anslutning åt gången och syns inte för andra telefoner medan appen är ansluten.

**Frågor:**
- Ska fas 5-koden byta till `autoConnect = true` för sparade noder?
- Ska appen koppla från när den inte syns, så att andra telefoner kan ansluta? Det påverkar planen för anslutningar i bakgrunden i fas 7.
- Ska appen begära `requestMtu(247)`, som firmwaren rekommenderar? Det behövs inte för protokollet men gör att Info läses snabbare.

**Svar:** –

## 6. Ordningen mellan Result och skrivbekräftelsen

**Bakgrund:** Protokollet saknar sekvensnummer. Firmwaren förlitar sig på att ArduinoBLE skickar Result-notifieringen innan skrivningen bekräftas, så att `onCharacteristicChanged` kommer före `onCharacteristicWrite` i appen.

**Fråga:** Stämmer det på en riktig enhet? Det kan provas med nRF Connect eller med appen när fas 6 är på plats. Om det inte stämmer behöver appen vänta på Result separat, eller så behöver firmwaren ett sekvensnummer i en ny protokollversion.

**Svar:** –
