package com.leesfamily.chuno.room.shop

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.network.data.Item
import com.leesfamily.chuno.network.item.ItemGetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class ShopViewModel : ViewModel() {
    // 변경가능한 Mutable 타입의 LiveData
    private var _itemList: MutableLiveData<List<Item>> = MutableLiveData()
    private var _isBuySuccess: MutableLiveData<Boolean> = MutableLiveData()

    // 무결성을 위한 Getter
    val itemList: LiveData<List<Item>>
        get() = _itemList

    val isBuySuccess: LiveData<Boolean>
        get() = _isBuySuccess

    fun setItemList(itemList: List<Item>) {
        _itemList.value = itemList
    }

    fun buyItem(token: String, itemId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d("추노_shopViewModel", "buyItem: itemId $itemId")
            val result = async { ItemGetter().requestBuyItem(token, itemId+1) }
            _isBuySuccess.postValue(result.await())
        }

    }


}