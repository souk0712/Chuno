package com.leesfamily.chuno.network.room

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.leesfamily.chuno.network.ChunoServer
import com.leesfamily.chuno.network.data.*
import retrofit2.Response

class RoomGetter {

    fun requestRoomList(location: LatLng): List<Room>? {

        val itemResponse: Response<RoomForm> =
            ChunoServer.roomServer.getRoomList(location.latitude, location.longitude).execute()

        val networkResponse = itemResponse.raw().networkResponse?.code
        val requestCode = itemResponse.code()
        if (requestCode == 200) {
            val roomData = itemResponse.body()
            val roomList: List<Room>? = roomData?.result

            Log.d("추노_item", "requestRoomList: request success, $roomList")
            return roomList
        }
        Log.d("추노_item", "requestRoomList: request failed, $requestCode")


        return null
    }

    fun requestRoomData(token: String, roomId: Long): Room? {

        Log.d("추노_item", "requestRoomData: token $token")
        Log.d("추노_item", "requestRoomData: roomId $roomId")
        val itemResponse: Response<RoomDataForm> =
            ChunoServer.roomServer.getRoomData(token, roomId).execute()

        val networkResponse = itemResponse.raw().networkResponse?.code
        val requestCode = itemResponse.code()
        if (requestCode == 200) {
            val code = itemResponse.body()?.code
            if (code == 1) {
                val roomData = itemResponse.body()?.result
                Log.d("추노_item", "requestRoomData: request success, $roomData")
                return roomData
            }
        }
        Log.d("추노_item", "requestRoomData: request failed, $requestCode")

        return null
    }

    fun requestCreateRoom(token: String, room: RequestRoom): Long? {

        val itemResponse: Response<LongForm> =
            ChunoServer.roomServer.createRoom(token, room).execute()

        val networkResponse = itemResponse.raw().networkResponse?.code
        val requestCode = itemResponse.code()
        if (requestCode == 200) {
            val code = itemResponse.body()?.code
            if (code == 1) {
                val roomId = itemResponse.body()?.result
                Log.d("추노_item", "requestCreateRoom: request success, $roomId")
                return roomId
            }
        }
        Log.d("추노_item", "requestCreateRoom: request failed, $requestCode")
        return null
    }

}