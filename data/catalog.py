"""Source data for PrintDesk.

Printers and parts: prices, availability and compatibility as listed on 3djake.at
on 2026-10-05 (prices in EUR incl. VAT). Stock quantities are simulated for the demo.
Troubleshooting knowledge base: written for this project from common Bambu Lab
service procedures (Bambu Lab Wiki style), EN + DE.
"""

SNAPSHOT_DATE = "2026-10-05"
SOURCE = "3djake.at"
FREE_SHIPPING_FROM = 49.90
STD_SHIPPING = 4.99

PRINTERS = [
    # id, name, series, enclosed, nozzle_std, hotend_family, price_eur (3DJake, None = not sold), released
    dict(id="a1mini", name="A1 mini", series="A", enclosed=False, plate="180", hotend="A", price=189.00, combo=299.00, released=2023, ams="AMS lite"),
    dict(id="a1", name="A1", series="A", enclosed=False, plate="256", hotend="A", price=259.00, combo=369.00, released=2023, ams="AMS lite / AMS"),
    dict(id="a2l", name="A2L", series="A", enclosed=False, plate="330", hotend="A", price=379.00, combo=489.00, released=2026, ams="AMS lite / AMS 2 Pro / AMS HT"),
    dict(id="p1p", name="P1P", series="P", enclosed=False, plate="256", hotend="P1", price=None, combo=None, released=2022, ams="AMS"),
    dict(id="p1s", name="P1S", series="P", enclosed=True, plate="256", hotend="P1", price=379.00, combo=529.00, released=2023, ams="AMS / AMS 2 Pro"),
    dict(id="p2s", name="P2S", series="P", enclosed=True, plate="256", hotend="H2", price=519.00, combo=699.00, released=2025, ams="AMS 2 Pro / AMS HT"),
    dict(id="x1c", name="X1 Carbon", series="X", enclosed=True, plate="256", hotend="X1", price=None, combo=None, released=2022, ams="AMS / AMS 2 Pro"),
    dict(id="x2d", name="X2D", series="X", enclosed=True, plate="256", hotend="H2", price=629.00, combo=849.00, released=2026, ams="AMS 2 Pro / AMS HT"),
    dict(id="h2d", name="H2D", series="H", enclosed=True, plate="350", hotend="H2", price=None, combo=None, released=2025, ams="AMS 2 Pro / AMS HT"),
]

A = ["a1mini", "a1", "a2l"]
A1 = ["a1mini", "a1"]
P1 = ["p1p", "p1s"]
X1 = ["x1c"]
H2 = ["p2s", "x2d", "h2d"]
PLATE256 = ["a1", "p1p", "p1s", "p2s", "x1c", "x2d"]
AMS_USERS = ["a1mini", "a1", "a2l", "p1p", "p1s", "p2s", "x1c", "x2d", "h2d"]

