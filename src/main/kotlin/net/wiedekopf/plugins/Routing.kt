package net.wiedekopf.plugins

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import kotlinx.html.stream.appendHTML
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.mamoe.yamlkt.Yaml
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.notExists
import kotlin.io.path.readText

fun Application.configureRouting(feedConfigLocation: Path) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
        }
    }
    val feeds = readFeeds(feedConfigLocation)
    routing {
        feeds.groupBy { it.sanitizedPath }.forEach { (path, feeds) ->
            route(path) {
                environment?.log?.info("Configuring route '$path'")
                feeds.forEach { feed ->
                    environment?.log?.info("Configuring feed ${feed.fullPath}")
                    get(feed.name) {
                        val (responseBody, responseStatus) = this@configureRouting.respond(feed)
                        if (responseBody == null) {
                            call.respond(responseStatus)
                            return@get
                        }
                        call.respond(responseBody)
                    }
                }
            }
        }

        get("/") {
            call.respondText(ContentType.Text.Html) {
                buildString {
                    appendHTML(true).html {
                        head {
                            title("Feeds")
                            style(type = "text/css") {
                                unsafe {
                                    """
                                    * {
                                        font-family: sans-serif;
                                    }
                                    """.trimIndent()
                                }
                            }
                        }
                        body {
                            h1 { +"Feeds" }
                            ul {
                                feeds.forEach { feed ->
                                    li {
                                        a(href = feed.fullPath) {
                                            +feed.fullPath
                                        }
                                        if (feed.comment != null) {
                                            i {
                                                +" (${feed.comment})"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

suspend fun Application.respond(feed: Feed): Pair<String?, HttpStatusCode> {
    val client = HttpClient(CIO)
    val authorizationHeader = when (feed.authType) {
        null -> null
        else -> feed.authorizationHeader
    }
    val upstreamAsUrl = Url(feed.upstream)
    val response = client.get(upstreamAsUrl) {
        headers {
            if (authorizationHeader != null) {
                append(HttpHeaders.Authorization, authorizationHeader)
            }
            append(HttpHeaders.Accept, "*/*")
        }
    }
    environment.log.info("Response from $upstreamAsUrl: ${response.status}")
    if (response.status != HttpStatusCode.OK) {
        return null to response.status
    }
    return response.bodyAsText() to HttpStatusCode.OK
}

fun readFeeds(feedFile: Path): List<Feed> {
    val yaml = Yaml.decodeFromString(FeedList.serializer(), feedFile.readText())
    return yaml.feeds
}

@Serializable
data class FeedList(
    val feeds: List<Feed>
)

@Serializable
data class Feed(
    val name: String,
    val path: String = "/",
    val upstream: String,
    val authType: AuthType? = null,
    val usernameFile: String? = null,
    val passwordFile: String? = null,
    val tokenFile: String? = null,
    val tokenPrefix: String? = "Bearer",
    val comment: String? = null
) {

    val sanitizedPath
        get() = when (path) {
            "", "/" -> "/"
            else -> "/${path.trimStart('/').trimEnd('/')}/"
        }

    val fullPath get() = "$sanitizedPath${name.trimStart('/').trimEnd('/')}"

    @Transient
    val authorizationHeader: String? = buildAuthorizationHeader()

    private fun buildAuthorizationHeader(): String? = when (this.authType) {
        null -> null
        AuthType.Basic -> {
            if (Path(this.usernameFile!!).notExists()) {
                throw Exception("Username file ${this.usernameFile} does not exist")
            }
            if (Path(this.passwordFile!!).notExists()) {
                throw Exception("Password file ${this.passwordFile} does not exist")
            }
            val username = Path(this.usernameFile).readText()
            val password = Path(this.passwordFile).readText()
            "Basic ${Base64.getEncoder().encodeToString("$username:$password".toByteArray())}"
        }

        AuthType.Token -> {
            val token = Path(this.tokenFile!!).readText()
            "$tokenPrefix $token"
        }
    }

    enum class AuthType {
        Basic,
        Token
    }
}
