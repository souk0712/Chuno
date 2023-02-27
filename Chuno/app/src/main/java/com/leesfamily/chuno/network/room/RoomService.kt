package com.leesfamily.chuno.network.room

import com.leesfamily.chuno.network.data.*
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface RoomService {

    @GET("room")
    fun getRoomList(@Query("lat") lat: Double, @Query("lng") lng: Double): Call<RoomForm>

    @POST("room")
    fun createRoom(@Header("Authorization") auth: String, @Body room: RequestRoom): Call<LongForm>

    @GET("room/{roomId}")
    fun getRoomData(
        @Header("Authorization") auth: String,
        @Path("roomId") roomId: Long
    ): Call<RoomDataForm>
}