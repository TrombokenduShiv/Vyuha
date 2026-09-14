# Interface refinement — 14 September 2026

- User correction: remove excessive preloader copy, the matrix/grid background and confusing engineering text in the payment interface; prioritize smoother motion.
- The active payment dashboard is `apps/judge-dashboard/src/App.jsx`, reading the saved evaluation JSON. Preserve its seven scenarios and ADR-005 distinctions; the older simulation hook is not the active data source.
- Replaced the preloader destination placeholder with the already-loaded dashboard, served locally at `/dashboard/`. Old `?preview=1` URLs now run the normal intro; inspection controls require `?inspect=1`.
- Retain a compact Demo data label and expandable evidence details. Unknown recipient scores remain unknown, recipient risk remains separate from influence risk, and bank authorization ownership stays explicit.
- Composer rendering needs its own antialias pass; canvas antialiasing alone does not smooth the composer render target. Added FXAA and a bounded adaptive pixel ratio, and removed frame-loop camera vector allocation.
