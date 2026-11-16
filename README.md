SECURE LOCK
===========

PACKAGE:
com.securelock.app

TARGET:
Android 14 / Samsung One UI

LANGUAGE:
Kotlin

ROOT:
NOT REQUIRED.

LSPOSED:
NOT REQUIRED.

BUGJAEGER:
Used for ADB Device Owner provisioning.

FEATURES
--------

NORMAL MODE
- Shared PIN.
- Shared pattern.
- Automatic authentication.
- Five-minute break.
- Ten-minute break.
- Three failed attempts before escalating cooldown.

VIP MODE
- Separate VIP PIN per app.
- Normal and VIP credentials can both be required.
- No break when VIP is active.

STRICT MODE
- Timed restriction.
- Countdown overlay.
- No credential field while active.
- Other rules remain after the strict period expires.
- Independent Strict Mode password required to activate.

SCHEDULED MODE
- Start time.
- End time.
- Days of week.
- Overnight schedules.

SECURITY
--------

- AES-GCM encrypted state.
- Android Keystore.
- PBKDF2-HMAC-SHA256.
- Accessibility foreground monitoring.
- Accessibility overlay lock screen.
- Failed-attempt cooldown.
- Device Admin.
- Device Owner uninstall protection.
- Date/time configuration restriction.
- Boot restoration.

BUGJAEGER SETUP
---------------

1. Install Secure Lock.
2. Open Bugjaeger.
3. Connect through Wireless Debugging.
4. Open ADB Shell.
5. Execute:

dpm set-device-owner com.securelock.app/.admin.SecureLockAdminReceiver

6. Return to Secure Lock.
7. Enable Accessibility service.
8. Enable Device Admin if requested.

DEVICE OWNER NOTES
------------------

Device Owner provisioning is subject to Android's device
provisioning requirements.

Device Owner is stronger than ordinary Device Admin.

This project blocks uninstall of only the Secure Lock package.

It does not globally disable removal of other applications.

DATE AND TIME
-------------

When Device Owner is active, Secure Lock applies:

UserManager.DISALLOW_CONFIG_DATE_TIME

It also requests automatic time and timezone.

APP INFO PROTECTION
-------------------

The Accessibility service detects the Secure Lock app details
page and attempts to navigate back.

Device Owner uninstall blocking is the independent protection
against removal.

IMPORTANT ANDROID LIMITATIONS
-----------------------------

Bugjaeger provides ADB shell access, not root access.

A normal third-party application cannot set FLAG_SECURE on
another application's window.

A normal third-party application cannot reliably delete another
application's Recent Apps task.

The lock overlay uses TYPE_ACCESSIBILITY_OVERLAY. Its own
window uses FLAG_SECURE. This does not automatically apply
FLAG_SECURE to the protected application's actual window.

The accessibility service detects reduced application window
bounds and attempts the Android split-screen toggle action
when the system exposes it. This is not a guaranteed system-level
per-application split-screen prohibition.

The code does not claim these unavailable capabilities.

BUILD
-----

Use Android Studio with JDK 17 and Android SDK 35.

Build the project and test on the target Samsung device.

The project has not been verified by an actual Android build
or a physical Samsung device test.
