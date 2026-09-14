# VYUHA payment review

The existing React dashboard presents seven saved evaluation scenarios. The influence and recipient scores, policy decisions and evidence come from `src/data/evaluation.json`; this interface does not call a live bank or graph service.

## Combined introduction and dashboard

From this directory:

```powershell
npm run demo
```

Open http://127.0.0.1:4173/. This builds the dashboard, then serves the root procedural Three.js introduction with the dashboard preloaded at `/dashboard/`. The intro fades into the workspace. The intro needs the pinned Three.js and GSAP CDN modules; failure or reduced-motion preference skips it without blocking the dashboard.

Normal playback also works on old `?preview=1` links. Developer phase controls are only enabled with `?inspect=1`.

## Dashboard development

`npm run dev` runs the React dashboard directly through Vite with hot reload. `npm run build` produces the static dashboard in `dist/`. When using the combined preview, rebuild after dashboard source changes.

From the repository root, run:

```powershell
node --test tests/dashboard-presentation.test.mjs
```

Presentation helpers live in `src/presentation.js`. Keep unknown recipient scores null, preserve the independent risk dimensions, and use the saved policy template for the recommended action.
