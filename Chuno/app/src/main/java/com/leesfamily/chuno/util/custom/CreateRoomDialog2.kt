package com.leesfamily.chuno.util.custom

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.CreateRoomDialog2Binding
import com.leesfamily.chuno.util.PermissionHelper
import com.leesfamily.chuno.util.custom.DialogSizeHelper.dialogFragmentResize

class CreateRoomDialog2(
    context: Context, createRoomDialogInterface: CreateRoomDialogInterface
) : DialogFragment(), MyCustomDialogInterface, View.OnClickListener, OnMapReadyCallback {

    private lateinit var binding: CreateRoomDialog2Binding
    private var createRoomDialogInterface: CreateRoomDialogInterface? = null
    private var mContext: Context? = null
    private val createRoomViewModel: CreateRoomViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    // 간격
    var step = 50

    // 최소
    var minValue = 300

    // 최대
    var maxValue = 1000

    // 기본값
    var defValue = 500

    private var curValue = 500

    // 방정보 보기 전용
    var isReadOnly: Boolean = false

    // 인터페이스 연결
    init {
        this.createRoomDialogInterface = createRoomDialogInterface
        mContext = context
    }

    // 위치
    private var mMap: GoogleMap? = null
    private var currentPosition: LatLng? = null
    private val fusedLocationProviderClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }
    private val defaultLocation = LatLng(36.106164, 128.417049)
    private var needRequest = false
    private var circle: Circle? = null
    private val locationRequest: LocationRequest by lazy {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL).apply {
            setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE)
            setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL)
        }.build()
    }

    //위치정보 요청시 호출
    var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val locationList = locationResult.locations
            if (locationList.size > 0) {
                val location = locationList[locationList.size - 1]

                currentPosition = LatLng(location.latitude, location.longitude)

                Log.d(
                    TAG, "onLocationResult: 위도: ${location.latitude}, 경도: ${location.longitude}"
                )

                //현재 위치 이동
                setCurrentLocation(location)

                onAddCircle(defValue.toDouble())
            }
        }
    }

    private val locationServiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == AppCompatActivity.RESULT_OK) {
                //사용자가 GPS를 켰는지 검사함
                if (checkLocationServicesStatus()) {
                    needRequest = true
                    return@registerForActivityResult
                } else {
                    Toast.makeText(
                        context, "위치 서비스가 꺼져 있어, 현재 위치를 확인할 수 없습니다.", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (!isGranted) {
                    if (permissionName == PermissionHelper.ACCESS_COARSE_LOCATION || permissionName == PermissionHelper.ACCESS_FINE_LOCATION) showPermissionDialog(
                        getString(R.string.location_permission_message),
                        getString(R.string.setting),
                        getString(R.string.cancel)
                    )
                }

            }
        }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: ")
        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getParcelable(KEY_LOCATION)!!
        }
        if (PermissionHelper.hasLocationPermission(requireContext())) if (currentPosition != null) {
            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location: Location ->
                currentPosition = LatLng(location.latitude, location.longitude)
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    PermissionHelper.ACCESS_FINE_LOCATION, PermissionHelper.ACCESS_COARSE_LOCATION
                )
            )
        }
//        getDeviceLocation(requireActivity())
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        binding = CreateRoomDialog2Binding.inflate(inflater, container, false)
        Log.d(TAG, "onCreateView: ")
        dialog?.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.map.apply {
            onCreate(savedInstanceState)
            getMapAsync(this@CreateRoomDialog2)
//            val mapFragment = childFragmentManager
//                .findFragmentById(R.id.map) as SupportMapFragment
//            mapFragment.getMapAsync()
        }
        binding.yesButton.setOnClickListener(this)
        binding.closeButton.setOnClickListener(this)
        binding.noButton.setOnClickListener(this)
        val roundValue = binding.roomRoundValue.apply {
            text = defValue.toString()
        }


        binding.roomRoundEdit.apply {
            setSeekBarMax(this, maxValue)
            setSeekBarDefault(this, maxValue)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?, progress: Int, fromUser: Boolean
                ) {
                    curValue = setSeekBarChange(progress, roundValue)

                    onAddCircle(curValue.toDouble())
//                    MapsUtil.onAddCircle(curValue.toDouble(),blueTrans,blue)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

//        MapsUtil.onAddCircle(defValue.toDouble(),blue,blueTrans)
        setReadOnly()
        isCancelable = false
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.map.onStart()
        Log.d(TAG, "onResume: ")
        context?.dialogFragmentResize(this, 0.8f)
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.map.onDestroy()
    }

    override fun onStop() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        super.onStop()
        binding.map.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.map.onLowMemory()
    }

    @SuppressLint("MissingPermission")
    override fun onStart() {
        super.onStart()
        binding.map.onStart()

        if (PermissionHelper.hasLocationPermission(requireContext())) {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest, locationCallback, null
            )
            if (mMap != null && checkLocationServicesStatus()) mMap!!.isMyLocationEnabled = true
        }
    }

