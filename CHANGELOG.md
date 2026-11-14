## 1.20.1.3

- Replace the push-query `@Redirect` with MixinExtras `@WrapOperation`.
- Lower the push wrapper's priority so other mods' outer wrappers can filter push targets, and preserve the wrapper chain when falling back.
- Bundle MixinExtras 0.5.3 in the Forge jar; no separate installation is required.
- Add a regression test for another wrapper filtering targets at the same push-query call site.

Verified with seven in-world GameTests and a real client rendering the title screen for at least ten seconds on a separate background desktop.
