package net.wiedekopf

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import net.wiedekopf.plugins.configureMonitoring
import net.wiedekopf.plugins.configureRouting
import org.slf4j.LoggerFactory
import java.io.FileNotFoundException
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.notExists

private const val DEFAULT_CONFIG_PATH = "/config/feeds.yaml"
private const val CONFIG_LOCATION_ENV_VAR = "FEED_CONFIG_LOCATION"
private const val PORT = 8123
private const val HOST = "0.0.0.0"


private val mainLogger = LoggerFactory.getLogger("rss-auth-proxy")
val feedConfigLocation = System.getenv(CONFIG_LOCATION_ENV_VAR).let { environmentConfigLocation ->
    when (environmentConfigLocation == null) {
        false -> {
            val path = Path(environmentConfigLocation)
            if (path.notExists()) {
                throw FileNotFoundException("The specified feed config file '$environmentConfigLocation' was not found.")
            }
            mainLogger.info("Using config file from '$environmentConfigLocation'")
            path
        }

        else -> {
            val path = Path(DEFAULT_CONFIG_PATH)
            mainLogger.info("Using default config file '$path'")
            if (path.notExists()) {
                throw FileNotFoundException(
                    "The default feed config file '$DEFAULT_CONFIG_PATH' was not found. " +
                            "Create it from the template file, or specify an alternate location " +
                            "with the environment variable '$CONFIG_LOCATION_ENV_VAR'."
                )
            }
            path
        }
    }
}

fun main() {
    embeddedServer(
        Netty,
        port = PORT,
        host = HOST,
        module = Application::module,
    ).start(wait = true)
}

fun Application.module() {
    configureMonitoring()
    configureRouting(feedConfigLocation)
}