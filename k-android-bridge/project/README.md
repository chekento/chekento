# K Android 1.0 · Companion Audio Bridge

Native Android helper for the Total Launcher K Android companion.

- Passive Android AudioPlaybackCapture (system media/game output is not rerouted)
- 48 kHz / 20 ms analysis windows
- 15 Goertzel bands with AGC, noise-floor tracking, attack/decay smoothing
- one-row LCARS capsule mouth only — no second lip line
- AUTO / SPEECH / MUSIC modes
- transparent always-on-top overlay aligned to the upper 44% Claude-safe zone
- microphone fallback for apps that opt out of playback capture
- live AI + EU AI Act + GDPR/DSGVO news reader and widget
- Quick Settings tile for fast capture start

Android requires the user to approve MediaProjection/PlaybackCapture for each new capture session. Apps can opt out of playback capture; that platform policy cannot be bypassed.