/*    @SuppressLint("MissingPermission")
    fun getDeviceLocation(context: Context) {
        try {
            if (PermissionHelper.hasLocationPermission(context)) {
                val locationResult = fusedLocationProviderClient.lastLocation

                locationResult.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "getDeviceLocation: ")
                        // Set the map's camera position to the current location of the device.
                        lastKnownLocation = task.result
                        if (lastKnownLocation != null) {
                            mMap?.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng( // 이전으로 돌아갈 때, java.lang.NullPointerException
                                        lastKnownLocation!!.latitude,
                                        lastKnownLocation!!.latitude,
                                        lastKnownLocation!!.longitude
                                    ), DEFAULT_ZOOM.toFloat()
                                )
                            )
                            mMap!!.isMyLocationEnabled = true
                            onAddCircle(defValue.toDouble())

                        }
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        Log.e(TAG, "Exception: %s", task.exception)
                        mMap?.moveCamera(
                            CameraUpdateFactory
                                .newLatLngZoom(defaultLocation, DEFAULT_ZOOM.toFloat())
                        )
                    }

                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }*/

    fun setCurrentLocation(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        val cameraUpdate = CameraUpdateFactory.newLatLng(currentLatLng)
        mMap!!.moveCamera(cameraUpdate)
    }


    private fun showPermissionDialog(
        msg: String, positive: String, negative: String
    ) {
        MyCustomDialog(requireContext(), object : MyCustomDialogInterface {
            override fun onYesButtonClicked() {
                PermissionHelper.launchPermissionSettings(requireActivity())
            }

            override fun onNoButtonClicked() {}
        }).apply {
            message = msg
            yesMsg = positive
            noMsg = negative
            show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // 위치서비스 활성화 여부 check
        if (!checkLocationServicesStatus()) {
            showDialogForLocationServiceSetting()
        } else {
            if (PermissionHelper.hasLocationPermission(requireContext())) {
                fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest, locationCallback, null
                )
                if (mMap != null) mMap!!.isMyLocationEnabled = true
            }
        }
    }

    private fun showDialogForLocationServiceSetting() {
        MyCustomDialog(requireContext(), object : MyCustomDialogInterface {
            override fun onYesButtonClicked() {
                locationServiceLauncher.launch(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                )
            }

            override fun onNoButtonClicked() {
            }

        }).apply {
            message = """
            앱을 사용하기 위해서는 위치 서비스가 필요합니다.
            위치 설정을 해주세요.
            """.trimIndent()
            yesMsg = "설정"
            show()
        }
    }

    private fun checkLocationServicesStatus(): Boolean {
        val locationManager =
            activity?.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        ))
    }


    private fun setReadOnly() {
        if (isReadOnly) {
            Log.d(TAG, "setReadOnly: $isReadOnly")
            binding.titleView.text = getString(R.string.room_info_text)
            binding.roomRoundEdit.isEnabled = !isReadOnly
        }
    }

    private fun setSeekBarMax(sb: AppCompatSeekBar, max_value: Int) {
        sb.max = ((max_value - minValue) / step)
    }

    private fun setSeekBarDefault(sb: AppCompatSeekBar, max_value: Int) {
        sb.progress = sb.max / (max_value / defValue) - 1
    }

    private fun setSeekBarChange(progress: Int, tv: TextView): Int {
        val value = minValue + progress * step
        tv.text = value.toString()
        return value
    }

    private fun showCustomDialog(flag: Int) {
        when (flag) {
            1 -> {    // 방생성
                MyCustomDialog(mContext!!, this).apply {
                    message = getString(R.string.create_room_message)
                    yesMsg = getString(R.string.ok)
                    show()
                }
            }
            2 -> {    // lat,lng 아직 미로딩
                MyCustomDialog(mContext!!, this).apply {
                    message = getString(R.string.lat_lng_loading_message)
                    yesMsg = getString(R.string.ok)
                    show()
                }
            }
        }
    }

    private fun setViewModel(): Boolean {
        createRoomViewModel.setCurRadiusValue(curValue)
        if (currentPosition == null) {
            return false
        }
        currentPosition?.let { createRoomViewModel.setLat(it.latitude) }
        currentPosition?.let { createRoomViewModel.setLng(it.longitude) }
        return true
    }

    override fun onYesButtonClicked() {
    }

    override fun onNoButtonClicked() {
    }

    override fun onClick(view: View?) {
        when (view) {
            binding.yesButton -> {
                if (!isReadOnly) {
                    if (setViewModel()) {
                        showCustomDialog(1)
                        dismiss()
                    } else {
                        showCustomDialog(2)
                    }
                    val token = mainViewModel.token.value
                    if (token == null) {
                        dismiss()
                        navigateLogin()
                    } else
                        createRoomViewModel.createRoom(mainViewModel.token.value!!)
                    Log.d(TAG, "onClick: 저장하여 서버와 통신")
                } else {
                    dismiss()

                }
            }
            binding.closeButton -> {
                dismiss()
            }
            binding.noButton -> {
                createRoomDialogInterface?.onPrevButtonClicked(binding.root)
                dismiss()
            }
        }
    }

    private fun setDefaultLocation() {

        //초기 위치를 서울로
        val DEFAULT_LOCATION = LatLng(37.56, 126.97)
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
            DEFAULT_LOCATION, DEFAULT_ZOOM
        )
        mMap!!.moveCamera(cameraUpdate)
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        Log.d(TAG, "onMapReady: ")
        mMap = googleMap

        setDefaultLocation()

        mMap!!.apply {
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false

            uiSettings.setAllGesturesEnabled(false)
            mapType = GoogleMap.MAP_TYPE_NORMAL
            if (PermissionHelper.hasLocationPermission(requireContext())) { // 1. 위치 퍼미션을 가지고 있는지 확인
                // 2. 이미 퍼미션을 가지고 있다면
                // ( 안드로이드 6.0 이하 버전은 런타임 퍼미션이 필요없기 때문에 이미 허용된 걸로 인식)
                startLocationUpdates() // 3. 위치 업데이트 시작
            } else {  //2. 권한이 없다면
                permissionLauncher.launch(
                    arrayOf(
                        PermissionHelper.ACCESS_FINE_LOCATION,
                        PermissionHelper.ACCESS_COARSE_LOCATION
                    )
                )

            }
        }
