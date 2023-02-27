package com.leesfamily.chuno.network.item

import android.util.Log
import com.leesfamily.chuno.network.ChunoServer
import com.leesfamily.chuno.network.data.IntForm
import com.leesfamily.chuno.network.data.Item
import com.leesfamily.chuno.network.data.ItemForm
import retrofit2.Response

class ItemGetter {
    private var itemData: ItemForm? = null

    fun requestItem(): List<Item>? {

        val itemResponse: Response<ItemForm> = ChunoServer.itemServer.getItemData().execute()

        val networkResponse = itemResponse.raw().networkResponse?.code
        val requestCode = itemResponse.code()
        if (requestCode == 200) {
            itemData = itemResponse.body()
            val item: List<Item>? = itemData?.result

            Log.d("추노_itemGetter", "requestLogin: request success, $item")
            return item
        }
        return null
    }

    fun requestBuyItem(token: String, itemId: Int): Boolean? {

        val itemResponse: Response<IntForm> =
            ChunoServer.itemServer.buyItemData(token, itemId).execute()

        val networkResponse = itemResponse.raw().networkResponse?.code
        val requestCode = itemResponse.code()
        if (requestCode == 200) {
            val code = itemResponse.body()?.code
            when (code) {
                0 -> {
                    return false
                }
                1 -> {
                    return true
                }
            }
            Log.d("추노_itemGetter", "requestBuyItem: request success, $code")

        }
        Log.e("추노_itemGetter", "requestBuyItem: $requestCode")
        return null
    }
}