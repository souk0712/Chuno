package com.leesfamily.chuno.network.item

import com.leesfamily.chuno.network.data.IntForm
import com.leesfamily.chuno.network.data.ItemForm
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface ItemService {

    @GET("item")
    fun getItemData(): Call<ItemForm>
    // https://i8d208.p.ssafy.io/api/resources/images?path=item/item2.png

    @POST("user/shop/{itemId}")
    fun buyItemData(
        @Header("Authorization") auth: String,
        @Path("itemId") itemId: Int
    ): Call<IntForm>
}