//        onAddCircle(defValue.toDouble())
    }

    //마커 , 원추가
    private fun onAddCircle(meter: Double) {
        // 반경 1KM원
        circle?.remove()
        circle = mMap!!.addCircle(
            CircleOptions().center(
                if (currentPosition != null) {
                    LatLng(
                        currentPosition!!.latitude, currentPosition!!.longitude
                    )
                } else {
                    defaultLocation
                }
            ) //원점
                .radius(meter) //반지름 단위 : m
                .strokeWidth(2f) //선너비 0f : 선없음
                .strokeColor(ContextCompat.getColor(mContext!!, R.color.blue))
                .fillColor(ContextCompat.getColor(mContext!!, R.color.blue_trans)) //배경색
        )
        mMap!!.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                circle!!.center, getZoomLevel(circle)
            )
        )
    }

    private fun getZoomLevel(circle: Circle?): Float {
        var zoomLevel = 0f
        if (circle != null) {
            val radius = circle.radius
            val scale = radius / 500
            zoomLevel = (DEFAULT_ZOOM - Math.log(scale) / Math.log(2.0)).toInt().toFloat()
        }
        return zoomLevel + .45f
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mMap.let { map ->
            outState.putParcelable(KEY_LOCATION, currentPosition)
        }
        super.onSaveInstanceState(outState)
    }

    private fun navigateLogin() {
        val navHostFragment =
            activity?.supportFragmentManager?.findFragmentById(R.id.start_nav_host_fragment) as NavHostFragment
        navHostFragment.navController.navigate(R.id.loginFragment)
    }

    companion object {
        private const val TAG = "추노_CreateRoomDialog2"
        private const val UPDATE_INTERVAL = 1000L // 1초
        private const val MIN_UPDATE_INTERVAL = 500L // 0.5초
        private const val MIN_UPDATE_DISTANCE = 10f // 10m
        private const val DEFAULT_ZOOM = 14f
        private const val KEY_LOCATION = "location"
    }

}
