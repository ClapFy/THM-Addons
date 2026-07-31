<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

---
description: Write the GitHub release text for the commits since the last release tag
argument-hint: [new-version] [since-tag]
---

Write the release body for the next THM Addons release and **print it in chat as plain markdown**
(no file, no attachment — the user copies it straight into GitHub).

## Steps

1. **Resolve versions.** `$ARGUMENTS` may give the new version and/or the tag to diff against.
   Defaults: since-tag = `git tag --sort=-creatordate | head -1`, new version = that tag's number
   bumped by one patch (tags look like `release0.2.6`).
2. **Read the range**: `git log --oneline <since>..HEAD`, then `git show --stat` per commit, and
   diff the interesting ones (`git show <sha> -- <file> | grep '^+'`) to find *what settings and
   modules changed*. Commit messages here are useless ("Fix and break hella shit") — go by the code.
3. **Check the working tree** (`git status --short`). Uncommitted work is not in the release: leave
   it out and say so at the end, outside the release text.
4. Write it.

## Shape

```
# THM Addons <version>

<one line: the 2-3 headline changes>

## ⚠️ Breaking            <- only if something needs user action (renamed setting, lost config)
## <Area>                 <- New / PvP / Mining / Highway Builder / GUI — only areas that changed
## Misc                   <- dependency bumps, chores; one line each

**Full changelog:** https://github.com/Leonn170709/THM-Addons/compare/<since>...<new-tag>
```

## Rules

- **Short.** One line per change, bold the setting or module name, then what it does for the player.
  A whole area is 3-6 bullets. If it needs a paragraph, it needs fewer words.
- User-facing only. No class names, no file paths, no "refactored X" — if a player can't see it,
  it doesn't go in. Skip pure-internal commits entirely.
- Group by area, not by commit. Several commits fixing one thing are one bullet.
- Anything that resets a setting or needs a re-entered value goes in **⚠️ Breaking** at the top.
