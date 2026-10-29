# Marlboro Gold Totem (Fabric 1.21.1)

Der Totem of Undying sieht aus wie eine goldene Zigarettenschachtel (Inventar, Hand, Boden).
Beim Aktivieren poppt die Schachtel auf, der Deckel klappt auf, die Zigaretten fallen heraus,
goldene Funken erscheinen. Die Mod ist nur clientseitig und funktioniert auch auf Servern ohne Mod.

## Jar bauen (eine der drei Moeglichkeiten)

### A) Ohne Installation: GitHub Actions
1. Neues GitHub-Repository erstellen, diesen Ordner hochladen.
2. Reiter "Actions" > "build" > "Run workflow".
3. Nach ca. 3 Minuten unter "Artifacts" die Datei `goldensmokes-totem` herunterladen.
   Darin liegt `goldensmokes-totem-1.0.0.jar` (die Datei OHNE `-sources`).

### B) IntelliJ IDEA (Community reicht, Java 21 noetig)
1. Ordner oeffnen, Gradle-Import abwarten.
2. Rechts "Gradle" > Tasks > build > build.
3. Jar liegt in `build/libs/goldensmokes-totem-1.0.0.jar`.

### C) Kommandozeile
Gradle 8.10+ und Java 21 installieren, dann im Ordner: `gradle build`

## Testen
Fabric Loader 0.16+ und Fabric API fuer 1.21.1 installieren, Jar in den `mods`-Ordner.
Totem in die Hand nehmen und sterben (z. B. `/kill` geht NICHT, besser Fallschaden oder Schaden von einem Mob).

## Anpassen
- Texturen: `python3 tools/generate_textures_hd.py` erzeugt die HD-Texturen (144x192). Eigene PNGs mit gleichen
  Dateinamen ersetzen sie (`assets/minecraft/textures/item/goldensmokes/` und `assets/goldensmokes/textures/gui/`).
- Dauer/Anzahl der Zigaretten: Konstanten oben in `TotemAnimation.java`.
- Haltung in der Hand: `display` in `assets/minecraft/models/item/totem_of_undying.json`.

## Modrinth
Projekttyp "Mod", Loader Fabric, Version 1.21.1, Client-Seite "required", Server "unsupported".
Hinweis: Mit der echten Marke (Name, Wappen) wird die Mod dort sehr wahrscheinlich abgelehnt.

## Marken-Variante
Standard-Texturen tragen den Marlboro-Gold-Schriftzug. Fuer eine Veroeffentlichung (z. B. Modrinth) ohne Markennamen:
`python3 tools/generate_textures_hd.py --neutral` (erzeugt "Golden Smokes"), dazu in
`assets/minecraft/lang/*.json` und `fabric.mod.json` den Namen aendern.

## Texturen aus dem Screenshot
Ohne `--neutral` schneidet `tools/generate_textures_hd.py` die Ansichten (Vorderseite, Rueckseite, Seite, Oben)
direkt aus `tools/reference/vorlage.png` aus. Mit `--neutral` werden stattdessen eigene Texturen gezeichnet.
