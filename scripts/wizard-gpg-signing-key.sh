#!/usr/bin/env bash
#
# A wizard — walks a human through a manual procedure step by step.
#
# Everything above the "STAGES" marker is the wizard library: do not hand-edit
# it. Author the per-step stages below the marker.

set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────
# Wizard library — delightful, consistent UX. Identical across every wizard.
# ──────────────────────────────────────────────────────────────────────────

if [[ -t 1 ]] && command -v tput >/dev/null 2>&1 && [[ "$(tput colors 2>/dev/null || echo 0)" -ge 8 ]]; then
  BOLD=$(tput bold); DIM=$(tput dim); RESET=$(tput sgr0)
  BLUE=$(tput setaf 4); GREEN=$(tput setaf 2); YELLOW=$(tput setaf 3); RED=$(tput setaf 1)
else
  BOLD=""; DIM=""; RESET=""; BLUE=""; GREEN=""; YELLOW=""; RED=""
fi

# Author sets this at the top of the stages section.
TOTAL_STAGES=0

_STAGE_INDEX=0
ENV_FILE="${ENV_FILE:-.env}"
WRITTEN_ENV=()    # KEYs written to ENV_FILE this run
WRITTEN_SECRET=() # secret NAMEs set this run
SKIPPED=()        # things we couldn't do (e.g. gh missing)

# _clear — wipe the terminal so only the current step is on screen. No-op when
# output isn't a terminal, so piped logs stay readable.
_clear() {
  [[ -t 1 ]] || return 0
  if command -v tput >/dev/null 2>&1; then tput clear; else printf '\033[2J\033[3J\033[H'; fi
}

# banner "Title" — opening frame: what this wizard does.
banner() {
  _clear
  printf '\n%s%s  %s%s\n' "$BOLD" "$BLUE" "$1" "$RESET"
  printf '%s  %s stages%s\n\n' "$DIM" "$TOTAL_STAGES" "$RESET"
  printf '%s  You drive the browser; this wizard tells you exactly what to do and\n' "$DIM"
  printf '  captures the values you copy back. Stop any time with Ctrl-C and re-run\n'
  printf '  later — it remembers values already saved.%s\n' "$RESET"
  pause "Ready to start?"
}

# stage "Name" — clear the screen, then announce a stage and show progress.
# Clearing keeps only the current step on screen.
stage() {
  _clear
  _STAGE_INDEX=$((_STAGE_INDEX + 1))
  printf '\n%s%s▸ Stage %s/%s · %s%s\n' \
    "$BOLD" "$BLUE" "$_STAGE_INDEX" "$TOTAL_STAGES" "$1" "$RESET"
}

# say "..." — a plain instruction line.
say()  { printf '  %s\n' "$1"; }
# step "..." — a numbered-feeling action the human takes in the browser.
step() { printf '  %s•%s %s\n' "$BLUE" "$RESET" "$1"; }
note() { printf '  %s%s%s\n' "$DIM" "$1" "$RESET"; }
warn() { printf '  %s⚠ %s%s\n' "$YELLOW" "$1" "$RESET"; }

# open_url URL — open in the human's browser, cross-platform incl. WSL.
open_url() {
  local url="$1"
  printf '  %s↗ opening%s %s\n' "$GREEN" "$RESET" "$url"
  { if   command -v wslview     >/dev/null 2>&1; then wslview "$url"
    elif command -v explorer.exe >/dev/null 2>&1; then explorer.exe "$url"
    elif command -v xdg-open    >/dev/null 2>&1; then xdg-open "$url"
    elif command -v open        >/dev/null 2>&1; then open "$url"
    else warn "couldn't open a browser — visit it manually: $url"; fi
  } >/dev/null 2>&1 || warn "couldn't open a browser — visit it manually: $url"
}

# pause "msg" — wait for the human to confirm they've done the manual part.
pause() {
  printf '  %s%s%s ' "$DIM" "${1:-Press Enter to continue}" "$RESET"
  read -r _ || true
}

