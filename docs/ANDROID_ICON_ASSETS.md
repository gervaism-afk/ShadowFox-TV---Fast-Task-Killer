# ShadowFox Ultra Cleaner launcher artwork

The launcher uses separately recreated square/round designs, a transparent adaptive foreground, an opaque blue/green background, and a borderless transparent 16:9 TV banner. All artwork is raster PNG, not vector. This update does not alter the app's application ID, signing configuration, version, updater or other runtime behavior.

`ic_launcher` resolves to legacy PNGs before Android 8 and the foreground/background adaptive XML on Android 8+. `ic_launcher_round` provides the matching round artwork. Legacy icon densities are 80/120/160/240/320px for TV readability; Android launchers scale them to their icon cell. Adaptive layers are 108/162/216/324/432px (108dp). Banners are 160x90/240x135/320x180/480x270/640x360px. The application label is exactly **ShadowFox Ultra Cleaner**.

All density exports were checked against the reviewed asset package. PNG alpha is real; no checkerboard is embedded. The visible adaptive logo is inside the central 66dp safe circle; faint decorative glow may extend into the effects area. Small launcher cells prioritize fox/color identity; the full subtitle is best read in the banner.

The new PNGs are independent designs, not cropped from a composite. They follow the approved reference but are not pixel-identical reproductions. Replacing source resources takes effect only in a newly built and installed APK. Existing installations do not receive icon files remotely.
