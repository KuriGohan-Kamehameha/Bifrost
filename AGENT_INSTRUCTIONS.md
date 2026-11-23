This document contains instructions for autonomous coding agents. 

1. Use openjdk@17 when building
2. adhere to NASA coding standards
3. ensure that this application consumes minimal overhead
  a. prioritize stability and performance over feature-richness
4. ensure that lessons learned and regressions to be avoided are added to this document
5. preserve divergent material when merging from upstream
6. 
  a. do not pollute or deface upstream repo
  b. be considerate when submitting changes and ensure there are minimal conflicts
  c. ensure that material specific to this fork (like readme differences and identifiers) are not submitted upstream
7. theme settings that are user-facing, especially selected theme and coloured-logo state, must remain transferable independently of full backups so theme iteration does not require whole-app restore flows
8. external API lessons
  a. inside ExternalApiReceiver.onReceive, Binder.getCallingUid() is Bifrost's own uid, not the sender's: never key permissions on it. A verified caller comes only from Android 14 share-identity or the creator of a caller-supplied PendingIntent (EXTRA_CALLER_TOKEN)
  b. keep v1 behaviour byte-for-byte for apiVersion = 1 callers (existing callers treat -3 as "this effect is not available here"); new behaviour and result codes are gated on apiVersion >= 2
  c. an external override must never start a capture effect Bifrost can't feed (no live capture session or unused consent grant, no RECORD_AUDIO for audio effects, or no accessibility service when that is the chosen ambient path): refuse with -8 instead of starting a dark override
