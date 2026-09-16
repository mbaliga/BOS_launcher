# Privacy Policy — Sphere Launcher

> **This is the working copy, not the hosted one.** The URL Play Console points at
> is **https://asystemofcells.com/sphere/privacy**. Keep the two in sync by hand.

**Last updated: 29 August 2026**

Sphere Launcher is a home-screen replacement for Android (package `com.bos.sphere`),
made by A System of Cells. This policy describes exactly what the app does with
data. It is short because the app does very little.

## The short version

Sphere Launcher has no accounts, no advertising, no analytics and no tracking. We
operate no servers and receive nothing from the app.

One thing is worth stating plainly rather than hiding behind a permission list: the
app itself has no internet permission, but **voice search hands your speech to
Android's own speech recognition service**, which on most devices sends that audio to
Google to transcribe it. That happens outside this app, under Google's privacy
policy, and only while you are actively using voice search.

## What the app collects

**Nothing reaches us.** We operate no servers.

## Voice search

When you use voice search, your speech is passed to the speech recognition service
built into your device. On most Android devices that service transcribes in the
cloud, so the audio leaves your phone, goes to Google, and is handled under their
privacy policy rather than ours. We never receive it, and we keep no recording.

It happens only while you are actively speaking to the launcher. If you never use
voice search, no audio is captured at all.

## Your notifications

If you grant notification access, the launcher reads your notifications to build the
pull-down hub: the app they came from, their title and text, and any actions they
offer. That reading happens **on your device**, the content is never transmitted
anywhere, and nothing about it is stored beyond what the hub is currently showing.

Revoking notification access in Android's settings stops it immediately.

## Your app list

A launcher has to know which apps you have in order to launch them. Sphere Launcher
reads that list through Android's own launcher API, uses it to draw your home
screen and to answer your searches, and does nothing else with it.

The list is never transmitted, never catalogued for any purpose of ours, and never
shared with anyone.

## What is stored, and where

Your hidden-apps list, your surface settings (perspective, spacing, turn, depth,
scale, focus, arc, tilt), your row mode and your theme are stored in the app's
private storage on your device. Uninstalling the launcher deletes all of it.

## Permissions, and why each exists

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | To show you all of your apps, which is what a launcher is for. |
| `RECORD_AUDIO` | Optional. Only while you are using voice search. See above for where that audio goes. |
| Notification access | Optional. Only to build the pull-down hub, read on device. |

## Children

Sphere Launcher is not directed at children and collects no personal information
from anyone, including children.

## Changes

If this policy changes, the "Last updated" date above changes with it, and the
revised policy is published at this same URL.

## Contact

Sphere Launcher is made by **A System of Cells**. Questions about this policy or
the app: sphere@asystemofcells.com
