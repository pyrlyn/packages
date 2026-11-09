# Roadmap

Approved, not yet in `plan/`:

- `files` module: `FileStore`, the column-folder board layout from rtok T8.1 (front matter, atomic writes, flock on
  `.lock`, `.state/` files, the `txn.json` redo log), moved out of rtok. Race tests open separate stores on one
  directory from several threads.
- `rpc` module: the JSON-RPC 2.0 method table, a transport-free dispatcher, a Kotlin client and the OpenRPC document.
- `sync` module: the sync engine and the `Remote` interface.
- `github`, `gitlab`, `linear` and `jira` modules: the trackers, over `kotlin-kit`'s `http`.
