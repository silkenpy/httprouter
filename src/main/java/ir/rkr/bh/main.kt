package ir.rkr.bh


import com.typesafe.config.ConfigFactory
import ir.rkr.bh.rest.JettyRestServer
import mu.KotlinLogging

const val version = 0.1

/**
 * CacheService main entry point.
 */
fun main(args: Array<String>) {
    val logger = KotlinLogging.logger {}
    val config = ConfigFactory.defaultApplication()

    JettyRestServer(config)
    logger.info { "BH V$version is ready :D" }

}
