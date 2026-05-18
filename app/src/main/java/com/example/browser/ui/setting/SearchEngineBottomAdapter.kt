package com.example.browser.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.databinding.ItemSearchEngineBottomBinding
import mozilla.components.browser.state.search.SearchEngine

/**
 * 搜索引擎底部弹框列表适配器 - 精简版
 */
class SearchEngineBottomAdapter(
    private var selectedEngineId: String,
    private val onEngineSelected: (SearchEngine) -> Unit,
) : RecyclerView.Adapter<SearchEngineBottomAdapter.ViewHolder>() {

    private val engines = mutableListOf<SearchEngine>()

    fun submitList(newEngines: List<SearchEngine>, currentSelectedId: String) {
        engines.clear()
        engines.addAll(newEngines)
        selectedEngineId = currentSelectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchEngineBottomBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(engines[position], engines[position].id == selectedEngineId)
    }

    override fun getItemCount(): Int = engines.size

    inner class ViewHolder(
        private val binding: ItemSearchEngineBottomBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(engine: SearchEngine, isSelected: Boolean) {
            binding.tvEngineName.text = engine.name

            engine.icon?.let {
                binding.ivEngineIcon.setImageBitmap(it)
            }

            // 选中状态：背景高亮 + 显示对勾
            binding.itemContainer.isSelected = isSelected
            binding.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.tvEngineName.paint.isFakeBoldText = isSelected

            binding.root.setOnClickListener {
                onEngineSelected(engine)
            }
        }
    }
}