# confirm "question" — y/N gate; returns success on yes.
confirm() {
  local reply=""
  printf '  %s? %s [y/N] ' "$YELLOW" "$1"
  read -r reply || true
  [[ "$reply" =~ ^[Yy] ]]
}

# _existing KEY — current value of KEY in ENV_FILE, if any.
_existing() {
  [[ -f "$ENV_FILE" ]] || return 1
  local line; line=$(grep -E "^${1}=" "$ENV_FILE" | tail -n1) || return 1
  printf '%s' "${line#*=}"
}

# ask KEY "Prompt" — read a value into $KEY. Offers the existing .env value as
# a default on re-runs (Enter keeps it). Visible input (non-secret).
ask() {
  local key="$1" prompt="$2" current input
  current=$(_existing "$key" || true)
  if [[ -n "$current" ]]; then
    printf '  %s%s%s %s[Enter keeps current]%s ' "$BOLD" "$prompt" "$RESET" "$DIM" "$RESET"
  else
    printf '  %s%s%s ' "$BOLD" "$prompt" "$RESET"
  fi
  read -r input || true
  [[ -z "$input" && -n "$current" ]] && input="$current"
  printf -v "$key" '%s' "$input"
}

# ask_secret KEY "Prompt" — like ask, but input is hidden.
ask_secret() {
  local key="$1" prompt="$2" current input
  current=$(_existing "$key" || true)
  if [[ -n "$current" ]]; then
    printf '  %s%s%s %s[Enter keeps current]%s ' "$BOLD" "$prompt" "$RESET" "$DIM" "$RESET"
  else
    printf '  %s%s%s ' "$BOLD" "$prompt" "$RESET"
  fi
  read -rs input || true
  printf '\n'
  [[ -z "$input" && -n "$current" ]] && input="$current"
  printf -v "$key" '%s' "$input"
}

# write_env KEY VALUE — upsert KEY=VALUE into ENV_FILE (creates it; replaces
# any existing line). Idempotent.
write_env() {
  local key="$1" value="$2" tmp
  touch "$ENV_FILE"
  tmp=$(mktemp)
  grep -vE "^${key}=" "$ENV_FILE" > "$tmp" || true
  printf '%s=%s\n' "$key" "$value" >> "$tmp"
  mv "$tmp" "$ENV_FILE"
  WRITTEN_ENV+=("$key")
  printf '  %s✓ wrote%s %s → %s\n' "$GREEN" "$RESET" "$key" "$ENV_FILE"
}

# set_secret NAME VALUE — set a GitHub Actions repo secret via gh. Falls back
# to a warning (and records it) if gh is unavailable or unauthenticated.
set_secret() {
  local name="$1" value="$2"
  if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    if printf '%s' "$value" | gh secret set "$name" >/dev/null 2>&1; then
      WRITTEN_SECRET+=("$name")
      printf '  %s✓ set%s GitHub secret %s\n' "$GREEN" "$RESET" "$name"
      return
    fi
  fi
  SKIPPED+=("GitHub secret $name (set it manually: gh secret set $name)")
  warn "skipped GitHub secret $name — gh not ready; set it later"
}

# set_var NAME VALUE — set a GitHub Actions repo variable (non-secret).
set_var() {
  local name="$1" value="$2"
  if command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1; then
    if gh variable set "$name" --body "$value" >/dev/null 2>&1; then
      printf '  %s✓ set%s GitHub variable %s\n' "$GREEN" "$RESET" "$name"
      return
    fi
  fi
  SKIPPED+=("GitHub variable $name")
  warn "skipped GitHub variable $name — gh not ready; set it later"
}

