# Security Policy

## Supported versions

Only the latest release (`main` branch + most recent tag) receives security fixes.

## Reporting a vulnerability

Email security concerns to the maintainer via GitHub's
[private vulnerability reporting](https://github.com/SecretArrow/sine-miner/security/advisories/new).
Do not open a public issue.

We aim to acknowledge reports within 72 hours and to ship a fix within 30 days
for high-severity issues (key leakage, RCE, fund theft).

## Threat model

SineMiner runs entirely on the user's device. The wallet keystore is encrypted
with AES-256-GCM (PBKDF2-derived key, 100k iterations by default). The mnemonic
phrase is shown **once** at creation/import time and is never written to disk.

Risks to be aware of:

- **Mnemonic on clipboard**: when you tap "Copy mnemonic", the phrase enters the
  system clipboard. Other apps may read it. Clear the clipboard after backing
  up.
- **Rooted / compromised devices**: a malicious app with root can read the
  encrypted keystore file and try to brute-force the password. Use a strong,
  unique password.
- **Screen recording**: Android 14+ allows apps to detect screen capture; older
  versions do not. Do not screen-record the mnemonic reveal screen.

## What we do NOT collect

- No analytics. No crash reporting. No telemetry. The app's network traffic
  is solely the Stratum pool connection (and your wallet's RPC if you opt in).
- No wallet data leaves the device unless you explicitly export it.

## Acknowledgments

We will credit reporters in the release notes that ships the fix, unless the
reporter prefers to remain anonymous.
