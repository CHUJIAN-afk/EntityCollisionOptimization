## 1.21.1.3

- Lower the MixinExtras push wrapper's priority so other mods' outer wrappers can filter push targets.
- Keep the fallback through `Operation.call` to preserve the wrapper chain.
- Add seven in-world GameTests, including a regression test for another wrapper filtering targets at the same push-query call site.
- Add automated client startup and title-screen rendering verification. MixinExtras is provided by NeoForge.

Verified with seven in-world GameTests and a real client rendering the title screen for at least ten seconds on a separate background desktop.