# delivery: business days from snapshot date as shown on 3djake.at ("Lieferung bis 8.10." = 3)
# kind: official = Bambu Lab original, alt = third-party alternative sold by 3DJake
PARTS = [
    # --- hotends, official
    dict(id="BL-HE-A04H", name="Hotend - A1/A2 Series, 0.4 mm hardened steel", brand="Bambu Lab", cat="hotend", price=12.99, compat=A, days=3),
    dict(id="BL-HEA-A1", name="Hotend Heating Assembly (A1 / A1 mini)", brand="Bambu Lab", cat="heater", price=24.99, compat=A1, days=3),
    dict(id="BL-HE-P04H", name="Hotend - P1 Series, 0.4 mm hardened steel", brand="Bambu Lab", cat="hotend", price=16.49, compat=P1, days=3),
    dict(id="BL-CHE-P04H", name="Complete Hotend - P1 Series, 0.4 mm hardened steel (heater, thermistor, fan)", brand="Bambu Lab", cat="hotend", price=36.99, compat=P1, days=3),
    dict(id="BL-CHE-X04H", name="Complete Hotend - X1C, 0.4 mm hardened steel (heater, thermistor, fan)", brand="Bambu Lab", cat="hotend", price=36.99, compat=X1, days=3),
    dict(id="BL-HE-H02S", name="Hotend - H2/P2/X2D, 0.2 mm stainless steel", brand="Bambu Lab", cat="hotend", price=19.99, compat=H2, days=3),
    dict(id="BL-HFHE-H2", name="High Flow Hotend - H2/P2 Series", brand="Bambu Lab", cat="hotend", price=52.99, compat=["p2s", "h2d"], days=3),
    dict(id="BL-TCN-H2", name="High Flow Tungsten Carbide Nozzle - H2/P2S/X2D", brand="Bambu Lab", cat="hotend", price=99.99, compat=H2, days=3),
    # --- hotends, alternatives
    dict(id="BIQU-PJ-A1", name="Panda Juicer Hotend SF, hardened steel 0.4 mm (A1 / A1 mini)", brand="BIQU", cat="hotend", price=8.99, compat=A1, days=3),
    dict(id="BIQU-PJ-P2S", name="Panda Juicer Hotend SF, hardened steel 0.4 mm (P2S)", brand="BIQU", cat="hotend", price=9.99, compat=["p2s"], days=19),
    dict(id="PH-CONCH-M4-A1", name="Conch Hotend M4, 0.8 mm (A1 / A1 mini)", brand="Phaetus", cat="hotend", price=14.39, was=17.99, compat=A1, days=3),
    dict(id="PH-CONCH-M6-XP", name="Conch Hotend M6, 0.4 mm (X1 / P1)", brand="Phaetus", cat="hotend", price=21.99, compat=X1 + P1, days=3),
    dict(id="PH-CONCH-M4-P2S", name="Conch Hotend M4, 0.4 mm (P2S)", brand="Phaetus", cat="hotend", price=23.99, compat=["p2s"], days=3),
    dict(id="PH-CONCH-M4-H2D", name="Conch Hotend M4, 0.4 mm (H2D / A1 / A1 mini)", brand="Phaetus", cat="hotend", price=24.99, compat=["h2d"] + A1, days=3),
    dict(id="BROZZL-A1-04H", name="Hot-End A1 Series, 0.4 mm hardened steel", brand="BROZZL", cat="hotend", price=25.99, compat=A1, days=3),
    dict(id="BROZZL-AM-04H", name="All Metal Hot-End, 0.4 mm hardened steel (X1 / P1)", brand="BROZZL", cat="hotend", price=25.99, compat=X1 + P1, days=3),
    dict(id="MS-CHT-HF", name="CHT High Flow Hotend, 0.4 mm (X1 / P1)", brand="Micro-Swiss", cat="hotend", price=49.99, compat=X1 + P1, days=3),
    dict(id="E3D-OBX-A", name="High Flow ObXidian HotEnd, 0.4 mm (A1 / A1 mini / A2L)", brand="E3D", cat="hotend", price=69.99, compat=A, days=3),
    dict(id="E3D-OBX-XP", name="High Flow ObXidian HotEnd, 0.4 mm (X1 / P1)", brand="E3D", cat="hotend", price=89.99, compat=X1 + P1, days=3),
    dict(id="MS-FT-P1", name="FlowTech Hotend, CHT nozzle (P1P / P1S)", brand="Micro-Swiss", cat="hotend", price=85.49, was=94.99, compat=P1, days=3),
    # --- heater / thermistor / fans / electronics
    dict(id="BL-CHT-P1", name="Ceramic Heater & Thermistor (P1P / P1S)", brand="Bambu Lab", cat="heater", price=29.99, compat=P1, days=3),
    dict(id="BL-FAN-HE-P1", name="Cooling Fan for Hotend (P1P / P1S)", brand="Bambu Lab", cat="fan", price=14.99, compat=P1, days=3),
    dict(id="BL-EXH-P2S", name="External Exhaust Fan Kit (P2S)", brand="Bambu Lab", cat="fan", price=24.99, compat=["p2s"], days=3),
    dict(id="BL-THB-P2S", name="Toolhead Board (P2S)", brand="Bambu Lab", cat="electronics", price=39.99, compat=["p2s"], days=3),
    dict(id="BL-BUS4", name="Bambu Bus Cable - 4 pin", brand="Bambu Lab", cat="electronics", price=5.99, compat=P1 + X1, days=3),
    dict(id="BL-CAM-P2S", name="Live View Camera (P2S)", brand="Bambu Lab", cat="electronics", price=35.99, was=39.99, compat=["p2s"], days=3),
    # --- extruder
    dict(id="BL-EXT-P1", name="Extruder Unit (P1P / P1S)", brand="Bambu Lab", cat="extruder", price=44.99, compat=P1, days=3),
    dict(id="BL-EXG-HS", name="Hardened Steel Extruder Gear Assembly", brand="Bambu Lab", cat="extruder", price=21.49, compat=P1 + X1, days=3),
    dict(id="BL-EFS-P1", name="Extruder Filament Sensor (P1P / P1S)", brand="Bambu Lab", cat="sensor", price=14.99, compat=P1, days=3),
    dict(id="BL-FS-A1", name="Filament Sensor (A1 / A1 mini)", brand="Bambu Lab", cat="sensor", price=7.49, compat=A1, days=3),
    # --- cutter / wipers / small parts
    dict(id="BL-CUT3", name="Replacement Filament Cutter (3 pcs)", brand="Bambu Lab", cat="cutter", price=4.49, compat=A1 + P1 + X1, days=3),
    dict(id="BL-CUTL", name="Filament Cutter Lever (X1 / P1)", brand="Bambu Lab", cat="cutter", price=6.74, compat=P1 + X1, days=3),
    dict(id="BL-SOCK-A1", name="Hotend Silicone Sock (3 pcs, A1 / A1 mini)", brand="Bambu Lab", cat="wear", price=4.49, compat=A1, days=3),
    dict(id="BL-WIPE-A1", name="Heatbed Nozzle Wiper (A1)", brand="Bambu Lab", cat="wear", price=4.49, compat=["a1"], days=3),
    dict(id="BL-PWIPE-A1", name="Purge Wiper (A1 / A1 mini)", brand="Bambu Lab", cat="wear", price=7.49, compat=A1, days=3),
    # --- motion
    dict(id="BL-XYB-P1", name="XY Belt (2 pcs)", brand="Bambu Lab", cat="motion", price=15.99, compat=P1 + X1, days=3),
    dict(id="BL-XB-A1", name="X Belt (A1)", brand="Bambu Lab", cat="motion", price=8.99, compat=["a1"], days=3),
    dict(id="BL-YB-A1", name="Y Belt (A1)", brand="Bambu Lab", cat="motion", price=8.99, compat=["a1"], days=3),
    dict(id="BL-IDP-P2S", name="Idler Pulley (P2S)", brand="Bambu Lab", cat="motion", price=11.69, compat=["p2s"], days=3),
    dict(id="BL-AVF8", name="Anti-Vibration Feet (8 pcs)", brand="Bambu Lab", cat="motion", price=14.99, compat=P1 + X1, days=3),
    # --- PTFE / AMS
    dict(id="BL-PTFE-HUB", name="PTFE Tube (AMS Hub)", brand="Bambu Lab", cat="ptfe", price=11.99, compat=A1, days=3),
    dict(id="BL-PTFE-CPL3", name="PTFE Tube Coupler (3 pcs)", brand="Bambu Lab", cat="ptfe", price=7.49, compat=P1 + X1, days=3),
    dict(id="BL-PTFE-CON-A1", name="PTFE Tube Connector (A1)", brand="Bambu Lab", cat="ptfe", price=6.74, was=7.49, compat=["a1"], days=3),
    dict(id="BL-4IN1", name="4-in-1 PTFE Adapter", brand="Bambu Lab", cat="ptfe", price=5.99, compat=["a2l"] + P1 + X1 + H2, days=3),
    dict(id="CAP-AMS-KIT", name="Capricorn Bambu Lab AMS Kit (low-friction PTFE)", brand="Capricorn", cat="ptfe", price=29.99, compat=AMS_USERS, days=3),
    dict(id="BL-AMS-FEED", name="AMS Feeder Unit (AMS 2 Pro)", brand="Bambu Lab", cat="ams", price=49.99, compat=["p1s", "p2s", "x1c", "x2d", "h2d", "a2l"], days=3),
    dict(id="BL-AMSL-FEED", name="AMS lite Feeder Unit", brand="Bambu Lab", cat="ams", price=79.99, compat=A, days=3),
    dict(id="BL-AMS-HUB-INT", name="AMS Internal Hub Unit", brand="Bambu Lab", cat="ams", price=64.99, compat=["p1p", "p1s", "x1c", "a1"], days=3),
    dict(id="BL-AMS-EXW", name="AMS Active Extrusion Wheel Assembly", brand="Bambu Lab", cat="ams", price=14.99, compat=["p1p", "p1s", "x1c", "a1"], days=3),
    dict(id="BL-AMSL-HUB", name="AMS Lite Filament Hub", brand="Bambu Lab", cat="ams", price=8.99, compat=A, days=3),
    dict(id="BL-AMS-DES6", name="Desiccant for AMS (6 pcs)", brand="Bambu Lab", cat="ams", price=5.99, compat=["p1p", "p1s", "x1c", "p2s", "x2d", "h2d", "a2l", "a1"], days=3),
    dict(id="BL-AMS-MB", name="AMS Mainboard", brand="Bambu Lab", cat="ams", price=69.99, compat=["p1p", "p1s", "x1c", "a1"], days=3),
    # --- build plates
    dict(id="BL-PLT-TPEI", name="Bambu Textured PEI Plate (256 mm)", brand="Bambu Lab", cat="plate", price=49.99, compat=PLATE256, days=3),
    dict(id="BL-PLT-DTPEI", name="Bambu Dual Texture PEI Plate (256 mm)", brand="Bambu Lab", cat="plate", price=42.49, compat=["a1", "p1p", "p1s", "x1c"], days=3),
    dict(id="BL-PLT-ENG", name="Engineering Plate (256 mm)", brand="Bambu Lab", cat="plate", price=39.99, compat=PLATE256, days=3),
    dict(id="BL-PLT-3DCF", name="3D Effect Plate Carbon Fiber (256 mm)", brand="Bambu Lab", cat="plate", price=24.99, compat=PLATE256, days=3),
    dict(id="BIQU-CRYO-256", name="Panda Build Plate CryoGrip Pro Glacier (256 mm)", brand="BIQU", cat="plate", price=29.99, compat=PLATE256, days=3),
    dict(id="PH-POPWEB-256", name="Arkfly POPweb Cool Plate (256 mm)", brand="Phaetus", cat="plate", price=18.99, compat=["a1", "p1p", "p1s", "x1c"], days=3),
    dict(id="BIQU-PYRO-H2", name="PyroGrip Build Plate (H2D / H2S)", brand="BIQU", cat="plate", price=25.99, compat=["h2d"], days=3),
]

