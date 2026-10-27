package fs

/** What [copyTree] does with a symlink inside the source. */
enum class Links {
    /** Copy what the link points at, so the copy stands alone (a plugin shipped as a tree). */
    Follow,

    /** Recreate the link as it is (an app bundle whose links are part of it). */
    Keep,

    /** Leave the link out: an unpacked archive whose links could point out of the tree. */
    Skip,
}
