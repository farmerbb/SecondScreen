![SecondScreen](http://i.imgur.com/EkYuo7A.png "SecondScreen")

SecondScreen is an application designed for power users that frequently connect their Android devices to external displays. It works with your existing screen mirroring solution to give you the best experience possible. With SecondScreen, you can change your device's resolution and density to fit your TV or monitor, enable always-on desktop mode in Chrome, and even turn your device's backlight off, among several other features.

* **This app REQUIRES elevated permissions, granted via root access or adb shell commands. The app will do nothing if you do not have a rooted device or access to adb.**
* **This app is ONLY for devices with AOSP / Google experience ROMs. It is not guaranteed to function properly on devices with manufacturer-skinned ROMs.**
* **This app does not provide screen mirroring capabilities on its own. Screen mirroring may require either an MHL/SlimPort adapter or a wireless solution such as Miracast or Chromecast.**
* **A Bluetooth keyboard and mouse is strongly recommended as the app can make UI elements smaller and harder to press on the device itself.**

## Features
* Easily change resolution and density (DPI) - take full advantage of the resolution of your external display, and show the Android tablet interface if you're using a phone
* Simple profile-based interface - easy to enable/disable different profiles for different types of displays
* Many configurable options, including:
    * Automatically enable Bluetooth and Wi-Fi - quickly connect a keyboard, mouse, and/or game controller
    * Automatically enable Daydreams
    * Lock screen orientation to landscape
    * Show desktop sites in Chrome by default - browse the real Web, on your TV!
    * Overscan support for older TVs (Android 4.3+)
    * System-wide immersive mode (Android 5.0+)
    * Disable device backlight and vibration - save battery while your device is connected (not compatible with all devices)
* Full integration with Tasker
* Load profiles automatically when a display is connected
* Quick Actions - quickly and easily run SecondScreen features without creating or editing profiles
* Homescreen shortcuts - launch a profile with one tap, without entering the app

## Download
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play"
      height="80"
      align="middle">](https://play.google.com/store/apps/details?id=com.farmerbb.secondscreen.free)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80"
      align="middle">](https://f-droid.org/packages/com.farmerbb.secondscreen.free/)

## Explanation of permissions
* root access or adb shell commands required to change resolution/DPI, disable backlight/vibration, enable desktop-only mode in Chrome
* "connect and disconnect from Wi-Fi", "view Wi-Fi connections" - required for profiles to enable Wi-Fi
* "access Bluetooth settings", "pair with Bluetooth devices" - required for profiles to enable Bluetooth
* "close other apps" - required to refresh the user interface after a resolution/DPI change. Ensure all data is saved before launching a profile.
* "run at startup" - required to show SecondScreen profile notification after a (soft/hard) reboot
* "modify system settings" - required for profiles to lock rotation and set brightness

## Translation credits
* Braden Farmer (English)
* César Parga, Adrian Brown (Spanish)
* Christophe Romana (French)
* ja-som (Slovak)
* Heimen Stoffels (Dutch)
* Asbesbopispa (Italian)
* czheji (Chinese)
