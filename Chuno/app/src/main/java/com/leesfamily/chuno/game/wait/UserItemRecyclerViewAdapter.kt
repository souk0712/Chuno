package com.leesfamily.chuno.game.wait

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.leesfamily.chuno.databinding.FragmentUserItemBinding
import com.leesfamily.chuno.network.data.Player

class UserItemRecyclerViewAdapter(
    private val values: ArrayList<Player>?,
) : RecyclerView.Adapter<UserItemRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {

        return ViewHolder(
            FragmentUserItemBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (values != null) {
            val item = values[position]
            holder.idView.text = item.level
            holder.contentView.text = item.nickname
        }
    }

    override fun getItemCount(): Int = values?.size ?: 0

    inner class ViewHolder(binding: FragmentUserItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val idView: TextView = binding.userLevel
        val contentView: TextView = binding.userName

        override fun toString(): String {
            return super.toString() + " '" + contentView.text + "'"
        }
    }

}