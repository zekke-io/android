# `ui` — the design system

What every screen is drawn with. Today it holds `ZekkeTheme`, the Material 3 theme with the web app's
primary colour, background and text colours; task A0.4 brings the full token set (colours, type
scale, shapes, light and dark) and the components every secret-bearing screen uses: `PinField`,
`SecretField`, `CopyButton` and the private-text modifiers.

It depends on Compose and Material 3 only, never on the core: a component draws what it is given
and holds no key.
