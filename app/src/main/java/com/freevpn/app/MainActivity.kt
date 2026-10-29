package com.freevpn.app

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class MainActivity : AppCompatActivity() {

    private val adapter = ServerAdapter { showServerOptions(it) }
    private var allServers: List<Server> = emptyList()

    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var status: TextView
    private lateinit var search: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))

        swipe = findViewById(R.id.swipe)
        status = findViewById(R.id.status)
        search = findViewById(R.id.search)

        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        swipe.setColorSchemeColors(0xFF8FE3FF.toInt(), 0xFFC9A7FF.toInt())
        swipe.setProgressBackgroundColorSchemeColor(0xFF232A5C.toInt())
        swipe.setOnRefreshListener { loadServers() }
        search.doAfterTextChanged { applyFilter() }

        loadServers()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_refresh -> { loadServers(); true }
        R.id.action_help -> { showHelp(); true }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadServers() {
        swipe.isRefreshing = true
        status.text = getString(R.string.loading)
        Thread {
            try {
                val result = VpnGateApi.fetchServers(filesDir) {
                    try { assets.open("servers.csv").bufferedReader().use { it.readText() } } catch (e: Exception) { null }
                }
                runOnUiThread {
                    allServers = result.servers
                    swipe.isRefreshing = false
                    applyFilter()
                    if (result.fromCache) {
                        Toast.makeText(this, R.string.using_cache, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    swipe.isRefreshing = false
                    status.text = getString(R.string.load_error, e.message ?: e.javaClass.simpleName)
                }
            }
        }.start()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        val list = if (q.isEmpty()) allServers else allServers.filter {
            it.country.contains(q, ignoreCase = true) ||
                it.countryCode.equals(q, ignoreCase = true) ||
                it.ip.contains(q)
        }
        adapter.submit(list)
        if (allServers.isNotEmpty()) {
            status.text = getString(R.string.server_count, list.size)
        }
    }

    private fun showServerOptions(server: Server) {
        MaterialAlertDialogBuilder(this)
            .setTitle("${server.flag}  ${server.country}")
            .setMessage(getString(R.string.server_dialog_msg, server.ip))
            .setPositiveButton(R.string.connect) { _, _ -> openInVpnApp(server) }
            .setNeutralButton(R.string.save_file) { _, _ -> saveToDownloads(server) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun profileName(server: Server) =
        "vpngate-${server.countryCode.lowercase()}-${server.ip.replace('.', '-')}.ovpn"

    private fun openInVpnApp(server: Server) {
        val dir = File(cacheDir, "profiles").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, profileName(server))
        file.writeText(VpnGateApi.compatibleConfig(server))
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/x-openvpn-profile")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            askToInstallOpenVpn()
        }
    }

    private fun saveToDownloads(server: Server) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, profileName(server))
                put(MediaStore.Downloads.MIME_TYPE, "application/x-openvpn-profile")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create file")
            contentResolver.openOutputStream(uri)?.use {
                it.write(VpnGateApi.compatibleConfig(server).toByteArray())
            }
            Toast.makeText(this, getString(R.string.saved, profileName(server)), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.save_failed, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun askToInstallOpenVpn() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.need_openvpn_title)
            .setMessage(R.string.need_openvpn_msg)
            .setPositiveButton(R.string.install) { _, _ ->
                val id = "de.blinkt.openvpn"
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
                } catch (e: ActivityNotFoundException) {
                    startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$id"))
                    )
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHelp() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.help_title)
            .setMessage(R.string.help_msg)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
