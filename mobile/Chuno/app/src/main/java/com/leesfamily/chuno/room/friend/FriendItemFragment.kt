package com.leesfamily.chuno.room.friend

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.FragmentFriendListBinding
import com.leesfamily.chuno.room.placeholder.PlaceholderContent

/**
 * A fragment representing a list of Items.
 */
class FriendItemFragment : Fragment() {

    private lateinit var binding: FragmentFriendListBinding

    private var columnCount = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            columnCount = it.getInt(ARG_COLUMN_COUNT)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFriendListBinding.inflate(inflater, container, false)
        binding.toolbarInclude.toolbarTitle.text = getString(R.string.friend_list_title)
        // Set the adapter
        binding.friendList.apply {

            layoutManager = when {
                columnCount <= 1 -> LinearLayoutManager(context)
                else -> GridLayoutManager(context, columnCount)
            }
            val itemDecoration = DividerItemDecoration(context, DividerItemDecoration.VERTICAL)
            itemDecoration.setDrawable(getDrawable(context, R.drawable.line)!!)
            addItemDecoration(itemDecoration)
            adapter = FriendItemRecyclerViewAdapter(PlaceholderContent.ITEMS)
        }

        return binding.root
    }

    companion object {
        const val ARG_COLUMN_COUNT = "column-count"

        @JvmStatic
        fun newInstance(columnCount: Int) =
            FriendItemFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_COLUMN_COUNT, columnCount)
                }
            }
    }
}