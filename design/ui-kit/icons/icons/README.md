# PicoloDSP Icon System

All icons use a 24x24 viewBox, 1.8px rounded stroke and `currentColor`. They are intentionally simple for reliable rendering on stage and at small sizes.

## Sizes
- 20dp: compact inline action
- 24dp: default navigation/action
- 28dp: effect block icon
- 32dp: hero/empty state
- 48dp minimum touch target around every interactive icon

## States
Do not communicate active/inactive state by color alone. Pair semantic accent color with border/background/bypass state. Disabled icons use reduced opacity and remain non-interactive.

## Android
Import SVGs through Android Studio Vector Asset (`New > Vector Asset > Local file`). Keep path color theme-driven rather than hardcoded where possible.
