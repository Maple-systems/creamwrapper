package com.mal.creamwrapper

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mal.creamwrapper.databinding.PageOneBinding
import com.mal.creamwrapper.databinding.PageTwoBinding

class PageAdapter : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    private val pages = listOf(
        PageType.One,
        PageType.Two
    )

    sealed class PageType {
        data object One : PageType()
        data object Two : PageType()
    }

    sealed class PageViewHolder(
        val root: ViewGroup
    ) : RecyclerView.ViewHolder(root) {

        class One(
            val binding: PageOneBinding
        ) : PageViewHolder(binding.root)

        class Two(
            val binding: PageTwoBinding
        ) : PageViewHolder(binding.root)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PageViewHolder {

        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) {
            0 -> PageViewHolder.One(
                PageOneBinding.inflate(inflater, parent, false)
            )

            else -> PageViewHolder.Two(
                PageTwoBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(
        holder: PageViewHolder,
        position: Int
    ) {
        // 页面数据绑定
    }

    override fun getItemCount(): Int {
        return pages.size
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }
}