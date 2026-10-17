# Screenshots

These images show PyCharm 2026.2.3 in an isolated profile with sample files.
All captures use four times the logical pixel density. No image was upscaled.

Static captures use CodexCommit 1.1.7:

- `generate-button.png`: Commit toolbar and tooltip, 2200 × 464 pixels. The bundled Overview uses these same bytes.
- `commit-message.png`: a short draft in the Commit box, 1824 × 1412 pixels.

Animated captures use CodexCommit 1.1.8:

- `how-to.webp`: select files, click once, wait, and review the draft; 3840 × 2160 pixels.
- `generate-button.webp`: the real toolbar button with its full rotation and smooth return; 2200 × 464 pixels.

Both WebP files use lossless encoding. The native capture targets are 8 fps for the full window and 25 fps for the button.
The request uses a local test backend held long enough to show the full motion curve.
The draft appears when it is ready. The visual return does not hold the draft. The Overview stays static.

The harness supplies the sample draft. The real button, background tasks, selection checks, and draft insertion run in PyCharm.
No Codex account request occurs. This is not a recorded model response or complete real-account acceptance test.
