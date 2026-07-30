<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

---
description: Port a Shadertoy shader into a THM Addons main-menu background shader (.fsh)
argument-hint: <shadertoy-url> [output-name]
---

Port the Shadertoy shader at the URL in `$ARGUMENTS` into a working THM Addons main-menu
background shader. Full format spec: `docs/main-menu-shader-spec.md` — read it first if you
haven't already this session.

## Steps

1. **Parse `$ARGUMENTS`**: first token is the Shadertoy URL (`https://www.shadertoy.com/view/XXXXXX`),
   optional second token is the desired output filename (no extension, lowercase). If no name
   given, derive one from the shader's title (kebab/lowercase, e.g. "Sea Fire" → `seafire`).
   Check `src/main/resources/assets/thm-addon/shaders/` for a name collision before deciding.

2. **Get the GLSL source — ask the user to paste it, don't try to scrape it.** Confirmed by
   testing: shadertoy.com sits behind a Cloudflare JS challenge that blocks both `curl` and
   `WebFetch` (403 / "Just a moment..." interstitial) even for plain view pages, let alone the
   AJAX endpoint the site itself uses to load shader JSON. There is no scripted fetch path here
   short of solving a JS challenge, which is out of scope — don't attempt it, don't spend tool
   calls re-testing it. Instead, tell the user which shader you're porting (title/ID from the
   URL if visible) and ask them to paste the **Image** tab's GLSL source directly. Only the
   "Image" tab matters — ignore Buffer A/B/.../Sound/Cubemap tabs (this addon's pipeline has no
   multi-pass/feedback-texture support, see the spec's Constraints section). Never guess or
   fabricate shader code.

3. **Check portability before writing anything:**
   - If the shader reads `iChannel0..3` (any buffer/texture/cubemap/keyboard input) or needs
     a previous frame (feedback), it is **not portable** as-is — tell the user why and stop
     (or ask if they want a version with that sampling stripped/stubbed, if they explicitly
     want a best-effort port anyway).
   - `iMouse`, `iTime`, `iResolution`, `iFrame` (frame count — approximate via `time` if truly
     needed, flag this to the user) are fine; only `iMouse`/`iTime`/`iResolution` map directly
     to `ThmShaderData` fields (`mouse`, `time`, `resolution`).

4. **Transform the source** following the exact pattern in `docs/main-menu-shader-spec.md`:
   ```glsl
   #version 330 core
   // Adapted from Shadertoy (<url>) for THM Addons
   layout(std140) uniform ThmShaderData {
       float time;
       vec2 mouse;
       vec2 resolution;
   };

   out vec4 fragColor;

   #define iTime time
   #define iResolution resolution
   #define iMouse mouse

   // --------[ Original ShaderToy begins here ]---------- //
   <original Image-tab source, UNCHANGED>
   // --------[ Original ShaderToy ends here ]---------- //

   void main(void) {
       mainImage(fragColor, gl_FragCoord.xy);
       fragColor.a = 1.;
   }
   ```
   - Do not touch the body of the original code beyond the `#define`s above — the whole point
     of the defines is that the original `mainImage` needs zero edits.
   - If the original declares `#extension GL_OES_standard_derivatives : enable`, drop the line
     entirely (core in desktop GLSL 330, and mid-shader `#extension` after other declarations
     doesn't compile — see spec).
   - `iMouse` on Shadertoy is `vec4` (xy = current, zw = click position); `ThmShaderData.mouse`
     is `vec2`. If the shader only uses `iMouse.xy`, the plain `#define iMouse mouse` is fine.
     If it uses `.zw` too, that data doesn't exist here — tell the user and either stub `.zw`
     as `vec2(0.0)` (wrap in a local `vec4` reconstruction) or ask how to proceed.

5. **Write** the result to `src/main/resources/assets/thm-addon/shaders/<name>.fsh`.

6. **Validate.** Run `glslangValidator -S frag <file>` if the binary is available (check with
   `command -v glslangValidator` first; if missing, say so and skip — don't try to install it).
   Treat a pass as a smoke test only, per the spec's caveat about `#extension` placement bugs
   the validator has missed before — the real check is compiling in-game.

7. **Report**: file path written, whether validation passed/was skipped, any manual follow-ups
   the user should do (e.g. no build/registration step needed — `ShaderManager` autodiscovers
   it — but suggest launching the game and picking it via `shaderChoice`/`shaderPool` in the
   THM Menu to confirm it actually compiles against the real driver).
