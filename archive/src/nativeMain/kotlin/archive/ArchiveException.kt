package archive

import kotlinx.io.IOException

/** A compressed stream or an archive that cannot be read; [kind] says why, the message says where. */
class ArchiveException(
    message: String,
    val kind: Kind,
) : IOException(message) {
    enum class Kind {
        /** The bytes break the format. */
        Corrupt,

        /** The input ends before the format does. */
        Truncated,

        /** Decoding would take more memory than the caller allowed. */
        MemoryLimit,

        /** Valid, but in a form this module does not read (an encrypted zip member, say). */
        Unsupported,
    }
}

internal fun corrupt(why: String): Nothing = throw ArchiveException(why, ArchiveException.Kind.Corrupt)

internal fun truncated(what: String): Nothing = throw ArchiveException("$what ends early", ArchiveException.Kind.Truncated)

internal fun unsupported(why: String): Nothing = throw ArchiveException(why, ArchiveException.Kind.Unsupported)
