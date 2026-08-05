# Aggregate-versus-standalone comparison tooling

`component_sync.py` calculates deterministic manifests and compares an aggregate external-component directory with a checkout of its standalone repository. It is read-only.

```bash
python tools/component-sync/component_sync.py manifest components/enthusia-currency --revision <aggregate-main-sha>
python tools/component-sync/component_sync.py compare \
  components/enthusia-currency /path/to/EnthusiaCurrency \
  --aggregate-sha <aggregate-main-sha> --standalone-sha <standalone-main-sha>
```

The normalized hash is SHA-256 over sorted POSIX paths, path lengths, byte lengths, and raw bytes. `.git` is ignored and aggregate-only `COMPONENT-METADATA.md` is excluded from product parity. No user-controlled allowlist or arbitrary exclusion option exists.

The tool refuses to report parity when it sees symlinks or generated/private/runtime/build/cache/log/database/secret/package artifacts. It reports files added to the aggregate, missing from the aggregate, and modified on either side, while recording both revisions and hashes.

The tool never pushes, deletes, merges, force-pushes, rewrites history, creates permanent branches, or chooses a winner when repositories diverge.
