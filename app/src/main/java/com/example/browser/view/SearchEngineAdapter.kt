package com.example.browser.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.databinding.ItemSearchEngineDialogBinding
import mozilla.components.browser.state.search.SearchEngine

/**
 * 搜索引擎选择 Adapter
 */
class SearchEngineAdapter(
    private val engines: List<SearchEngine>,
    private var selectedEngineId: String,
    private val onEngineSelected: (SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchEngineDialogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(engines[position], engines[position].id == selectedEngineId)
    }

    override fun getItemCount(): Int = engines.size

    fun updateSelection(engineId: String) {
        val oldPosition = engines.indexOfFirst { it.id == selectedEngineId }
        val newPosition = engines.indexOfFirst { it.id == engineId }
        
        selectedEngineId = engineId
        
        if (oldPosition != -1) {
            notifyItemChanged(oldPosition)
        }
        if (newPosition != -1) {
            notifyItemChanged(newPosition)
        }
    }

    inner class ViewHolder(
        private val binding: ItemSearchEngineDialogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(engine: SearchEngine, isSelected: Boolean) {
            binding.tvEngineName.text = engine.name
            
            // 设置图标
            engine.icon?.let {
                binding.ivEngineIcon.setImageBitmap(it)
            }
            
            // 设置选中状态
            binding.checkbox.isChecked = isSelected
            
            // 点击事件
            binding.root.setOnClickListener {
                onEngineSelected(engine)
            }
            
            binding.checkbox.setOnClickListener {
                onEngineSelected(engine)
            }
        }
    }
}
