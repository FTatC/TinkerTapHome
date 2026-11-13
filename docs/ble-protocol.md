# BLE-protokoll: TinkerTapNode (protokollversion 3)

Det här dokumentet beskriver protokollet som TinkerTapHome använder för att prata med enheter som kör firmwaren **TinkerTapNode**. Det är underlaget för fas 6 i `docs/roadmap.md`.

**Källa:** Arduino-repot `TinkerTapNode`, branchen `feature/ble-pairing` vid commit `f6a1aaf`: filerna `config.h`, `protocol.h`, `bonds.cpp`, `TinkerTapNode.ino` och avsnittet om BLE-arkitektur i repots CLAUDE.md. Det gäller firmwareversion `0.4.0` och **protokollversion 3**.

> **Status:** Protokoll 3 är ännu inte mergat till `main` i Arduino-repot. Där gäller fortfarande protokoll 2 (fw `0.3.1`, `5abdec2`). Det har inte heller bekräftats att parkopplingen fungerar på kortet. Testsketchen `tests/PairingTest` finns för det. Dokumentet bör stämmas av igen när branchen är mergad.

Där firmwarens CLAUDE.md och dess kod skiljer sig gäller **koden**, eftersom det är den som körs på enheterna. Detaljer som bara framgår av koden är markerade med *(kod)*.

> Protokollet får inte ändras på appsidan (se CLAUDE.md). Ändringar görs först i firmwaren, som då höjer `PROTOCOL_VERSION` om ändringen är inkompatibel, och förs sedan in här.

## Översikt

