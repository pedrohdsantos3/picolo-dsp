# PicoloDSP Typography

## Font families
1. **Space Grotesk** - display, screen titles and brand-adjacent headings. Weights: 600, 700.
2. **Inter** - operational UI, labels, menus, descriptions and buttons. Weights: 400, 500, 600.
3. **JetBrains Mono** - technical values only: latency, sample rate, buffer, CPU, dB, BPM and numeric parameter readouts. Weights: 400, 500.

All three families are available under open font licenses. Keep their upstream license files when bundling them in an application. Font binaries are intentionally not included in this UI kit.

## Scale
| Token | Family | Weight | Size / line | Usage |
|---|---|---:|---|---|
| display | Space Grotesk | 700 | 32/38sp | marketing / empty states |
| h1 | Space Grotesk | 600 | 24/30sp | screen title |
| h2 | Space Grotesk | 600 | 20/26sp | card section |
| title | Inter | 600 | 18/24sp | preset/effect title |
| body | Inter | 400 | 16/24sp | standard text |
| bodyCompact | Inter | 400 | 14/20sp | secondary text |
| label | Inter | 600 | 12/16sp | control labels |
| technical | JetBrains Mono | 500 | 12/16sp | telemetry |
| value | JetBrains Mono | 500 | 14/18sp | knob/value readout |

## Rules
- Do not use monospaced text for paragraphs.
- Use uppercase sparingly: small module labels only.
- Prefer tabular numerals for meters and rapidly changing values.
- Minimum body size for stage use: 14sp; prefer 16sp for primary information.
- If APK size is a concern, Inter can replace Space Grotesk; keep JetBrains Mono only for telemetry if desired.
