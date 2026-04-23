package com.example.browser.ui.junk

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.data.process.RunningAppInfo
import com.example.browser.databinding.ItemProcessAppBinding

/**
 * 进程列表适配器
 */
class ProcessAdapter(
    private val onStopClick: (RunningAppInfo, Int) -> Unit
) : RecyclerView.Adapter<ProcessAdapter.ViewHolder>() {
    
    private val apps = mutableListOf<RunningAppInfo>()
    
    fun setData(newApps: List<RunningAppInfo>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }
    
    fun removeItem(position: Int) {
        if (position in apps.indices) {
            apps.removeAt(position)
            notifyItemRemoved(position)
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProcessAppBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(apps[position])
    }
    
    override fun getItemCount(): Int = apps.size
    
    inner class ViewHolder(
        private val binding: ItemProcessAppBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(app: RunningAppInfo) {
            binding.ivAppIcon.setImageDrawable(app.icon)
            binding.tvAppName.text = app.appName
            
            binding.btnStop.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onStopClick(app, position)
                }
            }
        }
    }
}