# Simulated in-house stock & reorder level (the shop's own warehouse view)
import random as _r
_r.seed(7)
for _p in PARTS:
    _p["official"] = _p["brand"] == "Bambu Lab"
    _p.setdefault("was", None)
    _p["stock"] = _r.choice([0, 2, 3, 5, 8, 12, 18, 25, 40, 60]) if _p["id"] not in ("BL-HE-P04H", "BL-CHE-P04H", "BL-HE-A04H", "BL-CUT3") else _r.choice([3, 4, 6])
    _p["reorder_level"] = 10 if _p["price"] < 30 else 5

CATEGORIES = {
    "hotend": ("Hotend / nozzle", "Hotend / Düse"),
    "heater": ("Heater & thermistor", "Heizung & Thermistor"),
    "fan": ("Fans", "Lüfter"),
    "electronics": ("Electronics & cables", "Elektronik & Kabel"),
    "extruder": ("Extruder", "Extruder"),
    "sensor": ("Sensors", "Sensoren"),
    "cutter": ("Filament cutter", "Filamentschneider"),
    "wear": ("Wipers & socks", "Wischer & Socken"),
    "motion": ("Belts & motion", "Riemen & Mechanik"),
    "ptfe": ("PTFE & adapters", "PTFE & Adapter"),
    "ams": ("AMS parts", "AMS-Teile"),
    "plate": ("Build plates", "Druckplatten"),
}

