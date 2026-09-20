# Store assets

Everything Play needs for the listing, per build plan §10. The plan says these are
"produced as files in `store/`, not uploaded by the agent" — uploading is Phase 8 and
yours to do.

| File | What it is | Play requirement |
|---|---|---|
| `icon-512.png` | Listing icon | 512×512, 32-bit PNG, no transparency |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500 |
| `screenshots/01…06` | Phone screenshots | 2–8, min 320px on the short side |
| `listing.md` | App name, short and full description | 30 / 80 / 4,000 characters |
| `privacy-policy.html` | Privacy policy | Required because the app serves ads |

## Regenerating

```bash
python store/generate_assets.py      # icon, feature graphic, launcher icons
python store/compose_screenshots.py  # adds caption bands to store/raw/*.png
```

`generate_assets.py` writes the launcher icons straight into `app/src/main/res` as well,
so the icon in the launcher and the icon on the listing cannot drift apart.

The palette is duplicated at the top of `generate_assets.py` from
`ui/theme/Color.kt`. There is no way to share constants across that boundary, so if the
theme changes, change it there too.

## The icon

§10 calls this "the single highest-leverage asset you will make… legible at 48px".

It is a 5×5 grid with a diamond filled in — the app's own board, in miniature. Two things
it has to do at launcher size, and the first attempt failed both:

- **Read as a grid.** The first version filled a contiguous blob and drew the separators
  underneath it. At any size that collapses into one black shape with notched corners,
  which says nothing about puzzles. Every cell is now drawn as its own square with a
  visible gap, and empty cells get a pale tint instead of being left as background.
- **Have a silhouette.** A scattered fill pattern turns to noise when shrunk. The diamond
  is symmetric and unbroken, so it survives.

The gap has a two-pixel floor. It is split either side of a cell, so a one-pixel gap
leaves half a pixel between neighbours, the renderer rounds it away, and adjacent filled
cells merge back into a blob — which is exactly how the grid stopped reading at 48px on
the first attempt. Check any change against
`python store/generate_assets.py` followed by an eyeball at 48px before committing it.

## The screenshots

The raw captures in `store/raw/` came off a real Galaxy A15, not a mock-up. Puzzles were
solved by reading the solution out of the bundled pack and drag-painting each run over
adb, the same way a player would.

**The device state is seeded.** `store/raw` shows a streak of 8, a best of 12 and 34
solved puzzles. Android will not let a non-rooted device's clock be changed, so a real
multi-day streak cannot be played out on demand; the database was written directly
instead via `run-as` on the debug build. Nothing about the app was modified — every
feature shown is real, and the numbers are representative of a returning player rather
than invented capabilities. Recapture from genuine play before launch if you would
rather they be literal.

Captions are added by `compose_screenshots.py` rather than baked into the captures, so
the wording can be reworded without replaying the device — which is the slow part.

## Before you upload

- [ ] **Put a contact email in `privacy-policy.html`.** It is marked `TODO`; Play
      requires a working address.
- [ ] Publish the policy somewhere public. GitHub Pages is free and acceptable — enable
      Pages on this repo and the file will serve at
      `https://amerganim.github.io/Nonogram/privacy-policy.html` if you move it to
      `/docs` or the Pages branch.
- [ ] Confirm the app name is free on Play; names must be unique.
- [ ] Declare **contains ads** and complete the Data Safety form. AdMob collects device
      identifiers — the policy lists exactly what, and the two must agree.
- [ ] Complete the content rating questionnaire (Everyone; no violence, no UGC, no
      social features).

## What is deliberately not here

- **A promo video.** Optional on Play, and a bad use of time before there is any signal
  that the listing converts.
- **Tablet or 7"/10" screenshots.** Only needed if you publish for tablets. The app works
  on them, but §12's plan is phone-first.
- **Localised listings.** §11.3 suggests Bengali, Spanish, Portuguese and Indonesian
  *later*. English only for launch.
