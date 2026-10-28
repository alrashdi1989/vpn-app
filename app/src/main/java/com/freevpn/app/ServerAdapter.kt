package com.freevpn.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class ServerAdapter(
    private val onClick: (Server) -> Unit,
) : RecyclerView.Adapter<ServerAdapter.Holder>() {

    private var items: List<Server> = emptyList()

    fun submit(list: List<Server>) {
        items = list
        notifyDataSetChanged()
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val flag: TextView = view.findViewById(R.id.flag)
        val country: TextView = view.findViewById(R.id.country)
        val host: TextView = view.findViewById(R.id.host)
        val stats: TextView = view.findViewById(R.id.stats)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_server, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val s = items[position]
        holder.flag.text = s.flag
        holder.country.text = s.country
        holder.host.text = s.ip
        val ping = if (s.pingMs >= 0) "${s.pingMs} ms" else "– ms"
        val speed = String.format(Locale.US, "%.1f Mbps", s.speedMbps)
        holder.stats.text = holder.itemView.context.getString(
            R.string.server_stats, ping, speed, s.sessions
        )
        holder.itemView.setOnClickListener { onClick(s) }
    }
}