ENCL = ["p1s", "p2s", "x1c", "x2d", "h2d"]
ALL = [p["id"] for p in PRINTERS]
AMS_P = AMS_USERS
CUTTER_P = ["p1p", "p1s", "x1c", "p2s", "x2d", "h2d"]

# Problems: steps carry a base success weight used by the history generator.
# step tuple: (weight, en, de, part_category or None)
PROBLEMS = [
    dict(id="P01", cat="extrusion", printers=ALL, freq=16,
         en="Nozzle clog / under-extrusion", de="Düse verstopft / Unterextrusion",
         kw="clog clogged verstopft under-extrusion nothing comes out no filament thin lines gaps",
         steps=[
             (8, "Check the spool and filament path for tangles or kinks.", "Spule und Filamentweg auf Knoten oder Knicke prüfen.", None),
             (34, "Do a cold pull: heat to 250 °C, push filament, cool to 90 °C, pull out firmly.", "Cold Pull durchführen: auf 250 °C heizen, Filament nachschieben, auf 90 °C abkühlen, kräftig herausziehen.", None),
             (18, "Clear the nozzle tip with the cleaning needle from the accessory box while hot.", "Düsenspitze im heißen Zustand mit der Reinigungsnadel aus dem Zubehör freimachen.", None),
             (10, "Check the extruder gears for filament dust and clean them.", "Extruderzahnräder auf Filamentabrieb prüfen und reinigen.", "extruder|gear"),
             (30, "Replace the hotend (quick-swap) with a new one.", "Hotend (Schnellwechsel) gegen ein neues tauschen.", "hotend"),
         ]),
    dict(id="P02", cat="extrusion", printers=ENCL, freq=8,
         en="Heat creep with PLA in enclosed printer", de="Heat Creep mit PLA im geschlossenen Drucker",
         kw="heat creep PLA door closed jams after hours enclosed warm chamber softens",
         steps=[
             (55, "For PLA, open the front door or remove the top glass to keep the chamber cool.", "Bei PLA die Fronttür öffnen oder den Glasdeckel abnehmen, damit die Kammer kühl bleibt.", None),
             (14, "Check that the hotend cooling fan spins freely at full speed.", "Prüfen, ob der Hotend-Lüfter frei und mit voller Drehzahl läuft.", None),
             (16, "Do a cold pull to clear the softened plug.", "Cold Pull durchführen, um den erweichten Pfropfen zu entfernen.", None),
             (15, "Replace the hotend cooling fan.", "Hotend-Lüfter tauschen.", "fan|hotend"),
         ]),
    dict(id="P03", cat="adhesion", printers=ALL, freq=15,
         en="First layer does not stick", de="Erste Schicht haftet nicht",
         kw="adhesion first layer not sticking lifts off bed haftung erste schicht löst sich",
         steps=[
             (42, "Wash the build plate with warm water and dish soap, dry without touching the surface.", "Druckplatte mit warmem Wasser und Spülmittel waschen, trocknen, Oberfläche nicht anfassen.", None),
             (14, "Apply a thin layer of glue stick (required on cool/engineering plates).", "Dünn Klebestift auftragen (bei Cool-/Engineering-Platte nötig).", None),
             (12, "Check that the plate type selected in Bambu Studio matches the plate on the printer.", "Prüfen, ob die in Bambu Studio gewählte Platte der eingelegten entspricht.", None),
             (12, "Run bed levelling and flow calibration again.", "Bettnivellierung und Flusskalibrierung erneut ausführen.", None),
             (20, "Replace a worn build plate (shiny or scratched PEI).", "Abgenutzte Druckplatte ersetzen (glänzendes oder zerkratztes PEI).", "plate"),
         ]),
    dict(id="P04", cat="adhesion", printers=ENCL, freq=5,
         en="Warping with ABS / ASA", de="Verzug bei ABS / ASA",
         kw="warping warp corners lift ABS ASA verzug ecken heben sich",
         steps=[
             (38, "Close the door and preheat the chamber with the bed at 100 °C for 10 minutes.", "Tür schließen und Kammer 10 Minuten mit Bett auf 100 °C vorheizen.", None),
             (24, "Add a brim (5-8 mm) in the slicer.", "Im Slicer eine Brim (5-8 mm) hinzufügen.", None),
             (20, "Dry the filament (ABS 80 °C, 8 h) before printing.", "Filament vor dem Druck trocknen (ABS 80 °C, 8 h).", None),
             (18, "Switch to the Engineering Plate with glue stick.", "Auf die Engineering-Platte mit Klebestift wechseln.", "plate|engineering"),
         ]),
    dict(id="P05", cat="ams", printers=AMS_P, freq=13,
         en="AMS fails to feed / filament stuck", de="AMS fördert nicht / Filament steckt fest",
         kw="AMS feed failed load unload stuck filament hub assist motor slot error",
         steps=[
             (30, "Check that the spool turns freely and the filament end is not tangled or wound under.", "Prüfen, ob die Spule frei dreht und das Filament nicht verknotet oder untergewickelt ist.", None),
             (22, "Cut the filament tip at an angle and reload the slot.", "Filamentspitze schräg abschneiden und Slot neu laden.", None),
             (18, "Check every PTFE tube connection for gaps or sharp bends.", "Alle PTFE-Schlauchverbindungen auf Spalten oder scharfe Knicke prüfen.", "ptfe"),
             (12, "Open the AMS and clear debris from the feeder and extrusion wheels.", "AMS öffnen und Feeder sowie Förderräder von Abrieb befreien.", None),
             (18, "Replace the AMS feeder unit of the affected slot.", "Feeder-Einheit des betroffenen Slots tauschen.", "ams|feeder"),
         ]),
    dict(id="P06", cat="ams", printers=CUTTER_P, freq=6,
         en="Filament cut failed", de="Filament konnte nicht geschnitten werden",
         kw="cut failed cutter blade lever filament cutting error schneiden",
         steps=[
             (24, "Push the cutter lever by hand and check that it moves and springs back.", "Schneidhebel von Hand drücken und prüfen, ob er sich bewegt und zurückfedert.", None),
             (46, "Replace the cutter blade.", "Schneidklinge tauschen.", "cutter|replacement filament cutter"),
             (30, "Replace the cutter lever.", "Schneidhebel tauschen.", "cutter|lever"),
         ]),
    dict(id="P07", cat="electronics", printers=ALL, freq=7,
         en="Nozzle temperature error (HMS)", de="Düsentemperatur-Fehler (HMS)",
         kw="temperature abnormal heating failed thermistor HMS error nozzle temp 0300 heater",
         steps=[
             (26, "Power off and reseat the hotend cable and connectors.", "Ausschalten, Hotend-Kabel und Stecker neu einstecken.", None),
             (14, "Update the printer firmware and run the self-test.", "Firmware aktualisieren und Selbsttest ausführen.", None),
             (36, "Replace the heater and thermistor (or the heating assembly on A1).", "Heizelement und Thermistor tauschen (beim A1 die Heizbaugruppe).", "heater"),
             (24, "Replace the complete hotend.", "Komplettes Hotend tauschen.", "hotend|complete"),
         ]),
    dict(id="P08", cat="motion", printers=ALL, freq=6,
         en="Layer shift", de="Schichtversatz",
         kw="layer shift shifted offset versatz verschoben",
         steps=[
             (30, "Check belt tension with the built-in tension routine or by hand.", "Riemenspannung prüfen (integrierte Routine oder von Hand).", None),
             (26, "Look for curled-up edges the nozzle could hit; enable avoid-crossing-walls.", "Auf hochstehende Kanten achten, gegen die die Düse stößt; Wände umfahren aktivieren.", None),
             (20, "Clean and lubricate the rods/rails.", "Stangen/Schienen reinigen und schmieren.", None),
             (24, "Replace worn belts.", "Verschlissene Riemen tauschen.", "motion|belt"),
         ]),
    dict(id="P09", cat="quality", printers=ALL, freq=7,
         en="Stringing / oozing", de="Fädenziehen / Nachtropfen",
         kw="stringing strings hairs oozing fäden spinnweben",
         steps=[
             (48, "Dry the filament (PETG 65 °C, 8 h; PLA 55 °C, 8 h).", "Filament trocknen (PETG 65 °C, 8 h; PLA 55 °C, 8 h).", None),
             (28, "Lower the nozzle temperature by 5-10 °C and check retraction in the profile.", "Düsentemperatur um 5-10 °C senken und Retraction im Profil prüfen.", None),
             (12, "Replace desiccant in the AMS.", "Trockenmittel im AMS ersetzen.", "ams|desiccant"),
             (12, "Replace a worn nozzle.", "Verschlissene Düse tauschen.", "hotend"),
         ]),
    dict(id="P10", cat="connectivity", printers=ALL, freq=6,
         en="Wi-Fi / cloud connection lost", de="WLAN-/Cloud-Verbindung verloren",
         kw="wifi wlan offline cloud connection bambu handy not connected network",
         steps=[
             (34, "Make sure the printer is on a 2.4 GHz network; separate the band if needed.", "Sicherstellen, dass der Drucker im 2,4-GHz-Netz ist; Band ggf. trennen.", None),
             (28, "Restart router and printer, then rebind in Bambu Handy.", "Router und Drucker neu starten, dann in Bambu Handy neu verbinden.", None),
             (22, "Update firmware and Bambu Studio to the latest version.", "Firmware und Bambu Studio aktualisieren.", None),
             (16, "Use LAN-only mode with access code as a workaround.", "Als Workaround den LAN-Modus mit Zugangscode nutzen.", None),
         ]),
    dict(id="P11", cat="extrusion", printers=ALL, freq=6,
         en="Extruder clicking / skipping", de="Extruder klickt / überspringt",
         kw="clicking skipping extruder grinding klicken knacken",
         steps=[
             (22, "Lower speed or max volumetric flow for this filament.", "Geschwindigkeit oder max. Volumenfluss für dieses Filament senken.", None),
             (30, "Clear a partial clog with a cold pull.", "Teilverstopfung per Cold Pull lösen.", None),
             (22, "Replace worn extruder gears.", "Verschlissene Extruderzahnräder tauschen.", "extruder|gear"),
             (26, "Replace the extruder unit.", "Extrudereinheit tauschen.", "extruder|extruder unit"),
         ]),
    dict(id="P12", cat="electronics", printers=ALL, freq=4,
         en="Noisy or failed fan", de="Lauter oder defekter Lüfter",
         kw="fan noise loud rattle fan error lüfter laut rattert",
         steps=[
             (30, "Remove filament strings or dust from the fan with compressed air.", "Filamentfäden oder Staub mit Druckluft aus dem Lüfter entfernen.", None),
             (20, "Check the fan cable connection.", "Lüfterkabel-Verbindung prüfen.", None),
             (50, "Replace the fan.", "Lüfter tauschen.", "fan"),
         ]),
    dict(id="P13", cat="calibration", printers=ALL, freq=5,
         en="Bed levelling / homing fails", de="Bettnivellierung / Referenzfahrt schlägt fehl",
         kw="levelling leveling homing failed probe z offset nozzle too high too low",
         steps=[
             (40, "Clean filament blobs off the nozzle tip (heat and wipe with a brass brush).", "Filamentreste von der Düsenspitze entfernen (heizen, mit Messingbürste abwischen).", None),
             (22, "Clean the plate and check it sits flat on all alignment pins.", "Platte reinigen und prüfen, ob sie flach an allen Führungsstiften anliegt.", None),
             (18, "Replace the nozzle wiper (A1) or purge wiper.", "Düsenwischer (A1) bzw. Purge-Wischer tauschen.", "wear|wiper"),
             (20, "Update firmware and run full calibration.", "Firmware aktualisieren und vollständige Kalibrierung ausführen.", None),
         ]),
    dict(id="P14", cat="quality", printers=ALL, freq=4,
         en="Wrong nozzle for carbon-fibre filament", de="Falsche Düse für Carbonfaser-Filament",
         kw="carbon fiber CF GF abrasive nozzle wear hardened stainless CF filament",
         steps=[
             (30, "Check the nozzle type set in the printer settings and in Bambu Studio.", "Düsentyp in den Druckereinstellungen und in Bambu Studio prüfen.", None),
             (70, "Fit a hardened-steel hotend for CF/GF filaments.", "Für CF/GF-Filamente ein Hotend mit gehärtetem Stahl einsetzen.", "hotend|hardened"),
         ]),
]

