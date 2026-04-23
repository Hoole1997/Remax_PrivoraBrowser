package com.example.browser.ui.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.data.language.Language
import com.example.browser.databinding.ItemLanguageBinding

/**
 * 语言选择适配器
 */
class LanguageAdapter(
    private val languages: MutableList<Language>,
    private val onLanguageSelected: (Language) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {
    
    private var selectedPosition = -1
    
    init {
        // 找到已选中的语言
        selectedPosition = languages.indexOfFirst { it.isSelected }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LanguageViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        holder.bind(languages[position], position == selectedPosition)
    }
    
    override fun getItemCount(): Int = languages.size
    
    inner class LanguageViewHolder(
        private val binding: ItemLanguageBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(language: Language, isSelected: Boolean) {
            binding.tvLanguageName.text = language.nativeName
            binding.cbLanguage.isChecked = isSelected
            
            binding.root.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = bindingAdapterPosition
                
                // 更新UI
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
                
                // 回调
                onLanguageSelected(language)
            }
            
            binding.cbLanguage.setOnClickListener {
                binding.root.performClick()
            }
        }
    }
}
