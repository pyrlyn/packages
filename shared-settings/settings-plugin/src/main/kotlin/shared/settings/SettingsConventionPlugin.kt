package shared.settings

import com.github.burrunan.s3cache.AwsS3BuildCache
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * Settings plugin. The consumer also declares `com.github.burrunan.s3-build-cache` (with version) in the
 * same settings `plugins {}` block.
 * - Repositories: mavenCentral + google for dependency resolution.
 * - Remote build cache (S3 API, e.g. Cloudflare R2): on only when both S3_BUILD_CACHE_ACCESS_KEY_ID and
 *   S3_BUILD_CACHE_SECRET_KEY are set (read by the s3 plugin, never from the repo). Settings come from
 *   `diffuser.remoteCache.<bucket|prefix|endpoint>` (fallback `rtok.remoteCache.*`) or env
 *   `DIFFUSER_REMOTE_CACHE_<BUCKET|PREFIX|ENDPOINT>` (fallback `RTOK_REMOTE_CACHE_*`). Prefix defaults to
 *   `<rootProject.name>/`. Pushes only when `CI` is set.
 */
class SettingsConventionPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val pv = settings.providers
        settings.dependencyResolutionManagement.repositories { mavenCentral(); google() }
        fun setting(name: String): String? =
            pv.gradleProperty("diffuser.remoteCache.$name").orNull
                ?: pv.gradleProperty("rtok.remoteCache.$name").orNull
                ?: pv.environmentVariable("DIFFUSER_REMOTE_CACHE_${name.uppercase()}").orNull
                ?: pv.environmentVariable("RTOK_REMOTE_CACHE_${name.uppercase()}").orNull
        val keys = listOf("S3_BUILD_CACHE_ACCESS_KEY_ID", "S3_BUILD_CACHE_SECRET_KEY")
            .all { !pv.environmentVariable(it).orNull.isNullOrEmpty() }
        if (!keys) return
        settings.buildCache.remote(AwsS3BuildCache::class.java) {
            region = "auto"
            isReducedRedundancy = false // R2 has no REDUCED_REDUNDANCY storage class
            bucket = requireNotNull(setting("bucket")) { "set diffuser.remoteCache.bucket or DIFFUSER_REMOTE_CACHE_BUCKET" }
            prefix = setting("prefix") ?: "${settings.rootProject.name}/"
            endpoint = requireNotNull(setting("endpoint")) { "set diffuser.remoteCache.endpoint or DIFFUSER_REMOTE_CACHE_ENDPOINT" }
            isPush = !pv.environmentVariable("CI").orNull.isNullOrEmpty()
        }
    }
}
