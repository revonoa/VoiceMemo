package com.dalek.voicememo.ui

import com.dalek.voicememo.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dalek.voicememo.data.ReminderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReminderAdapter : ListAdapter<ReminderEntity, ReminderAdapter.VH>(DIFF){
    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.tvItemTitle)
        val time: TextView = itemView.findViewById(R.id.tvItemTime)
        val body: TextView = itemView.findViewById(R.id.tvItemBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reminder, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.body.text = item.body

        holder.time.text = item.remindAtMillis?.let { "⏰ " + format(it) } ?: "메모"
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ReminderEntity>() {
            override fun areItemsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ReminderEntity, newItem: ReminderEntity) =
                oldItem == newItem
        }

        private fun format(millis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            return sdf.format(Date(millis))
        }
    }
}