# Vendored native libraries

## libmolly-wipe.so

Source: https://github.com/mollyim/ndkutil (artifact `im.molly:native-utils:1.0.0`).

License: GPLv3. The library exposes JNI entry points for `WipeMemoryService` (`allocPages`, `freePages`,
`wipePage`, `getPageSize`). It is renamed from `libnative-utils.so` to avoid colliding with Signal's own
`core/util` `libnative-utils.so`.

The binaries are unmodified apart from the file name and are checked in because the app has no native build
configuration of its own for this module.
