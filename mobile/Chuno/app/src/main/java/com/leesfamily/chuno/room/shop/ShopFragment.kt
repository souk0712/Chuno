package com.leesfamily.chuno.room.shop

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.Placeholder
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.NavHostFragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.leesfamily.chuno.BuildConfig
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.FragmentShopBinding
import com.leesfamily.chuno.util.custom.MyCustomDialog
import com.leesfamily.chuno.util.custom.MyCustomDialogInterface

class ShopFragment : Fragment() {
    private lateinit var binding: FragmentShopBinding
    private val shopViewModel: ShopViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentShopBinding.inflate(inflater, container, false)
        binding.toolbarInclude.toolbarTitle.text = getString(R.string.shop_title)

        val itemImageViews: ArrayList<AppCompatImageView> = arrayListOf(
            binding.runnerItemImage1,
            binding.runnerItemImage2,
            binding.runnerItemImage3,
            binding.runnerItemImage4,
            binding.chaserItemImage1,
            binding.chaserItemImage2,
            binding.chaserItemImage3,
            binding.chaserItemImage4,
        )
        val itemTextViews: ArrayList<TextView> = arrayListOf(
            binding.runnerItemText1,
            binding.runnerItemText2,
            binding.runnerItemText3,
            binding.runnerItemText4,
            binding.chaserItemText1,
            binding.chaserItemText2,
            binding.chaserItemText3,
            binding.chaserItemText4,
        )
        val itemCountViews: ArrayList<TextView> = arrayListOf(
            binding.runnerItemCount1,
            binding.runnerItemCount2,
            binding.runnerItemCount3,
            binding.runnerItemCount4,
            binding.chaserItemCount1,
            binding.chaserItemCount2,
            binding.chaserItemCount3,
            binding.chaserItemCount4,
        )
        val itemViews: ArrayList<LinearLayout> = arrayListOf(
            binding.shopRunnerItemView1,
            binding.shopRunnerItemView2,
            binding.shopRunnerItemView3,
            binding.shopRunnerItemView4,
            binding.shopChaserItemView1,
            binding.shopChaserItemView2,
            binding.shopChaserItemView3,
            binding.shopChaserItemView4,
        )

        val itemList = mainViewModel.itemList
        shopViewModel.setItemList(itemList)
        mainViewModel.user.observe(viewLifecycleOwner) {
            binding.moneyText.text = it.money.toString()
            val userItems = it.items
            userItems.forEachIndexed { index, item ->
                itemCountViews[index].text = item.toString()
            }
        }

        var choosedItem: Int? = null
        val server_url = BuildConfig.SERVER_URL
        itemList.forEachIndexed { index, item ->

            val imageUrl =
                "${server_url}/resources/images?path=${item.imgPath}"

            Glide.with(requireContext())
                .load(imageUrl)
                .placeholder(R.drawable.round_logo)
                .error(R.drawable.account)
                .into(itemImageViews[index])
            Log.d(TAG, "onCreateView: item.name ${item.name}")

            itemTextViews[index].text = item.name
        }
        itemViews.forEachIndexed { index, item ->
            val imageUrl =
                "${server_url}/resources/images?path=${itemList[index].imgPath}"

            item.setOnClickListener {
                choosedItem = index
                binding.itemPreviewText.text = itemList[index].name
                binding.itemPreviewExplain.text = itemList[index].description
                binding.itemPreviewPrice.text = itemList[index].price.toString()

                Glide.with(requireContext())
                    .load(imageUrl)
                    .placeholder(R.drawable.round_logo)
                    .error(R.drawable.account)
                    .into(binding.itemPreviewView)
            }
        }
        shopViewModel.isBuySuccess.observe(viewLifecycleOwner) {
            if (it) {
                mainViewModel.setUserInfo()
            } else {
                Toast.makeText(requireContext(), "구매 실패", Toast.LENGTH_SHORT).show()
            }
        }

        binding.itemPreviewBuyButton.setOnClickListener {
            choosedItem?.let { itemId ->
                mainViewModel.user.value?.let {
                    if (mainViewModel.user.value!!.money > itemList[itemId].price) {
                        if (mainViewModel.token.value != null) {
                            shopViewModel.buyItem(
                                mainViewModel.token.value!!, itemId
                            )
                        } else {
                            navigateLogin()
                        }
                    } else {
                        MyCustomDialog(requireContext(), object : MyCustomDialogInterface {
                            override fun onYesButtonClicked() {

                            }

                            override fun onNoButtonClicked() {

                            }

                        }).apply {
                            message = getString(R.string.no_money)
                            yesMsg = getString(R.string.ok)
                            show()
                        }
                        Log.d(TAG, "onCreateView: 돈이 없어요.")
                    }
                }
            }
        }

        return binding.root
    }

    private fun navigateLogin() {
        val navHostFragment =
            activity?.supportFragmentManager?.findFragmentById(R.id.start_nav_host_fragment) as NavHostFragment
        navHostFragment.navController.navigate(R.id.loginFragment)
    }

    companion object {
        private const val TAG = "추노_ShopFragment"
    }
}