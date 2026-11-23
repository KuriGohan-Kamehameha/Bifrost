# LED plugins

In Settings, select **Open Plugin Store**, choose a pack, and tap **Install**. A pack adds named LED presets and can map Android package names to them. Enable app profiles for automatic switching. A companion app that streams live colours also needs **Allow third-party LED control** in Bifrost. Installing a preset alone cannot produce game telemetry.

| Pack | What to expect |
| --- | --- |
| Fallout 4 Pip-Boy | A green phosphor preset for the Fallout companion. A compatible patched sender supplies live HUD changes. The optional screen-mirror checkbox uses Bifrost accessibility capture and consumes more power. |
| Zelda Android | Purah Pad and Sheikah Slate presets for the compatible native TotK and BotW ports. The catalogue entry lists the companion downloads. |

**Refresh catalogue** fetches the current list. Enable **Check for plugin updates at launch** for update notices. Open the store to apply available updates. Obtainium tracks the Bifrost APK separately. If installation reports a preset name conflict, export or rename the conflicting personal preset before retrying.

## Publish a pack

1. Check out the [plugin-catalog branch](https://github.com/KuriGohan-Kamehameha/Bifrost/tree/plugin-catalog) in a separate worktree. Copy an existing plugin directory and give it a unique ID.
2. Edit the root bundle manifest, following the [Zelda example](https://github.com/KuriGohan-Kamehameha/Bifrost/blob/plugin-catalog/plugins/zelda-android/manifest.json). Use unique preset names and the exact Android package IDs for app mappings. Live policies are keyed by effect name.
3. Run `bash build-bundle.sh plugins/YOUR-ID 1` from the catalogue root. Inspect the ZIP entries with `unzip -l` and confirm every referenced icon is included.
4. Add an entry to `catalog.json` with the versioned HTTPS bundle URL, the printed SHA-256 digest, integer version and minimum Bifrost version code. For updates increment the integer and publish a new filename so older entries still identify their exact bytes.
5. Test installation, an update over the previous version, preset switching and removal. To add live events from an Android app, follow the [sender API guide](INTEGRATING.md).

The catalogue is [available as JSON](https://raw.githubusercontent.com/KuriGohan-Kamehameha/Bifrost/plugin-catalog/catalog.json). The client verifies the downloaded bundle against its catalogue digest before importing it. Choose a catalogue publisher you trust.
