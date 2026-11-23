package process

/** Rust `std::env::set_var`: [key] set to [value] for this process and the children it starts. */
expect fun setEnv(
    key: String,
    value: String,
)

/** Rust `std::env::remove_var`. */
expect fun unsetEnv(key: String)