- Noden är BLE-**peripheral** (GATT-server). Appen är **central**.
- Noden tar emot **en anslutning åt gången**.
- Telefonen måste vara **parkopplad** med noden för att få skriva kommandon. Nya parkopplingar godtas bara när någon har tryckt på nodens parkopplingsknapp (se [Parkoppling](#parkoppling)).
- En anpassad 128-bitars service med fyra karakteristiker:

  | Karakteristik | Används till |
  |---|---|
  | **Command** | Appen skriver kommandon |
  | **Result** | Noden svarar på varje kommando |
  | **State** | Utgångarnas aktuella värden |
  | **Info** | Firmware, protokollversion, namn och enhetsregister |

- Alla fält i Command, State och Result är **en byte** (`uint8`, osignerad), så protokollet har ingen byteordning att ta hänsyn till. Undantaget är tokenet i `AUTH`, som är en sträng av ASCII-tecken.
- Info är en kort JSON-sträng i UTF-8.
- Varje nod har ett **enhetsregister**: en fast lista med utgångar (reläer, lysdioder, PWM, servon). Varje utgång har ett `deviceId` som är dess index i listan, med början på 0. Listan har 1–20 utgångar.

> **Ordval:** I firmwaren betyder *enhet* (device) en utgång på noden, alltså en rad i enhetsregistret. I appen betyder `Device` en hel BLE-nod. I det här dokumentet används **nod** för BLE-enheten och **utgång** för en rad i registret.

## Annonsering och anslutning

| Fält | Värde |
|---|---|
| Annonserad service-UUID | `7d3c0000-5b2e-4a8f-9c61-2e4b8a1f7c90` |
| Lokalt namn | `TinkerTapNode-XXXX` (ligger i scan response) |
| GAP-enhetsnamn | Samma som det lokala namnet |

- `XXXX` är de fyra sista hexsiffrorna i nodens BLE-adress, med versaler. Adressen `AA:BB:CC:DD:A1:B2` ger till exempel namnet `TinkerTapNode-A1B2`.
- Om suffixet är avstängt i firmwaren (`NAME_SUFFIX_FROM_MAC = false`) heter noden bara `TinkerTapNode`.
- *(kod)* Om adressen inte har det väntade formatet blir suffixet `0000`.
- Samma namn finns också i fältet `name` i Info.
- Appen ska filtrera skanningen på **service-UUID:t**, inte på namnet. Namnet är bara till för att visa och skilja noder åt för användaren. Det är BLE-adressen som identifierar en nod.
- Noden **slutar annonsera** när en central ansluter och börjar igen när den kopplar från. Så länge appen är ansluten syns noden alltså inte för andra telefoner, och de kan inte ansluta.
- Noden kopplar själv ner en central som inte autentiserar sig i tid eller som skickar fel token (se [Autentisering](#autentisering-auth)), så att en främmande anslutning inte kan blockera appen.

## GATT-service och karakteristiker

Bas-UUID: `7d3cXXXX-5b2e-4a8f-9c61-2e4b8a1f7c90`, där `XXXX` är karakteristikens id.

| Namn | UUID | Egenskaper | Längd | Format |
|---|---|---|---|---|
| Service | `7d3c0000-5b2e-4a8f-9c61-2e4b8a1f7c90` | – | – | – |
| Command | `7d3c0001-5b2e-4a8f-9c61-2e4b8a1f7c90` | Write, Write Without Response, **kräver krypterad länk** | 1–20 byte | Binärt, se [Command](#command-0001) |
| State | `7d3c0002-5b2e-4a8f-9c61-2e4b8a1f7c90` | Read, Notify | Fast: en byte per utgång | Binärt, se [State](#state-0002) |
| Info | `7d3c0003-5b2e-4a8f-9c61-2e4b8a1f7c90` | Read | Högst 512 byte | JSON i UTF-8, se [Info](#info-0003) |
| Result | `7d3c0004-5b2e-4a8f-9c61-2e4b8a1f7c90` | Read, Notify | Fast: 3 byte | Binärt, se [Result](#result-0004) |

Bara **Command** kräver krypterad länk, och den får bara en parkopplad telefon. Info, State och Result kan läsas och ge notifieringar utan kryptering. Efter parkopplingen är ändå hela länken krypterad.

Ovanpå parkopplingen finns ett token (se [Autentisering](#autentisering-auth)).

## Parkoppling

Gäller när `pair` = `1` i Info. Det är standard i firmwaren (`PAIRING_REQUIRED = true`).

### Parkopplingsfönster

- Nya parkopplingar godtas bara medan **parkopplingsfönstret** är öppet. Fönstret öppnas med en tryckknapp på noden (D2 mot GND).
- Fönstret är öppet i **30 sekunder** och tar emot **en** parkoppling. Det stängs när en parkoppling har sparats, när tiden har gått ut eller när någon trycker på knappen igen.
- Parkopplingen är av typen **Just Works**, så ingen kod behöver jämföras. Skyddet är att fönstret bara kan öppnas av någon som står vid noden.
- LED-matrisen på noden visar:

  | Symbol | Betydelse |
  |---|---|
  | Blinkande Bluetooth-symbol | Fönstret är öppet |
  | Fast Bluetooth-symbol | En app är ansluten och har skickat rätt token |
  | Bock | En ny parkoppling har sparats |
  | Kryss | Parkopplingen misslyckades, eller alla parkopplingar har raderats |

- *(kod)* Om en parkopplingsförfrågan kom men ingen parkoppling sparades innan tiden gick ut, visas kryss.

### Lagrade parkopplingar

- Noden sparar nycklarna (LTK och IRK) för **högst 4 telefoner** i sitt flashminne, och de finns kvar efter omstart. När en femte telefon parkopplas ersätts den **äldsta**, utan att den telefonen får veta något.
- *(kod)* Om samma telefon parkopplas igen ersätts dess gamla nycklar.
- **Alla parkopplingar raderas** om man håller knappen intryckt i 3 sekunder vid uppstart. Det visas som ett blinkande kryss och sedan ett fast kryss.
- När noden har glömt en telefon, för att den har raderats eller ersatts, tror Android fortfarande att den är parkopplad. Krypteringen misslyckas då. Användaren måste ta bort noden i Androids Bluetooth-inställningar och parkoppla igen.

### Skrivningar före kryptering

- En skrivning till Command över en okrypterad länk besvaras med ATT-felet *Insufficient Encryption*.
  - Är telefonen redan parkopplad, krypterar Android länken och skickar skrivningen igen.
  - Är den inte parkopplad, startar Android en parkoppling. Den lyckas bara om fönstret är öppet.
- Enligt firmwarens CLAUDE.md håller ArduinoBLE kvar skrivningen och utför den när länken har krypterats. Hur det samspelar med Androids omsändning bör verifieras på en riktig enhet.
- Skrivningar med Write Without Response får inget felsvar och kan inte starta en parkoppling. Utan kryptering försvinner de tyst. Det är ytterligare ett skäl att alltid använda Write With Response.

### Tid för AUTH under parkoppling

*(kod)* Tiden för AUTH (`ato`) står still medan parkopplingsfönstret är öppet. Den räknas om från början när fönstret stängs. Androids parkopplingsdialog kan därmed vänta på användaren utan att noden kopplar ner.

## Command (`0001`)

```
byte:   0          1           2
      [opcode]  [deviceId]  [value]

AUTH: [0x20]    [token: 1–19 byte ASCII]
```

- `opcode` krävs alltid.
- `deviceId` krävs för kommandon mot en utgång. För övriga kommandon ignoreras resten av byten, utom för `AUTH`.
- `value` krävs för `SET`, `PWM` och `SERVO`. Byte efter `value` ignoreras.
- Ett kommando får vara högst 20 byte, vilket ryms i ett paket vid standard-MTU.

### Opcodes

| Opcode | Namn | Data | Kräver AUTH | Utgångstyp | Effekt |
|---|---|---|---|---|---|
| `0x01` | `SET` | id + 1 byte | ja | Digital | `0` = av, alla andra värden = på (sparas som `1`) *(kod)* |
| `0x02` | `TOGGLE` | id | ja | Digital | Växlar mellan av och på |
| `0x03` | `PWM` | id + 1 byte, `0`–`255` | ja | PWM | Sätter PWM-värdet |
| `0x04` | `SERVO` | id + 1 byte, `0`–`180` | ja | Servo | Sätter servots vinkel i grader. Värden över 180 sätts till 180. |
| `0x10` | `GET_STATE` | – | nej | – | Skickar en notifiering av State utan att ändra något |
| `0x11` | `PING` | – | ja | – | Heartbeat. Nollställer timern för timeout. |
| `0x20` | `AUTH` | token | – | – | Låser upp övriga kommandon för anslutningen. **Fel token kopplar ner anslutningen.** |
| `0xFF` | `ALL_OFF` | – | ja | – | Sätter alla utgångar till sitt säkra värde (`safeValue`) |

Bara `GET_STATE` och `AUTH` får skickas utan autentisering.

### Hur kommandon hanteras *(kod)*

Om nedkoppling redan är beslutad, efter fel token eller när tiden för AUTH har gått ut, **ignoreras alla kommandon**. Noden skickar då inget Result, och anslutningen bryts efter cirka 200 ms.

I övriga fall kontrolleras kommandot i ordningen nedan. Det första som slår till avgör vilken status som skickas.

| Steg | Villkor | Status | `deviceId` i Result |
|---|---|---|---|
| 1 | Kommandot är tomt | `BAD_LENGTH` | `0xFF` |
| 2 | Okänd opcode | `UNKNOWN_OPCODE` | `0xFF` |
| 3 | `GET_STATE` | `OK` | `0xFF` |
| 4 | `AUTH`: rätt token | `OK` | `0xFF` |
| 4 | `AUTH`: fel token | `AUTH_FAILED`, sedan nedkoppling | `0xFF` |
| 5 | Inte autentiserad (gäller `SET`, `TOGGLE`, `PWM`, `SERVO`, `PING` och `ALL_OFF`) | `NOT_AUTHORIZED` | `0xFF` |
| 6 | `PING` | `OK` | `0xFF` |
| 7 | `ALL_OFF` | `OK` | `0xFF` |
| 8 | `deviceId` saknas | `INVALID_DEVICE` | `0xFF` |
| 9 | `deviceId` ≥ antalet utgångar | `INVALID_DEVICE` | **Det id som skickades** |
| 10 | Opcode passar inte utgångens typ | `WRONG_TYPE` | id |
| 11 | `value` saknas | `MISSING_VALUE` | id |
| 12 | Allt stämmer | `OK`, värdet sätts | id |

Ett avvisat kommando påverkar aldrig utgångarna.

### Ordningen på notifieringar

**Varje** skrivning till Command besvaras med en notifiering på Result, utom när nedkoppling redan är beslutad.

| Kommando | Notifieringar |
|---|---|
| `SET`, `TOGGLE`, `PWM` och `SERVO` som lyckas, samt `ALL_OFF` och `GET_STATE` | Först **State**, sedan **Result** |
| Övriga kommandon och alla avvisade kommandon | Bara **Result** |

Ett godkänt kommando som inte ändrar något, till exempel `SET 1` på en utgång som redan är på, ger ändå en notifiering av State.

### Skrivsätt: Write With Response, ett kommando i taget

Firmwaren rekommenderar att kommandon skickas med **Write With Response**, ett i taget. ArduinoBLE kör kommandohanteraren och skickar notifieringarna innan skrivningen bekräftas. När `onCharacteristicWrite` kommer i appen har Result därför redan kommit via `onCharacteristicChanged`. Därför behövs inget sekvensnummer i protokollet.

**Följder för appen:**
- Ett kommando är klart när skrivningen har bekräftats. Det senaste Result-värdet för samma opcode som kommit **efter** att skrivningen startade är svaret på kommandot.
- Om skrivningen bekräftas utan att något Result har kommit har noden ignorerat kommandot eftersom nedkoppling pågår. Det ska behandlas som ett fel, och anslutningen bryts strax.
- Write Without Response fungerar också, men då kan appen inte para ihop två likadana kommandon med sina svar, och utan kryptering försvinner skrivningen tyst. Appen ska inte använda det.
- Att Result kommer före bekräftelsen bygger på hur ArduinoBLE och Androids BLE-stack beter sig. Det bör verifieras på en riktig enhet (se fråga 6 i [`docs/open-questions.md`](open-questions.md)).

### Exempel

Förutsätter `auth` = `1` och standardtokenet `tinker`.

| Avsikt | Byte (hex) | Result (hex) |
|---|---|---|
| Autentisera | `20 74 69 6E 6B 65 72` | `20 FF 00` |
| Tänd utgång 0 (digital) | `01 00 01` | `01 00 00` |
| Släck utgång 0 | `01 00 00` | `01 00 00` |
| Växla utgång 0 | `02 00` | `02 00 00` |
| Sätt utgång 1 (PWM) till 50 % | `03 01 80` | `03 01 00` |
| Sätt utgång 2 (servo) till 90° | `04 02 5A` | `04 02 00` |
| Heartbeat | `11` | `11 FF 00` |
| Be om aktuellt tillstånd | `10` | `10 FF 00` |
| Alla utgångar till säkert läge | `FF` | `FF FF 00` |
| Tänd utgång 0 utan AUTH | `01 00 01` | `01 FF 05` |
| `ALL_OFF` utan AUTH | `FF` | `FF FF 05` |
| Fel token | `20 78` | `20 FF 06`, sedan nedkoppling |
| `PWM` mot en digital utgång 0 | `03 00 80` | `03 00 03` |
| `deviceId` 9 finns inte | `01 09 01` | `01 09 02` |
| `SET` utan värde | `01 00` | `01 00 04` |
| Okänd opcode | `42` | `42 FF 01` |

## Result (`0004`)

```
byte:   0          1           2
      [opcode]  [deviceId]  [status]
```

- `opcode` är kommandots opcode, eller `0x00` om kommandot var tomt.
- `deviceId` är den utgång som berördes, eller `0xFF` (`NO_DEVICE`). Se tabellen [Hur kommandon hanteras](#hur-kommandon-hanteras-kod) för vilket värde som gäller när.

| Status | Namn | Betydelse |
|---|---|---|
| `0x00` | `OK` | Kommandot utfördes |
| `0x01` | `UNKNOWN_OPCODE` | Okänd opcode |
| `0x02` | `INVALID_DEVICE` | `deviceId` saknas eller finns inte |
| `0x03` | `WRONG_TYPE` | Opcode passar inte utgångens typ |
| `0x04` | `MISSING_VALUE` | Värdebyten saknas |
| `0x05` | `NOT_AUTHORIZED` | `AUTH` krävs innan kommandot godtas |
| `0x06` | `AUTH_FAILED` | Fel token. Noden kopplar ner anslutningen. |
| `0x07` | `BAD_LENGTH` | Tomt kommando |

**Följder för appen** *(kod)*:
- Värdet i Result nollställs inte mellan anslutningar. Om Result läses direkt efter anslutning visas svaret på **förra sessionens** sista kommando. Vid uppstart är värdet `00 FF 00`. Appen ska bara lita på notifieringar som kommer efter att den själv har skickat ett kommando.
- En statuskod som appen inte känner till ska behandlas som ett okänt fel, inte som `OK`.

## State (`0002`)

State är en byte per utgång, i `deviceId`-ordning, utan huvud. Längden är fast och lika med antalet utgångar, högst 20, så den ryms i en notifiering vid standard-MTU.

```
byte:   0          1          …   N-1
      [value 0]  [value 1]   …  [value N-1]
```

| Utgångstyp | Värde |
|---|---|
| Digital | `0` = av, `1` = på |
| PWM | `0`–`255` |
| Servo | `0`–`180` (grader) |

Noden skickar en notifiering av State:
- efter `SET`, `TOGGLE`, `PWM` och `SERVO` som lyckas
- efter `ALL_OFF` och `GET_STATE`
- vid timeout för heartbeat (se [Heartbeat](#heartbeat)). Då kommer **ingen** Result-notifiering.

State kan läsas utan autentisering, både direkt och via `GET_STATE`. Eftersom utgångarna behåller sina värden mellan anslutningar visar en läsning efter anslutning det verkliga läget.

**Exempel:** En nod med `led` (digital), `fan` (PWM) och `servo` som har värdena på, 128 och 90° har State `01 80 5A`.

## Info (`0003`)

Info är en JSON-sträng i UTF-8 som beskriver noden. Den skrivs vid uppstart och ändras inte medan noden kör. Info kan läsas utan autentisering.

```json
{"fw":"0.4.0","proto":3,"name":"TinkerTapNode-A1B2","pair":1,"auth":1,"ato":10000,"hb":0,"dev":[{"id":0,"n":"led","t":"D"}]}
```

| Fält | Typ | Betydelse |
|---|---|---|
| `fw` | sträng | Firmwareversion, till exempel `"0.4.0"`. Bara för visning. |
| `proto` | heltal | Protokollversion. Det här dokumentet beskriver `3`. |
| `name` | sträng | Nodens namn, samma som i annonseringen |
| `pair` | heltal | `1` = parkoppling (krypterad länk) krävs för Command, `0` = krävs inte |
| `auth` | heltal | `1` = `AUTH` krävs, `0` = krävs inte |
| `ato` | heltal | Tid för AUTH i millisekunder, räknat från anslutningen. `0` när AUTH inte krävs. Standard är `10000`. |
| `hb` | heltal | Timeout för heartbeat i millisekunder. `0` = avstängd. |
| `dev` | lista | En post per utgång, i `deviceId`-ordning |
| `dev[].id` | heltal | `deviceId`, samma som index i State |
| `dev[].n` | sträng | Utgångens namn, till exempel `"led"`. Kort och på engelska. |
| `dev[].t` | sträng | Typ: `"D"` = Digital, `"P"` = PWM, `"S"` = Servo |

- **Läs Info först** efter anslutning och kontrollera `proto`. Är `proto` okänt ska appen koppla från och inte styra noden. Den ska visa att noden kräver en annan appversion.
- **Okända fält ska ignoreras.** Firmwaren lägger till nya fält utan att höja `proto`. Ändrad betydelse eller borttagna fält kräver ett nytt `proto`.
- **Hårdkoda aldrig `deviceId`.** Id:t är ett index i firmwarens tabell och kan ändras mellan firmwareversioner. Appen bygger listan över utgångar från `dev` vid varje anslutning.
- Strängen är högst **512 byte**, vilket är ATT-gränsen. Firmwaren kontrollerar redan vid kompilering att JSON:en ryms, så den kortas inte av. Appen ska ändå behandla ogiltig JSON som ett fel.
- Info är längre än 20 byte och läses med en lång läsning (Read Blob). Android sköter det automatiskt i `readCharacteristic`.
- Antalet poster i `dev` ska vara lika med längden på State. Stämmer de inte överens ska appen behandla noden som felaktig och inte gissa vilket värde som hör till vilken utgång.
- Ett okänt värde på `t` gör att den utgången inte kan styras. Appen får inte gissa typen.

## Autentisering (AUTH)

- När `auth` = `1` avvisas alla kommandon som kan ändra något (`SET`, `TOGGLE`, `PWM`, `SERVO`, `ALL_OFF` och `PING`) med `NOT_AUTHORIZED` tills appen har skickat `AUTH` med rätt token. Bara `GET_STATE` och `AUTH` är öppna. Info och State kan alltid läsas.
- Tokenet är 1–19 byte ASCII och måste matcha exakt, både innehåll och längd. Firmwarens standardtoken är `tinker` och ska bytas innan noden används på riktigt.
- Autentiseringen gäller **per anslutning** och nollställs när anslutningen bryts. Appen måste skicka `AUTH` efter varje anslutning.
- **Tid för AUTH:** En central som inte har autentiserat sig inom `ato` millisekunder från anslutningen (standard 10 s) kopplas ner. Det inkluderar anslutning, MTU, service discovery, läsning av Info, CCCD för notifieringar, kryptering och själva `AUTH`. Tiden står still medan parkopplingsfönstret är öppet.
- **Fel token:** Noden skickar `AUTH_FAILED` i Result och kopplar ner cirka 200 ms senare. Under tiden ignoreras alla kommandon, så inga fler token kan provas. Appen ska **inte** automatiskt försöka igen med samma token.
- *(kod)* Det gäller även när anslutningen redan är autentiserad: ett nytt `AUTH` med fel token kopplar ner.
- *(kod)* Det gäller även när `auth` = `0`: `AUTH` med fel token kopplar ner. Appen ska därför bara skicka `AUTH` när `auth` = `1`.
- **Kryptering:** När `pair` = `1` skickas `AUTH` alltid över en krypterad länk, eftersom det är en skrivning till Command. Tokenet kan då inte avlyssnas. När `pair` = `0` är länken okrypterad, och tokenet skyddar bara mot misstag och obehöriga appar.

## Heartbeat

- När `hb` > 0 och en central är ansluten men inte har skickat något godkänt kommando på `hb` millisekunder, sätts alla utgångar till säkert läge. Det händer en gång per tyst period och följs av en notifiering av State.
- Det skyddar mot att appen hänger sig medan länken ligger kvar. Det påverkar aldrig utgångarna när ingen central är ansluten.
- *(kod)* Timern startar vid anslutning och nollställs av **varje** kommando som får status `OK`, inte bara `PING`.
- Appen ska skicka `PING` med ett intervall som är klart kortare än `hb`, till exempel `hb / 3`.
- Heartbeat är avstängd i standardkonfigurationen (`hb` = `0`).

## Säkert läge och frånkoppling

Varje utgång har ett säkert värde (`safeValue`) i firmwaren. Det är oftast `0`, men ett servo kan till exempel ha `90`.

| Händelse | Utgångarna |
|---|---|
| Uppstart | Sätts till sina säkra värden |
| `ALL_OFF` (kräver AUTH) | Sätts till sina säkra värden |
| Timeout för heartbeat medan en central är ansluten | Sätts till sina säkra värden |
| **Frånkoppling, utom räckhåll, timeout för AUTH eller fel token** | **Behåller sina värden.** Ingenting ändras. |

**Följder för appen:**
- Det går att tända något, koppla från och sedan ansluta igen och läsa det verkliga läget i State.
- Att koppla från är inget säkert läge. Vill användaren släcka allt måste appen skicka `ALL_OFF` uttryckligen.
- När noden inte är ansluten visar appen senast kända värden, men de kan ha ändrats av en annan central eller av en omstart.

## Rekommenderat anslutningsflöde

Flödet är hämtat från firmwarens CLAUDE.md och kompletterat med följderna ovan. **Steg 2–6 måste vara klara inom `ato` millisekunder** (standard 10 s), annars kopplar noden ner. Tiden står still medan parkopplingsfönstret är öppet.

1. Skanna med filter på service-UUID:t `7d3c0000-…`. Noder skiljs åt med namnsuffixet eller BLE-adressen.
2. Anslut. För noder som appen redan känner till rekommenderar firmwaren `autoConnect = true`, så att Android återansluter själv när noden kommer inom räckhåll igen. Kör sedan `requestMtu(247)` och `discoverServices()`.
3. Läs **Info**. Kontrollera `proto` och bygg listan över utgångar.
4. Slå på notifieringar för **Result** och **State** (CCCD), och läs State för att få utgångarnas aktuella värden.
5. Om `pair` = `1` och telefonen inte är parkopplad med noden (`device.getBondState()`):
   1. Be användaren trycka på nodens parkopplingsknapp.
   2. Anropa `device.createBond()`.
   3. Vänta på `ACTION_BOND_STATE_CHANGED` med `BOND_BONDED`.

   Är telefonen redan parkopplad krypterar Android länken automatiskt vid första skrivningen till Command.
6. Om `auth` = `1`: skicka `AUTH` med Write With Response och vänta på Result med `20 FF 00`.
   - Vid `AUTH_FAILED` kopplar noden ner. Be då användaren om ett nytt token i stället för att försöka igen.
   - Om krypteringen misslyckas trots att telefonen tror att den är parkopplad har noden glömt telefonen. Be användaren ta bort noden i Androids Bluetooth-inställningar och parkoppla igen.
7. Om `hb` > 0: skicka `PING` regelbundet, till exempel var `hb / 3` millisekund, så länge anslutningen är uppe.
8. Skicka kommandon med Write With Response, ett i taget, och läs svaret i Result. Alla GATT-operationer går genom kön i `:data`.

### Jämfört med appen i dag (fas 5)

| Rekommendation | `:data` i dag |
|---|---|
| `autoConnect = true` för kända noder | `connectGatt(…, autoConnect = false, …)` i `AndroidGattConnector` |
| `requestMtu(247)` | MTU begärs inte. Det fungerar med standard-MTU, men Info läses då i fler paket. |
| Write With Response | ✅ `WRITE_TYPE_DEFAULT` i `AndroidGattHandle` |
| `createBond()` och väntan på `BOND_BONDED` | ✅ `GattSession` (timeout 60 s) och `AndroidGattConnector`, som tar emot `ACTION_BOND_STATE_CHANGED` |
| Be användaren trycka på parkopplingsknappen | Saknas. Hör till fas 6. |
| Upptäcka att noden har glömt telefonen | Saknas. Hör till fas 6. |
| En GATT-operation i taget | ✅ `GattOperationQueue` |

## Öppna frågor 🔶

Frågor som påverkar hur appen använder protokollet samlas i [`docs/open-questions.md`](open-questions.md). Det gäller parkoppling, token, protokollversioner, `autoConnect`, ordningen mellan Result och skrivbekräftelsen och hur appen ska hantera parkopplingsknappen och glömda parkopplingar.
