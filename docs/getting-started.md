# Getting Started

## 1. Installation

<div style="display: flex; gap: 10px; align-items: center;">
    <a href="https://flathub.org/en/apps/io.voicestream.SpeedOfSound">
      <img width="240" alt="Get it on Flathub" src="https://flathub.org/api/badge?locale=en"/>
    </a>
    <a href="https://snapcraft.io/voicestream">
      <img width="260" alt="Get it from the Snap Store" src=https://snapcraft.io/en/dark/install.svg />
    </a>
</div>

The easiest and recommended way to install Nexiant Voice is from
[Flathub](https://flathub.org/en/apps/io.voicestream.SpeedOfSound) or from the
[Snap Store](https://snapcraft.io/voicestream).

Alternatively, AppImage, Deb, and RPM packages are also available from the
[releases page](https://nexiant.ai/releases/latest).

To build from source, see [`CONTRIBUTING.md`](https://nexiant.ai/blob/main/CONTRIBUTING.md).

## 2. Accept Remote Desktop Permissions

When Nexiant Voice is first launched, the main window displays a banner with a **Start** button.
Clicking it will prompt your system to grant the application the permissions it needs to type on your behalf.

![Main screen](assets/screenshots/main-banner-light.png#only-light)
![Main screen](assets/screenshots/main-banner-dark.png#only-dark)

After clicking **Start**, your system will show the Remote Desktop permissions dialog.
You must check **Allow remote interaction**, which is required for the application to simulate keyboard input.
You should also check **Remember this selection**. Without it, you will need to re-accept these permissions every
time you launch Nexiant Voice.

![Remote Desktop Permissions Dialog](assets/screenshots/remote-desktop-permissions-light.png#only-light)
![Remote Desktop Permissions Dialog](assets/screenshots/remote-desktop-permissions-dark.png#only-dark)

Under the hood, Nexiant Voice uses the [XDG Desktop Portal](https://flatpak.github.io/xdg-desktop-portal/) standard
to simulate keyboard input. This is supported by all major desktop environments, including GNOME and KDE,
and works on both X11 and Wayland.

## 3. Set up a shortcut

By default, the `Super+Z` keyboard shortcut starts and stops listening, but only when the Nexiant Voice window is
open and focused. For a better experience, we recommend setting up a global shortcut in Preferences. This lets you
keep the window minimized or hidden and trigger Nexiant Voice from anywhere, typing directly into any app.

Follow the instructions under [Set Up a Keyboard Shortcut](keyboard-shortcut.md).
