package com.example.browser.ui.setting

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.data.language.Language
import com.example.browser.databinding.ItemLanguageBinding

/**
 * 语言选择适配器 - 精简版
 */
class LanguageAdapter(
    private val languages: MutableList<Language>,
    private val onLanguageSelected: (Language) -> Unit,
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    private var selectedPosition = -1

    init {
        selectedPosition = languages.indexOfFirst { it.isSelected }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return LanguageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(languages[position], position == selectedPosition)
    }

    override fun getItemCount(): Int = languages.size

    inner class LanguageViewHolder(
        private val binding: ItemLanguageBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(language: Language, isSelected: Boolean) {
            binding.tvLanguageName.text = language.nativeName

            // 选中状态：背景高亮 + 显示对勾
            binding.itemContainer.isSelected = isSelected
            binding.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.tvLanguageName.paint.isFakeBoldText = isSelected

            binding.root.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = bindingAdapterPosition

                if (oldPosition != -1) notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)

                onLanguageSelected(language)
            }
        }
    }
}
