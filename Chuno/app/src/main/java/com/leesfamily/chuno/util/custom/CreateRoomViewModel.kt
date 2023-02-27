package com.leesfamily.chuno.util.custom

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.leesfamily.chuno.network.data.RequestRoom
import com.leesfamily.chuno.network.data.Room
import com.leesfamily.chuno.network.room.RoomGetter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class CreateRoomViewModel : ViewModel() {
    private var _isReadOnly: Boolean = false
    val isReadOnly: Boolean
        get() = _isReadOnly

    fun setReadOnly(isReadOnly: Boolean) {
        _isReadOnly = isReadOnly
    }

    // 방제목
    private var _title: MutableLiveData<String> = MutableLiveData()
    val title: LiveData<String>
        get() = _title

    fun setTitle(title: String) {
        _title.value = title
    }

    // 비밀번호
    private var _password: MutableLiveData<String> = MutableLiveData()
    val password: LiveData<String>
        get() = _password

    fun setPw(password: String) {
        _password.value = password
    }

    // 예약일자
    private var _reservationDate: MutableLiveData<String> = MutableLiveData()
    val reservationDate: LiveData<String>
        get() = _reservationDate

    fun setReservationDate(reservationDate: String) {
        _reservationDate.value = reservationDate
    }

    private var _yearValue: MutableLiveData<Int> = MutableLiveData()
    val yearValue: LiveData<Int>
        get() = _yearValue

    fun setYearValue(yearValue: Int) {
        _yearValue.value = yearValue
    }

    private var _monthValue: MutableLiveData<Int> = MutableLiveData()
    val monthValue: LiveData<Int>
        get() = _monthValue

    fun setMonthValue(monthValue: Int) {
        _monthValue.value = monthValue
    }


    private var _dayValue: MutableLiveData<Int> = MutableLiveData()
    val dayValue: LiveData<Int>
        get() = _dayValue

    fun setDayValue(dayValue: Int) {
        _dayValue.value = dayValue
    }

    // 예약시간
    private var _reservationHour: MutableLiveData<Int> = MutableLiveData()
    val reservationHour: LiveData<Int>
        get() = _reservationHour


    fun setReservationHour(reservationHour: Int) {
        _reservationHour.value = reservationHour
    }

    private var _reservationMin: MutableLiveData<Int> = MutableLiveData()
    val reservationMin: LiveData<Int>
        get() = _reservationMin

    fun setReservationMin(reservationMin: Int) {
        _reservationMin.value = reservationMin
    }

    private var _maxPlayers: MutableLiveData<Int> = MutableLiveData()
    val maxPlayers: LiveData<Int>
        get() = _maxPlayers

    fun setCurValue(maxPlayers: Int) {
        _maxPlayers.value = maxPlayers
    }

    private var _curRadiusValue: MutableLiveData<Int> = MutableLiveData()
    val curRadiusValue: LiveData<Int>
        get() = _curRadiusValue

    fun setCurRadiusValue(curRadiusValue: Int) {
        _curRadiusValue.value = curRadiusValue
    }


    private var _isPublic: MutableLiveData<Boolean> = MutableLiveData()
    val isPublic: LiveData<Boolean>
        get() = _isPublic

    fun setIsPublic(isPublic: Boolean) {
        _isPublic.value = isPublic
    }

    private var _isToday: MutableLiveData<Boolean> = MutableLiveData()
    val isToday: LiveData<Boolean>
        get() = _isToday

    fun setIsToday(isToday: Boolean) {
        _isToday.value = isToday
    }

    private var _lat: MutableLiveData<Double> = MutableLiveData()
    val lat: LiveData<Double>
        get() = _lat

    fun setLat(lat: Double) {
        _lat.value = lat
    }

    private var _lng: MutableLiveData<Double> = MutableLiveData()
    val lng: LiveData<Double>
        get() = _lng

    fun setLng(lng: Double) {
        _lng.value = lng
    }

    private var _roomId: MutableLiveData<Long> = MutableLiveData()
    val roomId: LiveData<Long>
        get() = _roomId

    fun setRoomId(roomId: Long) {
        _roomId.value = roomId
    }

    fun setRoomInfo() {
        // 서버에서 방정보를 불러와서 셋팅
    }

    fun createRoom(token:String) {
        viewModelScope.launch(Dispatchers.IO) {
            val roomId = async {
                RoomGetter().requestCreateRoom(token,
                    RequestRoom(
                        title.value!!,
                        password.value,
                        isPublic.value!!,
                        isToday.value!!,
                        lat.value!!,
                        lng.value!!,
                        maxPlayers.value!!,
                        curRadiusValue.value!!,
                        reservationHour.value!!,
                        reservationMin.value!!
                    )
                )
            }
            _roomId.postValue(roomId.await())
            Log.d("추노", "createRoom: roomId ${roomId.await()}")
        }
    }
}