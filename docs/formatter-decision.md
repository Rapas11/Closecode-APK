# Format Code: ktlint investigation and decision

**Date:** 2026-10-02
**Status:** decided, not deferred

## Decision

**Closecode Android does not depend on ktlint 1.5.0.** The existing Analysis API and
compiler environment remain unchanged. Format Code is implemented independently and verified
by re-parsing before any change is applied.

No ktlint artifacts are vendored into `app/libs/`. No `kotlin-compiler-embeddable` version
override is introduced. No second Kotlin compiler is shipped.

## 1. Why ktlint 1.5.0 was investigated

Closecode needs a Format Code action. ktlint is the standard Kotlin formatter and is used
throughout the Android ecosystem, so it was the obvious candidate rather than a custom
implementation. The investigation ran before any dependency change was made.

The question was narrow: can ktlint 1.5.0 be added without disturbing the Kotlin Analysis
API that provides diagnostics, completion and quick fixes?

## 2. Its compiler dependency chain

Resolved from the published POMs on Maven Central. The compile/runtime closure is a single
linear chain, not a tree:

```
com.pinterest.ktlint:ktlint-ruleset-standard:1.5.0
└── com.pinterest.ktlint:ktlint-cli-ruleset-core:1.5.0
    └── com.pinterest.ktlint:ktlint-rule-engine-core:1.5.0
        ├── org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.0   (compile)
        ├── org.ec4j.core:ec4j-core:1.1.0                            (compile)
        ├── org.jetbrains.kotlin:kotlin-stdlib:2.1.0                (compile)
        ├── com.pinterest.ktlint:ktlint-logger:1.5.0                  (runtime)
        └── dev.drewhamilton.poko:poko-annotations-jvm:0.18.0        (runtime)
```

ktlint reaches the Kotlin compiler through `ktlint-rule-engine-core` at `compile` scope.
There is no optional edge to cut, so no `exclude` can yield a working formatter without the
compiler.

Two observations from the resolution work:

- ktlint 1.5.0 no longer depends on IntelliJ PSI. A previous assumption that it required a
  separate PSI module was wrong; `ktlint-rule-engine-psi` returns HTTP 404 for 1.5.0 because
  it was removed, not because it is missing. This is why ktlint was viable at all.
- The required `kotlin-compiler-embeddable:2.1.0` is not in the local Gradle cache. Cached
  versions are 1.9.24, 2.0.0, 2.2.21, 2.3.20 and 2.4.0. There is a gap at 2.1.0.

## 3. Why Gradle-level isolation is not viable

Gradle resolves exactly one version of a given module per configuration. Both ktlint and the
Analysis API would resolve `kotlin-compiler-embeddable` from the same module coordinate, so
the version chosen for ktlint is the version the Analysis API gets. Highest-wins conflict
resolution means the Analysis API's 2.4.x requirement would override ktlint's 2.1.0.

Forcing a single version to satisfy ktlint is precisely the global compiler bump that was
ruled out. There is no configuration that gives ktlint 2.1.0 while the Analysis API keeps its
own compiler.

## 4. Why runtime and classloader isolation is not appropriate

A `DexClassLoader` or a separate process only helps if both compiler versions are present in
the APK. They cannot be: one module coordinate yields one dexed version.

Making it work would require vendoring a second 60 MB `kotlin-compiler-embeddable` jar
specifically to populate a sandbox classloader, producing an APK of roughly 170 MB with two
copies of the same library. ktlint's path to the compiler also runs through shared
`kotlin-stdlib` internals, so the boundary would need that duplicated too.

Shipping duplicated runtime libraries to satisfy one formatting button is not an appropriate
trade for this application.

## 5. Why a Gradle Worker does not solve the problem

A Gradle Worker is a JVM-side build tool. It has no runtime presence in the shipped APK.

`androidx.work` is the Android equivalent and is a good fit for moving analysis off the main
thread. It isolates threads, schedules work, and can restart a process. It does not isolate
class versions. Work runs in the same process and the same classloader as the rest of the app,
so it cannot load two incompatible versions of the same class.

## 6. Why changing the Analysis API compiler is unacceptable

The Analysis API is the foundation of the editor's real language features: real diagnostics,
scope-based completion, member access on `.`, and compiler-provided quick fixes. It is the
single most valuable capability in this codebase.

Changing its compiler version to accommodate a formatter risks that capability silently for
the sake of one button. The dependency priority is diagnostics and completion over formatting,
and the cost asymmetry is severe: losing diagnostics breaks the editor's core promise, while
losing Format Code removes one action from a menu.

## 7. Final decision

Format Code is implemented as a self-verifying formatter, independent of ktlint:

- Parsing uses Closecode's existing Kotlin parser infrastructure.
- Indentation, brace placement, trailing whitespace, blank-line normalisation, newline at
  end of file, and operator and comma spacing are handled.
- After transformation the result is re-parsed.
- A result that fails to parse is never returned as a success.
- On verification failure the original source is left untouched and the failure is reported.
- The formatter sits behind an interface so a different implementation can replace it later.

## 8. Limitations compared with ktlint

Stated plainly, because parity is not claimed:

- Not byte-identical to ktlint output. Ambiguous constructs may be formatted differently.
- No rule catalogue. ktlint implements a large body of stylistic and correctness rules, such
  as import ordering, no-wildcard-imports, and naming conventions. This implementation covers
  layout and spacing only.
- No `.editorconfig` support. ktlint reads configuration from that file; this implementation
  does not.
- No rule-level reporting or incremental lint output.

## 9. Conditions for reconsidering ktlint

ktlint becomes viable if any of these become true:

- A JVM, desktop or server target exists. Two compiler versions can coexist across separate
  processes there, so ktlint can run with its required 2.1.0 while Closecode's Analysis API
  keeps 2.4.x.
- A shading or relocation mechanism makes it possible to repackage one compiler version under
  a distinct package namespace, avoiding the coordinate collision entirely.
- The Analysis API is retired or replaced, freeing the `kotlin-compiler-embeddable`
  coordinate.
- Upstream ktlint ships a version whose compiler requirement aligns with the Analysis API's.

In any of these cases, ktlint 1.5.0 should be evaluated on its merits, with the dependency
change made explicitly and deliberately rather than by version resolution.