# finish — clear, then a closing summary of everything configured.
finish() {
  _clear
  printf '\n%s%s  ✓ Setup complete%s\n' "$BOLD" "$GREEN" "$RESET"
  (( ${#WRITTEN_ENV[@]} ))    && note "wrote ${#WRITTEN_ENV[@]} value(s) to $ENV_FILE: ${WRITTEN_ENV[*]}"
  (( ${#WRITTEN_SECRET[@]} )) && note "set ${#WRITTEN_SECRET[@]} GitHub secret(s): ${WRITTEN_SECRET[*]}"
  if (( ${#SKIPPED[@]} )); then
    printf '\n'; warn "still to do by hand:"
    for s in "${SKIPPED[@]}"; do note "  - $s"; done
  fi
  printf '\n'
}

# ──────────────────────────────────────────────────────────────────────────
# STAGES — author this section. One stage() per step the human takes.
# Replace the example below. Set TOTAL_STAGES to match the stages you write.
# ──────────────────────────────────────────────────────────────────────────


TOTAL_STAGES=6

# Where the captured values are staged. Deliberately outside the repository:
# nothing here may ever be committed. Issue #27 reads these into the GitHub
# environment SONATYPE, after which this directory is a backup, not a source.
STAGING_DIR="${BOTTOMSHEET_RELEASE_HOME:-$HOME/.bottomsheet-release}"
ENV_FILE="$STAGING_DIR/signing.env"
KEY_FILE="$STAGING_DIR/signing-key.asc"

# Maven Central accepts exactly three keyservers; the SKS network is dead.
# https://central.sonatype.org/publish/requirements/gpg/
KEYSERVER="keyserver.ubuntu.com"

UID_NAME_DEFAULT="Cedric Kummer"
UID_EMAIL_DEFAULT="cedric.kummer@gmx.de"
UID_COMMENT="BottomSheet release signing"
KEY_EXPIRY="2y"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GNUPGHOME_REAL="${GNUPGHOME:-$HOME/.gnupg}"

umask 077
mkdir -p "$STAGING_DIR"
chmod 700 "$STAGING_DIR"

# _fingerprints — every secret-key fingerprint currently in the keyring.
_fingerprints() {
  gpg --list-secret-keys --with-colons 2>/dev/null | awk -F: '/^fpr/{print $10}' || true
}

banner "BottomSheet — GPG signing key"

# ── Stage 1 ───────────────────────────────────────────────────────────────
stage "Preflight"
say "This wizard creates a signing key used for one thing only: signing the"
say "artifacts this project publishes to Maven Central."
printf '\n'
note "A dedicated key, not your personal one — a leak then costs this project"
note "and not your whole signing identity."
printf '\n'

if ! command -v gpg >/dev/null 2>&1; then
  warn "gpg not found on PATH — install GnuPG first, then re-run."
  exit 1
fi
say "gpg: $(gpg --version | head -n1)"
say "keyring: $GNUPGHOME_REAL"
say "staging: $STAGING_DIR (mode 700, outside the repository)"
printf '\n'

EXISTING_FPR="$(_existing BOTTOMSHEET_SIGNING_KEY_FINGERPRINT || true)"
if [[ -n "$EXISTING_FPR" ]] && _fingerprints | grep -qx "$EXISTING_FPR"; then
  say "A key from an earlier run is already here:"
  note "  $EXISTING_FPR"
  say "Later stages will reuse it instead of creating a second one."
else
  EXISTING_FPR=""
  say "Secret keys already in the keyring (none of these will be touched):"
  gpg --list-secret-keys --keyid-format=long 2>/dev/null \
    | grep -E '^(sec|uid)' | sed 's/^/    /' || note "    (none)"
fi
printf '\n'
confirm "Continue?" || { say "Nothing done."; exit 0; }

# ── Stage 2 ───────────────────────────────────────────────────────────────
stage "Create the key"
say "Shape, fixed by the ticket discussion and verified against Gradle 9.7.0's"
say "signing plugin before this wizard was written:"
printf '\n'
note "  RSA 4096, sign-only, no subkey — the primary key signs, so the"
note "  optional signingInMemoryKeyId property is not needed at all."
note "  Expires in $KEY_EXPIRY. Stage 6 shows how to extend it."
printf '\n'

if [[ -n "$EXISTING_FPR" ]]; then
  say "Reusing the key from the earlier run — skipping creation."
  FPR="$EXISTING_FPR"
  PASSPHRASE="$(_existing BOTTOMSHEET_SIGNING_PASSPHRASE || true)"
  if [[ -z "$PASSPHRASE" ]]; then
    warn "The passphrase is missing from $ENV_FILE and cannot be recovered from"
    warn "the keyring. If you no longer have it, delete the key and start over:"
    note "  gpg --delete-secret-and-public-key $FPR"
    ask_secret PASSPHRASE "Paste the passphrase for the existing key:"
    write_env BOTTOMSHEET_SIGNING_PASSPHRASE "$PASSPHRASE"
  fi
else
  ask UID_NAME "Name for the key's user id [$UID_NAME_DEFAULT]:"
  [[ -z "$UID_NAME" ]] && UID_NAME="$UID_NAME_DEFAULT"
  ask UID_EMAIL "Email for the key's user id [$UID_EMAIL_DEFAULT]:"
  [[ -z "$UID_EMAIL" ]] && UID_EMAIL="$UID_EMAIL_DEFAULT"
  printf '\n'

  say "A random passphrase is stronger than one you invent, and nobody ever"
  say "types this one — CI reads it from a secret, you keep it in a manager."
  if confirm "Generate the passphrase for you?"; then
    if command -v openssl >/dev/null 2>&1; then
      PASSPHRASE="$(openssl rand -hex 24)"
    else
      PASSPHRASE="$(LC_ALL=C od -An -tx1 -N24 /dev/urandom | tr -d ' \n')"
    fi
    say "Generated a 48-character passphrase; it is stored in $ENV_FILE."
  else
    ask_secret PASSPHRASE "Passphrase for the new key:"
    ask_secret PASSPHRASE_CONFIRM "Repeat it:"
    if [[ "$PASSPHRASE" != "$PASSPHRASE_CONFIRM" ]]; then
      warn "The two entries differ. Re-run the wizard."
      exit 1
    fi
  fi
  printf '\n'

  BEFORE="$(_fingerprints)"
  PARAMS="$STAGING_DIR/.keyparams.$$"
  cat > "$PARAMS" <<PARAMS_EOF
Key-Type: RSA
Key-Length: 4096
Key-Usage: sign
Name-Real: $UID_NAME
Name-Comment: $UID_COMMENT
Name-Email: $UID_EMAIL
Expire-Date: $KEY_EXPIRY
Passphrase: $PASSPHRASE
%commit
PARAMS_EOF

  say "Generating — this takes a moment while gpg gathers entropy."
  if ! gpg --batch --pinentry-mode loopback --generate-key "$PARAMS" 2>&1 \
       | sed 's/^/    /'; then
    rm -f "$PARAMS"
    warn "Key generation failed. Nothing was written."
    exit 1
  fi
  rm -f "$PARAMS"

  FPR="$(comm -13 <(printf '%s\n' "$BEFORE" | sort) <(_fingerprints | sort) | head -n1)"
  if [[ -z "$FPR" ]]; then
    warn "Could not identify the new key's fingerprint. Inspect the keyring by"
    warn "hand with: gpg --list-secret-keys --keyid-format=long"
    exit 1
  fi
fi

KEY_ID="${FPR: -16}"
printf '\n'
say "Fingerprint: $FPR"
say "Long key id: $KEY_ID"
write_env BOTTOMSHEET_SIGNING_KEY_FINGERPRINT "$FPR"
write_env BOTTOMSHEET_SIGNING_KEY_ID "$KEY_ID"
write_env BOTTOMSHEET_SIGNING_PASSPHRASE "$PASSPHRASE"
write_env BOTTOMSHEET_SIGNING_KEY_EXPIRY "$(gpg --list-keys --with-colons "$FPR" 2>/dev/null | awk -F: '/^pub/{print $7; exit}')"
pause "Press Enter to publish the public half."

# ── Stage 3 ───────────────────────────────────────────────────────────────
stage "Publish the public key"
say "Central verifies release signatures by fetching the public key from a"
say "keyserver. Without this step every upload fails validation."
printf '\n'
note "Your dirmngr.conf defaults to keys.openpgp.org, so the keyserver is"
note "passed explicitly below."
printf '\n'
say "Sending $KEY_ID to $KEYSERVER ..."
if gpg --keyserver "$KEYSERVER" --send-keys "$FPR" 2>&1 | sed 's/^/    /'; then
  say "Sent."
else
  warn "Upload reported an error — the verification below will tell us for sure."
fi
printf '\n'

say "Verifying it can be fetched back by a stranger ..."
PROBE_HOME="$(mktemp -d)"
chmod 700 "$PROBE_HOME"
FOUND=""
for attempt in 1 2 3; do
  if GNUPGHOME="$PROBE_HOME" gpg --keyserver "$KEYSERVER" \
       --recv-keys "$FPR" >/dev/null 2>&1; then
    FOUND="yes"; break
  fi
  note "  not visible yet (attempt $attempt/3) — keyservers take a moment"
  sleep 10
done
GNUPGHOME="$PROBE_HOME" gpgconf --kill all >/dev/null 2>&1 || true
rm -rf "$PROBE_HOME"

if [[ -n "$FOUND" ]]; then
  say "${GREEN}✓${RESET} The key is retrievable from $KEYSERVER."
else
  warn "Could not fetch the key back yet. Propagation is usually seconds but"
  warn "can lag. Re-check later with:"
  note "  gpg --keyserver $KEYSERVER --recv-keys $FPR"
  SKIPPED+=("confirm the public key is live on $KEYSERVER")
fi
printf '\n'

if confirm "Also send it to keys.openpgp.org as a second source?"; then
  note "It will strip the user id unless you confirm the address by email."
  note "That is fine — Central only needs the key material."
  gpg --keyserver keys.openpgp.org --send-keys "$FPR" 2>&1 | sed 's/^/    /' || true
fi
pause "Press Enter to export the private half."

# ── Stage 4 ───────────────────────────────────────────────────────────────
stage "Export the private key"
say "The Gradle signing plugin reads the key in memory. Two contradictory"
say "instructions exist in the wild about the format; both were tried against"
say "Gradle 9.7.0 before this wizard was written:"
printf '\n'
note "  full ASCII-armored block, line breaks intact  → signs, verifies"
note "  first/last lines stripped, line breaks removed → 'Could not read PGP"
note "                                                   secret key'"
printf '\n'
say "So: the complete block, exactly as gpg emits it."
printf '\n'

if ! gpg --batch --pinentry-mode loopback --passphrase "$PASSPHRASE" \
     --export-secret-keys --armor "$FPR" > "$KEY_FILE" 2>/dev/null; then
  warn "Export failed — the passphrase in $ENV_FILE may not match the key."
  rm -f "$KEY_FILE"
  exit 1
fi
chmod 600 "$KEY_FILE"

if ! head -n1 "$KEY_FILE" | grep -q 'BEGIN PGP PRIVATE KEY BLOCK'; then
  warn "The exported file does not look like an armored private key."
  exit 1
fi
say "${GREEN}✓${RESET} Wrote $(wc -c < "$KEY_FILE" | tr -d ' ') bytes to $KEY_FILE (mode 600)"
write_env BOTTOMSHEET_SIGNING_KEY_FILE "$KEY_FILE"
pause "Press Enter to prove the exported key actually signs."

# ── Stage 5 ───────────────────────────────────────────────────────────────
stage "Prove the exported key signs"
say "Maven Central is append-only: a published version can never be replaced."
say "So the key is exercised now, through the same code path the release will"
say "use — Gradle's signing plugin reading it from an environment variable."
printf '\n'

PROBE_DIR="$(mktemp -d)"
mkdir -p "$PROBE_DIR/src/main/java"
cat > "$PROBE_DIR/settings.gradle.kts" <<'PROBE_EOF'
rootProject.name = "signing-probe"
PROBE_EOF
cat > "$PROBE_DIR/build.gradle.kts" <<'PROBE_EOF'
plugins {
    `java-library`
    signing
}

signing {
    useInMemoryPgpKeys(
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey").get(),
        providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword").get(),
    )
    sign(tasks.jar.get())
}
PROBE_EOF
printf 'public final class Probe {}\n' > "$PROBE_DIR/src/main/java/Probe.java"

PROBE_OK=""
if [[ -x "$REPO_ROOT/gradlew" ]]; then
  say "Running a throwaway Gradle build ..."
  if ORG_GRADLE_PROJECT_signingInMemoryKey="$(cat "$KEY_FILE")" \
     ORG_GRADLE_PROJECT_signingInMemoryKeyPassword="$PASSPHRASE" \
     "$REPO_ROOT/gradlew" -p "$PROBE_DIR" signJar --no-daemon -q >/dev/null 2>&1 \
     && [[ -f "$PROBE_DIR/build/libs/signing-probe.jar.asc" ]] \
     && gpg --verify "$PROBE_DIR/build/libs/signing-probe.jar.asc" \
            "$PROBE_DIR/build/libs/signing-probe.jar" >/dev/null 2>&1; then
    PROBE_OK="yes"
  fi
else
  warn "No gradlew at $REPO_ROOT — skipping the proof."
  SKIPPED+=("verify the exported key signs through Gradle")
fi
rm -rf "$PROBE_DIR"

if [[ -n "$PROBE_OK" ]]; then
  say "${GREEN}✓${RESET} Signed and verified. The exported block is usable as-is."
elif [[ -x "$REPO_ROOT/gradlew" ]]; then
  warn "The probe did not produce a verifiable signature."
  warn "Do not wire this key into CI until that is understood — re-run with the"
  warn "build output visible:"
  note "  ORG_GRADLE_PROJECT_signingInMemoryKey=\"\$(cat $KEY_FILE)\" \\"
  note "  ORG_GRADLE_PROJECT_signingInMemoryKeyPassword='<passphrase>' \\"
  note "  $REPO_ROOT/gradlew -p <probe> signJar --no-daemon"
  SKIPPED+=("investigate why the Gradle signing probe failed")
fi
pause "Press Enter for the handover."

# ── Stage 6 ───────────────────────────────────────────────────────────────
stage "Back up and hand over"
say "${BOLD}Back up two things that cannot be regenerated:${RESET}"
printf '\n'
step "The key and passphrase — put $KEY_FILE and the"
say "  passphrase from $ENV_FILE into your password manager."
note "  Lose them and future releases are signed by a different key."
step "The revocation certificate gpg wrote at key creation:"
note "  $GNUPGHOME_REAL/openpgp-revocs.d/$FPR.rev"
note "  It is the only way to revoke the key if it ever leaks."
printf '\n'

say "${BOLD}The three values issue #27 needs for environment SONATYPE:${RESET}"
printf '\n'
say "  private key   → Gradle property signingInMemoryKey"
note "                  the whole file, newlines intact:"
note "                  pbcopy < $KEY_FILE"
say "  passphrase    → Gradle property signingInMemoryKeyPassword"
note "                  grep BOTTOMSHEET_SIGNING_PASSPHRASE $ENV_FILE"
say "  key id        → $KEY_ID"
note "                  not needed as a secret; the primary key signs, so"
note "                  signingInMemoryKeyId stays unset. Recorded for the"
note "                  keyserver and for support requests."
printf '\n'
note "A multi-line value survives a GitHub Actions secret and the env var"
note "round-trip unchanged — verified. No base64 wrapper."
printf '\n'

say "${BOLD}When the key expires:${RESET}"
note "  gpg --edit-key $FPR   then: expire, save"
note "  gpg --keyserver $KEYSERVER --send-keys $FPR"
note "An expired key does not invalidate signatures it already made."
printf '\n'
pause "Press Enter to finish."

finish
