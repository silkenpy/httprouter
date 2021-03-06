package ir.rkr.bh.rest

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.hash.Hashing
import com.google.common.util.concurrent.RateLimiter
import com.google.gson.GsonBuilder
import com.typesafe.config.Config
import ir.rkr.bh.utils.HttpRouterMetrics
import ir.rkr.bh.version
import mu.KotlinLogging
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.math.absoluteValue


/**
 * [JettyRestServer] is a rest-based service to handle requests of redis cluster with an additional
 * in-memory cache layer based on ignite to increase performance and decrease number of requests of
 * redis cluster.
 */
class JettyRestServer(val config: Config, val metrics: HttpRouterMetrics) : HttpServlet() {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    val logger = KotlinLogging.logger {}
    private val murmur = Hashing.murmur3_32(1)
    private val cache = CacheBuilder.newBuilder().maximumSize(config.getLong("rest.topsNum")).build<String,Int>(CacheLoader.from({_ -> 0}))
//    var checksum: Checksum = CRC32()

    /**
     *
     * Start a jetty server.
     */
    init {

        val rate = RateLimiter.create(config.getDouble("rest.rate"))

        val shards = config.getStringList("rest.shards")
        println("shard num ${shards.size}")
        val step = 65535 / shards.size

        val logIt = config.getBoolean("rest.log")

        val threadPool = QueuedThreadPool(config.getInt("rest.threads"), 20)
        val server = Server(threadPool)
        val http = ServerConnector(server).apply { port = config.getInt("rest.port") }
        server.addConnector(http)

        val handler = ServletContextHandler(server, "/")

        /**
         * It can handle multi-get requests for Urls in json format.
         */


        handler.addServlet(ServletHolder(object : HttpServlet() {

            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

                metrics.MarkRedirectRequest(1)

                if (!rate.tryAcquire()){
                    resp.apply {
                        status = HttpStatus.NOT_FOUND_404
                        addHeader("Connection", "close")
                    }
                }else {

                    cache.put(req.pathInfo, cache.get(req.pathInfo) + 1)
                    val salt = murmur.hashBytes(req.pathInfo.toByteArray()).asInt() % 65534

//                checksum.update(req.pathInfo.toByteArray(),0,req.pathInfo.length)
//                val salt = (checksum.value % 65535).toInt()

                    var shardNum = salt.absoluteValue / step
                    if (shardNum == shards.size) shardNum = 0

                    if (logIt)
                        logger.debug { "${shards[shardNum]}${req.pathInfo}" }

                    resp.apply {
                        status = HttpStatus.MOVED_PERMANENTLY_301

                        setHeader("Location", "${shards[shardNum]}${req.pathInfo}")
                        addHeader("Connection", "close")
                    }
                }
            }
        }), "/*")

        handler.addServlet(ServletHolder(object : HttpServlet() {

            override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {

                resp.apply {
                    status = HttpStatus.OK_200
                    addHeader("Content-Type", "application/json; charset=utf-8")
                    //addHeader("Connection", "close")
                    writer.write(gson.toJson(metrics.getInfo()))
                }
            }
        }), "/metrics")

        handler.addServlet(ServletHolder(object : HttpServlet() {

            override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {

                resp.apply {
                    status = HttpStatus.OK_200
                    addHeader("Content-Type", "application/json; charset=utf-8")
                    //addHeader("Connection", "close")
                    writer.write(gson.toJson(cache.asMap()))
                }
            }

        }), "/top")

        handler.addServlet(ServletHolder(object : HttpServlet() {
            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

                resp.apply {
                    status = HttpStatus.OK_200
                    addHeader("Content-Type", "text/plain; charset=utf-8")
                    addHeader("Connection", "close")
                    writer.write("server V$version is running :D")
                }
            }
        }), "/version")

        server.start()

    }
}