LANGS = [("en", 26), ("de", 24), ("hu", 10), ("fr", 8), ("it", 6), ("pl", 6), ("es", 6), ("nl", 6), ("cs", 4), ("sv", 2), ("ro", 2)]
PRINTER_POP = {"a1mini": 15, "a1": 20, "a2l": 4, "p1p": 4, "p1s": 23, "p2s": 9, "x1c": 12, "x2d": 6, "h2d": 4}
CHANNELS = [("chat", 45), ("email", 35), ("phone", 20)]

# A few real-world custom fixes support staff typed in ("learned" notes)
CUSTOM_FIXES = [
    ("P05", "Spool from another brand was too wide for AMS; printed spool adapter ring.", "Fremdspule zu breit für das AMS; Adapterring gedruckt."),
    ("P01", "Customer used wet TPU in AMS; moved TPU to external spool holder.", "Kunde nutzte feuchtes TPU im AMS; TPU auf externen Spulenhalter verlegt."),
    ("P03", "Plate was upside down (smooth side used with textured profile).", "Platte falsch herum eingelegt (glatte Seite mit Textured-Profil)."),
    ("P10", "Mesh router band steering: created dedicated 2.4 GHz IoT SSID.", "Mesh-Router Band-Steering: eigene 2,4-GHz-IoT-SSID angelegt."),
    ("P07", "Thermistor wire pinched under the front cover; re-routed cable.", "Thermistorkabel unter der Frontabdeckung eingeklemmt; neu verlegt."),
]
