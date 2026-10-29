package com.freevpn.app

import android.util.Base64
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Server(
    val hostName: String,
    val ip: String,
    val score: Long,
    val pingMs: Int,
    val speedBps: Long,
    val country: String,
    val countryCode: String,
    val sessions: Int,
    val config: String,
) {
    val speedMbps: Double get() = speedBps / 1_000_000.0

    /** Turns a 2-letter country code like "JP" into its flag emoji. */
    val flag: String
        get() {
            val cc = countryCode.uppercase()
            if (cc.length != 2 || cc.any { it !in 'A'..'Z' }) return "🌐"
            return cc.map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        }
}

/**
 * Fetches the public server list from VPN Gate (a free academic VPN project
 * run by the University of Tsukuba, Japan): https://www.vpngate.net
 */
object VpnGateApi {
    // GitHub copies first (refreshed hourly by .github/workflows/servers.yml),
    // because vpngate.net itself is blocked on many networks.
    private val URLS = listOf(
        "https://raw.githubusercontent.com/alrashdi1989/vpn-app/servers/servers.csv",
        "https://cdn.jsdelivr.net/gh/alrashdi1989/vpn-app@servers/servers.csv",
        "https://www.vpngate.net/api/iphone/",
        "http://www.vpngate.net/api/iphone/",
    )

    private const val CACHE_FILE = "servers_cache.csv"

    class Result(val servers: List<Server>, val fromCache: Boolean)

    fun fetchServers(cacheDir: File, bundled: () -> String?): Result {
        val cache = File(cacheDir, CACHE_FILE)
        var firstError: Exception? = null
        for (url in URLS) {
            try {
                val csv = download(url)
                val servers = parse(csv)
                if (servers.isNotEmpty()) {
                    try { cache.writeText(csv) } catch (_: Exception) {}
                    return Result(servers, fromCache = false)
                }
            } catch (e: Exception) {
                if (firstError == null) firstError = e
            }
        }
        // Offline / everything blocked: last saved list, then the list built into the app
        try {
            if (cache.exists()) {
                val servers = parse(cache.readText())
                if (servers.isNotEmpty()) return Result(servers, fromCache = true)
            }
        } catch (_: Exception) {}
        try {
            val servers = bundled()?.let { parse(it) }.orEmpty()
            if (servers.isNotEmpty()) return Result(servers, fromCache = true)
        } catch (_: Exception) {}
        throw firstError ?: IOException("No servers returned")
    }

    private fun download(address: String): String {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 45_000
        conn.setRequestProperty("User-Agent", "FreeVPN-Android/1.0")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // CSV columns: HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,
    // NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,
    // OpenVPN_ConfigData_Base64
    private fun parse(csv: String): List<Server> =
        csv.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("*") && !it.startsWith("#") }
            .mapNotNull { line ->
                val f = line.split(",")
                if (f.size < 15) return@mapNotNull null
                val config = try {
                    String(Base64.decode(f.last(), Base64.DEFAULT), Charsets.UTF_8)
                } catch (e: Exception) {
                    return@mapNotNull null
                }
                if (!config.contains("remote ")) return@mapNotNull null
                Server(
                    hostName = f[0],
                    ip = f[1],
                    score = f[2].toLongOrNull() ?: 0,
                    pingMs = f[3].toIntOrNull() ?: -1,
                    speedBps = f[4].toLongOrNull() ?: 0,
                    country = f[5],
                    countryCode = f[6],
                    sessions = f[7].toIntOrNull() ?: 0,
                    config = config,
                )
            }
            .sortedByDescending { it.score }
            .toList()

    /** Makes older VPN Gate profiles work with newer OpenVPN (2.5+) clients. */
    fun compatibleConfig(server: Server): String {
        var cfg = server.config.replace("\r\n", "\n")
        if (!cfg.contains("data-ciphers")) {
            cfg = cfg.trimEnd() +
                "\n\n# Added by FreeVPN for OpenVPN 2.5+ compatibility\n" +
                "data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC\n" +
                "data-ciphers-fallback AES-128-CBC\n"
        }
        return cfg
    }
}
