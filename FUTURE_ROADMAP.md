# Tuny Music • Future Roadmap & Pro Feature Implementation

> **Vision:** Craft Tuny Music into an ultra-responsive, sensory-rich, 100% offline precision audio looper and player that users love exploring and keeping active.

---

## 🏆 Tier 1: User's Core Functional Roadmap

### 1. 🔁 Universal Loop Option for Folders
* **Concept:** Expand repeat loops beyond individual tracks to entire playlists and custom folders.
* **Details:**
  * Allow users to select an entire folder and set custom loop iteration counts (e.g. 1, 3, 5, 10, 50, or Infinite `∞`).
  * Seamless playback queue transitions: when the last track of the folder finishes, it seamlessly decrements the folder iteration count and cycles back to the start.
  * Real-time countdown on the Mini Player and Now Playing screen (e.g. *"Folder Loop: 3 loops remaining"*).

### 2. 🎛️ Folder "Start to Stop" Track Sequence Chooser
* **Concept:** Precision sub-range playback within a folder.
* **Details:**
  * Let the user pick a **Start Track** and a **Stop Track** (e.g. *"Play from Song #3 and stop automatically after Song #7 finishes"*).
  * Ideal for rehearsal sets, dance choreography segments, study units, or specific album movements without needing to manually build a temporary folder.

### 3. ⏱️ Enhanced Sleep & Practice Timer Experience
* **Concept:** Redesigned, visual, and tactile timer interface.
* **Details:**
  * Modern radial dial / smooth tactile slider with quick presets (`15m`, `30m`, `45m`, `1h`, `Custom`).
  * Live countdown timer display with a soothing visual ring.
  * Smooth fade-out option (`Fade audio out over last 30 seconds` so music doesn't cut off abruptly when falling asleep).

### 4. 📲 The 3 Distinct Sharing Systems (Clearly Separated & Named)
* **Concept:** Clearly differentiate the 3 distinct ways users share in Tuny Music so they never get confused:

  * **1. Universal Android System Share ("Share via App"):**
    * The standard Android native system share sheet (`Intent.ACTION_SEND` / `ACTION_SEND_MULTIPLE`).
    * Lets the user send audio files directly through **WhatsApp, Telegram, Gmail, Bluetooth, Google Drive, Quick Share, Discord, or SMS**.
    * Supports sharing single songs, multiple selected songs, or audio clips with third-party apps outside of Tuny Music.

  * **2. Tuny-to-Tuny Direct Wi-Fi Share ("Tuny Beam" / "Fast Offline Transfer"):**
    * High-speed, 100% offline device-to-device file transfer built specifically **from one Tuny Music app to another Tuny Music app**.
    * Transfer individual songs or **entire playlists & folders** with folder structures preserved intact.
    * Uses local Wi-Fi / Hotspot with QR pairing and automatic reception so songs land directly into the recipient's Tuny Music library without needing WhatsApp or internet data.

  * **3. Dual Listen Party ("Live Synced Audio Stream"):**
    * Real-time synchronized playback broadcast (listen together at the exact same millisecond).
    * One phone is the Host/DJ; other phones join via QR scan to listen simultaneously without file downloading.

---

## 🎨 Tier 2: Appearance & Sensory Delight (Paid / Pro Enhancements)

Features designed to make users enjoy spending time inside the app just watching, feeling, and playing with sound:

### 1. 🎵 Interactive Audio Visualizer Styles (Now Playing)
* **Cyber Neon Frequency Bars:** Jumping neon equalizer bars reactive to audio playback.
* **Breathing Glow Ring:** Ambient glowing halo that pulses organically with the rhythm of the track.
* **Minimalist Vinyl Spinner:** Smoothly rotates the album artwork like an authentic turntable with a subtle needle effect.

### 2. 🎛️ Tactile Haptics & Sensory Mechanical Feedback
* **Mechanical Dial Clicks:** High-precision haptic feedback when rotating repeat counters, moving sliders, and toggling playback modes (simulating a high-end luxury audio receiver).
* Optional subtle mechanical sound effects for physical satisfaction.

### 3. 🎨 Exclusive Premium Aesthetic Themes
* **OLED Pure Midnight:** True pitch-black (#000000) backgrounds paired with high-contrast neon accents (saves battery on OLED screens).
* **Retro Synthwave / Cyberpunk 1984:** Deep ultraviolet background with hot magenta and electric cyan highlights.
* **Lo-Fi Chill & Warmth:** Soft amber, warm paper, and cozy muted palettes for evening listening and study sessions.
* **Emerald Glass / Matrix:** Translucent glassy cards with cyber emerald accents.

### 4. 🎚️ Tape Speed & Pitch Shifting Laboratory
* Dynamic speed & pitch control slider (`0.5x` to `2.0x`).
* Real-time tempo adjustment without distortion, or full pitch shift ("Slowed & Reverb" / "Nightcore" modes).
* Practice tool for musicians transcribing solos or dancers adjusting choreography tempo.

### 5. 🌊 Dynamic Ambient Glow (Album-Color Synced)
* Automatically extracts dominant colors from the current album art and renders a soft, shifting ambient aurora in the Now Playing and Mini Player backgrounds.

---

## 💎 Monetization & Paid / Pro Architecture

| Feature | Free Tier | Tuny Music Pro |
|---|---|---|
| Single Song Loop Repetition | Full Access | Full Access |
| Hardware Ringtone Trimmer | Full Access | Full Access |
| Zero-Duplicate Custom Folders | Full Access | Full Access |
| Universal Android Share (WhatsApp/Email) | Full Access | Full Access |
| Tuny-to-Tuny Fast Share (Songs) | Full Access | Full Access |
| **Tuny-to-Tuny Folder & Playlist Share** | — | **Full Access** |
| **Universal Folder Loop** | — | **Unlimited** |
| **Start-to-Stop Track Range** | — | **Unlimited** |
| **Dual Listen Synced Party** | — | **Full Access** |
| **Advanced Visualizer Skins** | Basic | **All Skins (Vinyl, Bars, Halo)** |
| **Exclusive Pro Themes** | Default Light/Dark | **OLED Midnight, Synthwave, Lo-Fi, Emerald** |
| **Pitch & Speed Lab** | — | **Full Access (0.5x – 2.0x)** |
| **Enhanced Sleep Timer with Fade** | Basic Timer | **Radial Dial + 30s Audio Fade-out** |

---
*Documented and saved for future implementation by Eert Labs.*
