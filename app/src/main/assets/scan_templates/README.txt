Phase 29 – Bitmap-Matching templates.

Drop canonical grayscale Standardbox PNGs here, one per card, named <cardKey>.png
(320x64 px). These are the bundled reference templates for the experimental bitmap
scan path. Generate them once via the Scanner-Test screen ("Als Vorlage speichern" +
"Alle exportieren"), then unpack the exported ZIP into this folder to bake them in.

Runtime overrides in filesDir/scan_templates/ take precedence over these bundled files,
so you can iterate without a rebuild.
