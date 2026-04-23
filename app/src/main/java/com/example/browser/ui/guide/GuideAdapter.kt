package com.example.browser.ui.guide

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.browser.databinding.ItemGuidePageBinding

data class GuideItem(val imageRes: Int, val titleRes: Int)

class GuideAdapter(private val items: List<GuideItem>) : RecyclerView.Adapter<GuideAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemGuidePageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGuidePageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.ivGuide.setImageResource(item.imageRes)
        holder.binding.tvTitle.setText(item.titleRes)
    }

    override fun getItemCount() = items.size
}
