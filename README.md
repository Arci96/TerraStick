# TerraStick

Paper-Plugin (1.21.11) – Terraform-Stab für Hügel, Berge, Mauern und mehr.

## Befehle
`/terra <modus> [größe] [block(e)] [höhe]`

| Modus | Beschreibung | Beispiel |
|---|---|---|
| `hill` | Hügel, Größe = Radius | `/terra hill 10` |
| `mountain` | Zackiger Berg mit Schneespitze | `/terra mountain 20` |
| `wall` | Mauer quer zur Blickrichtung, Größe = Länge | `/terra wall 15 stone_bricks 6` |
| `crater` | Krater in den Boden graben | `/terra crater 8` |
| `tower` | Hohler runder Turm mit Zinnen (Standardhöhe 15) | `/terra tower 4 stone_bricks 20` |
| `sphere` | Volle Kugel, sitzt auf dem Zielblock | `/terra sphere 6 glass` |
| `brush` | Ersetzt vorhandene Blöcke in einer Kugel zufällig | `/terra brush 5 stone,cobblestone,andesite` |
| `raise` | Gelände anheben (wie WorldEdit-Pinsel), Höhe = Stärke (Standard 3) | `/terra raise 6 auto 3` |
| `lower` | Gelände absenken, Höhe = Stärke (Standard 3) | `/terra lower 6 auto 3` |
| `smooth` | Gelände glätten (mehrfach klicken) | `/terra smooth 8` |
| `undo` | Letzte Aktion rückgängig (bis zu 10) | `/terra undo` |

**Zufallsblöcke:** Mehrere Blöcke mit Komma trennen (ohne Leerzeichen), z. B. `stone,cobblestone,mossy_cobblestone`. Das geht bei allen Modi außer `crater`.

Stab in die Hand nehmen, auf einen Block zielen (bis 100 Blöcke), **Rechtsklick**.
Permission: `terrastick.use` (Standard: OP)

## Bauen
Push auf GitHub -> Reiter **Actions** -> letzten Lauf öffnen -> unter **Artifacts** `TerraStick` herunterladen -> `TerraStick.jar` in den `plugins`-Ordner deines Servers.
