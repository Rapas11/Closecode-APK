# Code Editor Design Spec (Android Compose)

Oct 9, 2026 · @Hh4hh

## Overview

This spec describes only the main editing area of the code editor: the gutter, the code text, and the popups that appear over it. The top bar and tab row are also in scope (see Top bar and tabs). The breadcrumb and bottom bar are out of scope and must not be changed.

The target is a calm, dark, professional editor in Android Jetpack Compose. Every item below was shown in the reference mockup, so the developer should match the mockup image first and use this document for exact values.

- Quiet by default: faint indent guides, small gutter icons, no boxes or side lines around code.
- Each helper appears next to the code it explains, never covering the line being typed.
- One indicator per idea: brace pairs get an underline only, errors get a squiggle plus a small pill, not several markers.

Features in scope:

1. Gutter with line numbers, error bar, lightbulb, fold arrows and git markers
2. Indent guides and syntax colors
3. Brace pair underline
4. Autocomplete list (4 visible rows, scrollable)
5. Error squiggle and error pill
6. Signature help tooltip
7. Selection handles and floating toolbar

8) Top bar (menu, project name, undo, redo, save, terminal, run, more) and the gray tab row with yellow unsaved dots

Units: all sizes are in dp, text sizes in sp, taken from a 380 dp wide phone screen. Scale them together on wider screens.

## Colors and fonts

Define every value below once as a named theme token and never hard-code them in the drawing code.

| Token | Hex | Used for |
| --- | --- | --- |
| editorBg | #0F1117 | Editor background |
| activeLineBg | #141925 | Background of the line with the cursor, gutter included |
| divider | #1C212C | Thin lines between editor sections |
| text | #D4D7DE | Default code text |
| lineNumber | #444C5E | Line numbers |
| lineNumberActive | #C5CAD6 | Line number of the cursor line |
| indentGuide | #232938 | Vertical indent guides |
| accent | #4C8DFF | Cursor, brace underline, active tab line |
| error | #EF5B5B | Squiggle, gutter error bar |
| keyword | #C792EA | package, import, class, fun, override, super |
| type | #E5C07B | Class and type names |
| function | #61AFEF | Function names |
| string | #98C379 | String literals |
| popupBg | #181D29 | Autocomplete and signature tooltip background |
| popupBorder | #2A3142 | Autocomplete and signature tooltip border |
| popupSelected | #243055 | Selected autocomplete row, active parameter |
| popupText | #C5CAD6 | Autocomplete row text |
| popupMuted | #7D869A | Type name at the end of a row |
| selection | #2A4380 | Selected text background |
| toolbarBg | #1F2533 | Selection toolbar background |
| toolbarBorder | #2F3749 | Selection toolbar border and separators |
| toolbarAccent | #7FB0FF | Last toolbar action (Format) |
| bulbIcon | #E5C07B | Lightbulb icon |
| bulbBg | #2A2615 | Lightbulb background |
| pillBg | #34191E | Error pill background |
| pillBorder | #5C2A31 | Error pill border |
| pillText | #F2A0A0 | Error pill text and icon |
| gitAdded | #3FB97A | Added lines |
| gitModified | #4C8DFF | Modified lines |
| gitDeleted | #EF5B5B | Deleted lines |
| foldArrow | #566078 | Fold chevrons |

Fonts:

- Code and line numbers: a monospace font. Use JetBrains Mono (bundled in res/font), regular weight. Code is 12sp, line numbers 11sp.
- Popups, pill and toolbar labels: the app's normal sans font, 11 to 12sp.
- Never use bold in code text. The only bold-looking text is the active parameter in the signature tooltip.

## Top bar and tabs

This section replaces the old top bar and tab row. It sits above the editor and uses the same colors.

**Top bar** (height 52 dp, 1 dp bottom border in divider color)

From left to right:

