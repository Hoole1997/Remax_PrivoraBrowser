package com.example.browser.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.data.ClearOption
import com.example.browser.databinding.ItemClearOptionBinding

/**
 * 清除选项适配器 - 精简版
 */
class ClearOptionAdapter(
    private val items: List<ClearOption>,
    private val onItemClick: (ClearOption) -> Unit,
) : RecyclerView.Adapter<ClearOptionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemClearOptionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClearOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvOptionName.setText(item.nameResId)

            // 选中状态：背景高亮 + 显示对勾
            itemContainer.isSelected = item.isSelected
            ivCheck.visibility = if (item.isSelected) View.VISIBLE else View.GONE
            tvOptionName.paint.isFakeBoldText = item.isSelected

            root.setOnClickListener {
                item.isSelected = !item.isSelected
                notifyItemChanged(position)
                onItemClick(item)
            }
        }
    }

    override fun getItemCount() = items.size
}
