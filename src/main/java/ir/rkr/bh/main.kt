package ir.rkr.bh



import com.typesafe.config.ConfigFactory
import ir.rkr.bh.rest.JettyRestServer
import mu.KotlinLogging
import com.sun.corba.se.spi.presentation.rmi.StubAdapter.request



const val version = 0.1

/**
 * CacheService main entry point.
 */
fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    val config = ConfigFactory.defaultApplication()

    JettyRestServer(config)
//    val client = OkHttpClient()
//
//    val request = Request.Builder()
//            .url("http://localhost:7070/aldddiii5555iii29")
//            .build()
//    val response = client.newCall(request).execute()
//
//    println(response.body().string())
    logger.info { "HttpRouter V$version is ready :D" }

}