| Order | Item | Behavior |
| --- | --- | --- |
| 1 | Menu (three lines) | Far left. Opens the file explorer drawer. Keep it exactly as it is today |
| 2 | Project name | Fills the free space. Shows the project name only, such as Blank app. Never the file name and never a file path |
| 3 | Undo | Dimmed to 35% opacity when there is nothing to undo |
| 4 | Redo | Dimmed to 35% opacity when there is nothing to redo |
| 5 | Save | Shows a small yellow dot (6 dp, #E5C07B) at its top right when the file has unsaved changes |
| 6 | Terminal | Opens the terminal panel |
| 7 | Run | Blue button (#2F6FED), white play icon, corner radius 8 dp |
| 8 | More (three dots) | Opens the overflow menu |

Remove the search icon from the top bar. Search moves to the overflow menu and the bottom bar.

Sizes: each button is 34 by 34 dp with 19 dp icons in #9AA3B5. The project name is 14sp medium in #E6E8EE, one line, ending with an ellipsis when too long. There is no second line under the name. Left and right padding is 10 dp.

**Tab row** (height 34 dp, 1 dp bottom border in divider color)

- Tabs are plain text on the editor background: 12sp, color #6B7385, 12 dp left and right padding, 6 dp gap between the dot and the name.
- Active tab: text #E6E8EE, background #131722, and a 2 dp line along its bottom edge in accent color (#4C8DFF).
- Unsaved indicator: a 6 dp yellow dot (#E5C07B) before the file name on every tab that has unsaved changes. It is the gray-and-yellow look from the reference mockup.
- No file-type icon tile before the name, and no close (x) button on the tab. Closing is done with a long press on the tab, which shows Close, Close others and Close all.
- When there are more tabs than fit, the row scrolls sideways. A 36 dp fade from transparent to the editor background on the right edge shows that more tabs exist, and the active tab scrolls into view.
- Tapping a tab switches file and keeps each file's cursor and scroll position.

## Editor layout

The editor is two columns: a fixed gutter on the left and the scrolling code area on the right.

| Part | Value |
| --- | --- |
| Line height | 20 dp (about 1.7 times the 12sp font; keep this ratio if the font size changes) |
| Top padding above line 1 | 4 dp |
| Gutter width | 56 dp |
| Code area start | x = 56 dp, no extra gap and no divider line between gutter and code |
| Line number alignment | Right-aligned, 18 dp from the gutter's right edge |
| Indent step | 4 characters |
| Active line | Full width, from x = 0 including the gutter, colored activeLineBg |
| Word wrap | Off by default |

Scrolling rules:

- Vertical scroll moves gutter and code together.
- Horizontal scroll moves only the code. The gutter stays fixed.
- The code area must be clipped exactly at x = 56 dp (use clipRect), so code never shows under the gutter or gets cut halfway through a character at the left edge. Snap the horizontal scroll offset to whole characters when the user releases.
- Show a thin scrollbar thumb (3 dp wide, #3A4256) on the right edge only while scrolling.
- The editor fills all the space between the tab row and the bottom bar. Empty space below the last line keeps the editor background.

## Gutter

The gutter is 56 dp wide. Each item has its own fixed slot, so numbers never shift when an icon appears. Positions are measured from the gutter's left edge, inside each 20 dp line.

| Item | Slot (x from left) | Size and style |
| --- | --- | --- |
| Git marker | 0 to 3 dp | Bar 3 dp wide, 1 dp inset top and bottom, right corners rounded 2 dp |
| Error bar | 4 to 6 dp | 2 dp wide red bar, only on lines with an error |
| Lightbulb | 6 to 24 dp | 18 by 18 dp, corner radius 5 dp, background bulbBg, 14 dp bulb icon in bulbIcon |
| Line number | Right-aligned, ends at 38 dp | 11sp mono, lineNumber color |
| Fold arrow | 41 to 53 dp | 12 dp chevron in foldArrow color, vertically centered |

Rules for each item:

- **Line numbers:** the current line number uses lineNumberActive. Error lines are not colored red, because the red bar already shows it.
- **Error bar:** shown on every line that has an error, next to the git marker, never over it.
- **Lightbulb:** shown only on the cursor line and only when a quick fix exists. Tapping it opens a quick-fix menu. The tap area is 36 by 20 dp even though the icon is 18 dp.
- **Fold arrows:** shown on the first line of every foldable block: imports group, class, function, lambda, multi-line comment. Down chevron means open, right chevron means folded. Tapping folds or unfolds. Tap area is 32 dp wide and the full line height.
- **Folded block:** after the first line, show a small chip with an ellipsis (background #1A1F2B, radius 4 dp, text popupMuted). Tapping the chip also unfolds.
- **Git markers:** green bar for an added line, blue bar for a modified line. For deleted lines, draw a small red triangle on the left edge at the top of the line that follows the deleted lines (6 dp wide, 8 dp tall, pointing right). Only show markers when the project is a git repository.
- Do not draw breakpoint dots in this gutter unless a breakpoint feature is added later; it needs its own slot.

## Syntax colors and indent guides

Syntax highlighting uses only five colors so the code stays calm:

| Code element | Token | Example |
| --- | --- | --- |
| Keywords | keyword | package, import, class, override fun, super |
| Class and type names | type | MainActivity, Bundle, AppTheme |
| Function and method names | function | onCreate, setContent, Text |
| Strings | string | "Hello" |
| Everything else | text | variables, dots, brackets, commas |

Indent guides:

- One 1 dp vertical line per indent level, colored indentGuide (#232938).
- Each line is 20 dp tall, so guides join into one continuous line down a block.
- All guides use the same color. Do not make the current block's guide yellow or brighter.
- Guides continue through blank lines inside a block and end on the line before the closing brace.
- No guide for level 0 (code at the left edge).
- Guides move with horizontal scrolling because they belong to the code.

## Brace pair underline

When the cursor is inside a pair of brackets, the matching opening and closing bracket each get a thin underline. Nothing else changes.

- **Underline:** 2 dp thick, accent color (#4C8DFF), drawn 2 dp below the character baseline, exactly as wide as the character.
- **Which pair:** the innermost pair of `{ }`, `( )` or `[ ]` that encloses the cursor. If the cursor is directly next to a bracket, that bracket's pair wins.
- **No box, no side line:** do not draw a rectangle around the bracket and do not draw a vertical line to the left or right of it. These were the two things that looked unprofessional in the old editor.
- **Unmatched bracket:** underline the lone bracket in the error color (#EF5B5B) instead.
- **Off-screen partner:** underline only the visible bracket. Do not scroll the editor.
- **Update timing:** recompute when the cursor moves, with no animation.

## Autocomplete list

A small list of suggestions that shows four rows at a time and scrolls for the rest. The old list was too tall and covered a third of the screen; this one must stay compact.

| Part | Value |
| --- | --- |
| Width | 196 dp |
| Visible rows | 4 maximum. With fewer results, shrink to the number of results |
| Row height | 26 dp, so the visible list is at most 104 dp tall |
| Container | popupBg, 1 dp popupBorder, corner radius 8 dp, content clipped to the corners |
| Row padding | 8 dp left and right, 8 dp gap between items |
| Badge | 16 by 16 dp, corner radius 4 dp, one letter in 10sp medium white |
| Name | Monospace 12sp, popupText, fills the remaining width |
| Type | Sans 11sp, popupMuted, aligned right |
| Selected row | Background popupSelected, white text |
| Scrollbar | Thin 3 dp thumb (#3A4256), visible only while scrolling |

Badge letters and colors:

| Letter | Meaning | Color |
| --- | --- | --- |
| P | Property or parameter | #7A5AF0 |
| F | Function | #2F9E7A |
| M | Member or variable | #C28A2C |

Position and behavior:

- **Anchor:** directly under the cursor line, with the list's left edge at the start of the word being typed. Never anchor to the screen's left edge.
- **Keep on screen:** keep an 8 dp margin from the screen edges and slide the list left if it would go off the right edge.
- **Flip:** if there is not enough room below (for example the keyboard is open and the cursor is low), show the list above the line instead.
- **Never cover the cursor line:** the list starts at the bottom of the cursor line.
- **Selection:** the first row is selected automatically. Tapping a row inserts it and closes the list. Dragging scrolls the list without selecting.
- **Filtering:** the list updates on every keystroke and closes when there are no results, when the user types a space, or when the cursor leaves the word.
- **Sorting:** exact prefix matches first, then other matches, then by how recently they were used.
- **Closing:** tap outside the list, press Back, or keep typing past the last match.

## Error squiggle and error pill

An error is shown in three small places: a squiggle under the wrong word, a red bar in the gutter, and a short pill near the line. There is no big message bubble next to the cursor.

**Squiggle**

- Wavy underline under the exact range of the error, 1 dp thick, error color (#EF5B5B), drawn about 3 dp below the text.
- Warnings use the same shape in a yellow color (#E5C07B).

**Error pill**

| Part | Value |
| --- | --- |
| Height | 18 dp, fully rounded (radius 9 dp) |
| Padding | 6 dp left, 8 dp right |
| Colors | Background pillBg, 1 dp border pillBorder, text and icon pillText |
| Icon | 12 dp alert-circle icon, 4 dp gap before the text |
| Text | 11sp, one line, short form such as Unresolved 'mod'. Cut with an ellipsis after 22 characters |

Pill position and behavior:

- Show it on the line directly above the line that has the error, starting at least 12 dp after the end of that line's text. Never place it on the cursor line or on top of the autocomplete list.
- If the line above is too long for the pill, show it on the line below the error line instead.
- Show it only for the error on the cursor line. Other errors show only the squiggle and gutter bar.
- Hide the pill while the signature tooltip is open, and show it again when the tooltip closes.
- Tapping the pill opens a small sheet with the full error message and the quick fixes (the same list as the lightbulb).
- The pill never blocks typing: it ignores touches outside its own 18 dp height.

## Signature help tooltip

A small tooltip that shows the parameters of the function being called, with the current parameter highlighted. Example: while typing inside Text( ... ), it shows `Text(text: String, modifier: Modifier, color: Color)` with `modifier: Modifier` highlighted.

| Part | Value |
| --- | --- |
| Width | 272 dp, text wraps to a second line when needed |
| Container | popupBg, 1 dp popupBorder, corner radius 8 dp, padding 6 dp top and bottom, 10 dp left and right |
| Signature text | Monospace 11sp, line height 1.5, color #9AA3B5 |
| Active parameter | White text on popupSelected, corner radius 3 dp, 3 dp horizontal padding |
| Description line | Sans 11sp, popupMuted (#6B7385), 2 dp above it, one line |

Position and behavior:

- **Position:** above the cursor line, left edge at 62 dp from the editor's left edge, with a 4 dp gap above the line. It must not touch the cursor line.
- **No room above:** if the tooltip would go above the editor's top edge, show it below the cursor line and move the autocomplete list below the tooltip.
- **Opens:** after the user types ( or , inside a call that has known parameters.
- **Updates:** the highlighted parameter follows the number of commas before the cursor. Named arguments highlight their matching parameter.
- **Closes:** when the call's closing ) is typed, when the cursor leaves the call, on Back, or when scrolling more than one screen.
- **Overloads:** if a function has several versions, show a small counter such as 1/3 at the right end of the signature and switch versions with a tap on it.
- **Together with autocomplete:** both can be open at once. The tooltip is above the line and the list is below it, so they never overlap.

## Selection and toolbar

When the user selects text, the selected words get a soft blue background, two drag handles appear, and a small toolbar floats above.

**Selection and handles**

- Selection background: selection (#2A4380), corner radius 2 dp. Text color does not change.
- Two handles in accent color (#4C8DFF), 12 dp teardrops, one under the start and one under the end of the selection. Dragging a handle changes the selection; the touch area is 40 dp.
- Double tap selects a word. Long press selects a word and shows the toolbar.
- The blinking cursor is hidden while text is selected.

**Floating toolbar**

| Part | Value |
| --- | --- |
| Height | 34 dp |
| Container | toolbarBg, 1 dp toolbarBorder, corner radius 10 dp |
| Actions in order | Cut, Copy, Paste, Comment, Format |
| Item | 11 dp left and right padding, 12sp sans, color text (#D4D7DE) |
| Separators | 1 dp toolbarBorder between items |
| Last item (Format) | Color toolbarAccent (#7FB0FF) |

Toolbar behavior:

- **Position:** above the selection, left edge aligned with the start of the selection, 8 dp gap above the line. Keep 8 dp from the screen edges.
- **No room above:** place it below the handles.
- **Dragging:** hide it while a handle is being dragged or the editor scrolls, and show it again about 200 ms after the user stops.
- **Comment:** toggles line comments (// for Kotlin) on every selected line.
- **Format:** formats only the selection when it covers whole lines, otherwise the whole file.
- **Paste:** is disabled and dimmed (50% opacity) when the clipboard is empty.
- **Cut and Copy:** are disabled and dimmed when nothing is selected.

## Compose implementation notes

These are suggestions for the developer; any approach that gives the same result is fine.

1. **Two layers.** Draw the code text and gutter in one Canvas-based composable, and put every popup (autocomplete, signature tooltip, pill, selection toolbar) in separate composables above it. This keeps text drawing fast and popups easy to move.
2. **Measure with TextMeasurer.** Use a monospace font so one character has one fixed width. Get the character width once, then compute every x position from column numbers, and every y position from line number times 20 dp.
3. **Cursor rectangle.** Keep a function that returns the cursor's screen rectangle. Autocomplete, tooltip, pill and toolbar are all positioned from this one rectangle, which stops them from drifting.
4. **Popups.** Use Popup with a custom PopupPositionProvider so each one can flip above or below, and clamp to the screen edges with an 8 dp margin. Do not use DropdownMenu, because it anchors to its parent and cannot be placed under the cursor.
5. **Autocomplete list.** Use a LazyColumn with Modifier.heightIn(max = 104.dp) and fixed 26 dp rows. This gives scrolling for free and keeps exactly four rows in view.
6. **Gutter clipping.** Use clipRect(left = 56.dp) for the code layer so horizontal scrolling never draws under the gutter.
7. **Brace underline.** Draw it as a line in the same Canvas pass, using drawLine with strokeWidth 2 dp. Do not use background highlight spans.
8. **Squiggle.** Draw a repeating zigzag Path under the error range. Do not use TextDecoration.Underline, because it cannot be wavy.
9. **Keyboard.** Apply imePadding so the editor shrinks when the keyboard opens and the cursor line is never hidden. Recompute popup positions when the keyboard height changes.
10. **Large files.** Only draw and highlight the visible lines plus a few lines above and below. Run syntax highlighting and diagnostics off the main thread and update the UI when they return.
11. **Alternative.** If building a full editor takes too long, embed an existing editor library (for example Sora Editor) in an AndroidView and only restyle it with the tokens above. Check that the library can draw the brace underline and gutter slots described here before choosing it.

Common mistakes to avoid:

- A box and a vertical line around brackets.
- A tall autocomplete list that is not anchored to the cursor.
- Placing the lightbulb, pill and tooltip on top of each other.
- Code showing under the gutter or cut in the middle of a character when scrolled.
- Hard-coded colors in the drawing code instead of theme tokens.

## Acceptance checklist

The editor is done only when every item below is true on a real phone. Send a screenshot of each state for review.

- [ ] Only the editing area, top bar and tab row changed; the breadcrumb and bottom bar are untouched.
- [ ] All colors come from named theme tokens that match the Colors and fonts table.
- [ ] Code font is 12sp monospace, line height 20 dp, gutter 56 dp wide.
- [ ] The gutter stays fixed during horizontal scroll and code is clipped cleanly at x = 56 dp.
- [ ] The cursor line has a full-width activeLineBg and a brighter line number.
- [ ] Brace pairs show a 2 dp underline only, with no box and no side line.
- [ ] An unmatched bracket is underlined in red.
- [ ] Autocomplete shows at most 4 rows, scrolls, and sits right under the word being typed.
- [ ] Autocomplete flips above the line when the keyboard leaves no room below.
- [ ] The error squiggle is wavy under the exact error range, with a red bar in the gutter.
- [ ] The lightbulb appears on the cursor line only, when a quick fix exists, and opens the fix menu.
- [ ] The error pill sits on the line above the error and hides when the tooltip is open.
- [ ] Fold arrows fold and unfold imports, classes, functions and lambdas, with a chip when folded.
- [ ] Git markers show green, blue and red-triangle states and appear only in git projects.
- [ ] The signature tooltip shows above the line and highlights the current parameter.
- [ ] Selecting text shows two handles and a toolbar with Cut, Copy, Paste, Comment, Format.
- [ ] Autocomplete, tooltip, pill and toolbar never cover the cursor line or each other.
- [ ] Scrolling a file of 5,000 lines stays smooth.

* [ ] The top bar shows the menu at the far left, the project name (never the file name), undo, redo, save, terminal, run and more.
* [ ] The search icon is gone from the top bar.
* [ ] Undo and redo are dimmed when unavailable, and Save shows a yellow dot when there are unsaved changes.
* [ ] Tabs are gray text with a yellow dot for unsaved files, a blue line under the active tab, and a fade on the right edge.
* [ ] Tabs have no file icon tile and no close button; a long press shows Close, Close others and Close all.
