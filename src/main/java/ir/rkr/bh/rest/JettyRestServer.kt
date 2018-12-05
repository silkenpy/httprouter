package ir.rkr.bh.rest

import com.google.common.hash.Hashing
import com.google.gson.GsonBuilder
import com.typesafe.config.Config
import ir.rkr.bh.version
import org.eclipse.jetty.http.HttpStatus
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.thread.QueuedThreadPool
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 * [JettyRestServer] is a rest-based service to handle requests of redis cluster with an additional
 * in-memory cache layer based on ignite to increase performance and decrease number of requests of
 * redis cluster.
 */
class JettyRestServer(val config: Config) : HttpServlet() {

    private val gson = GsonBuilder().disableHtmlEscaping().create()
    private val murmur = Hashing.murmur3_32(1)

    /**
     * Start a jetty server.
     */
    init {

        val shards = config.getStringList("rest.shards")
        val step = (65535 * 2 + 1) / shards.size


        val threadPool = QueuedThreadPool(500, 20)
        val server = Server(threadPool)
        val http = ServerConnector(server).apply { port = config.getInt("rest.port") }
        server.addConnector(http)

        val handler = ServletContextHandler(server, "/")

        /**
         * It can handle multi-get requests for Urls in json format.
         */

        handler.addServlet(ServletHolder(object : HttpServlet() {

            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
                val salt = murmur.hashBytes(req.pathInfo.toByteArray()).asInt() % 65535

                val shardNum = (salt + 65535) / step

                println(shards[shardNum])


                resp.apply {
                    status = HttpStatus.SEE_OTHER_303

                    setHeader("Location", "http://${shards[shardNum]}/${req.pathInfo}")
//                    setHeader("Location", "http://localhost:7070/ali")

                }
            }

        }), "/*")


        handler.addServlet(ServletHolder(object : HttpServlet() {

            override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {

                resp.apply {
                    status = HttpStatus.OK_200
                    addHeader("Content-Type", "application/json; charset=utf-8")
                    //addHeader("Connection", "close")
                    writer.write(gson.toJson("ali 29"))
                }
            }

        }), "/ali")

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