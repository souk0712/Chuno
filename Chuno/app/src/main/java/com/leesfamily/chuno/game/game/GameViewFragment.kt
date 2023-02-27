package com.leesfamily.chuno.game.game

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import com.leesfamily.chuno.BuildConfig
import com.leesfamily.chuno.MainViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.FragmentGameViewBinding
import com.leesfamily.chuno.game.GameViewModel
import com.leesfamily.chuno.network.data.Room
import com.leesfamily.chuno.network.data.User
import com.leesfamily.chuno.openvidu.game.LocalParticipantGame
import com.leesfamily.chuno.openvidu.game.RemoteParticipantGame
import com.leesfamily.chuno.openvidu.game.SessionGame
import com.leesfamily.chuno.openvidu.utils.CustomHttpClient
import com.leesfamily.chuno.openvidu.websocket.CustomWebSocketGame
import com.leesfamily.chuno.room.shop.ShopFragment
import com.leesfamily.chuno.util.PermissionHelper
import com.leesfamily.chuno.util.custom.CreateRoomDialog2
import com.leesfamily.chuno.util.custom.MyCustomDialog
import com.leesfamily.chuno.util.custom.MyCustomDialogInterface
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.webrtc.EglBase
import org.webrtc.MediaStream
import org.webrtc.SurfaceViewRenderer
import java.io.IOException
import java.util.*
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

class GameViewFragment : Fragment(), OnMapReadyCallback, SensorEventListener {
    private lateinit var binding: FragmentGameViewBinding

    private val gameViewModel: GameViewModel by activityViewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private var mMap: GoogleMap? = null
    private lateinit var user: User
    private val mFusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(requireActivity())
    }
    private val defaultLocation = LatLng(36.106164, 128.417049)
    private val locationRequest: LocationRequest by lazy {
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL).apply {
            setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE)
            setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL)
        }.build()
    }
    private var currentPosition: LatLng? = null
    private val userMarkers: MutableList<Marker?> by lazy {
        mutableListOf()
    }

    private lateinit var callback: OnBackPressedCallback
    private var APPLICATION_SERVER_URL = BuildConfig.SERVER_URL
    private var session: SessionGame? = null
    private var httpClient: CustomHttpClient? = null
    private var needRequest = false

    private var isMenu = false
    private var isMyVideo = false

    private lateinit var roomData: Room
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (!isGranted) {
                    if (permissionName == PermissionHelper.ACCESS_COARSE_LOCATION || permissionName == PermissionHelper.ACCESS_FINE_LOCATION)
                        showPermissionDialog(
                            getString(R.string.location_permission_message),
                            getString(R.string.setting),
                            getString(R.string.cancel)
                        )
                }

            }
        }


    private val turnOnMyVideo = {
        binding.peerContainer.visibility = View.VISIBLE
        binding.footer.faceButton.setImageResource(R.drawable.close_button)
        isMyVideo = !isMyVideo
    }

    private val turnOffMyVideo = {
        binding.peerContainer.visibility = View.GONE
        binding.footer.faceButton.setImageResource(R.drawable.face_button)
        isMyVideo = !isMyVideo
    }

    private val closeMenu = {
        binding.menu.root.visibility = View.GONE
        binding.footer.menuButton.setImageResource(R.drawable.menu)
        isMenu = !isMenu
    }

    private val openMenu = {
        binding.menu.root.visibility = View.VISIBLE
        binding.footer.menuButton.setImageResource(R.drawable.close_button)
        isMenu = !isMenu
    }

    //위치정보 요청시 호출
    var locationCallback: LocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val locationList = locationResult.locations
            if (locationList.size > 0) {
                val location = locationList[locationList.size - 1]
                val marker = mutableListOf(
                    LatLng(36.102208, 128.424187),
                    LatLng(36.102290, 128.423861), LatLng(36.102818, 128.424430),
                    LatLng(36.102421, 128.424620), LatLng(36.103249, 128.423868)
                )
                currentPosition = LatLng(location.latitude, location.longitude)

                Log.d(
                    TAG,
                    "onLocationResult: 위도: ${location.latitude}, 경도: ${location.longitude}"
                )

                //현재 위치에 마커 생성하고 이동
                setCurrentLocation(location)
                addMarkerUser(marker)
                onAddCircle(roomData.distance)
            }
        }
    }

    // 원
    private var circle: Circle? = null

    private val sensorManager by lazy {
        activity?.getSystemService(SENSOR_SERVICE) as SensorManager
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: ")
        if (savedInstanceState != null) {
            currentPosition = savedInstanceState.getParcelable(KEY_LOCATION)!!
        }
        if (PermissionHelper.hasLocationPermission(requireContext())) {
            if (currentPosition != null) {
                mFusedLocationClient.lastLocation.addOnSuccessListener { location: Location ->
                    currentPosition = LatLng(location.latitude, location.longitude)
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    PermissionHelper.ACCESS_FINE_LOCATION, PermissionHelper.ACCESS_COARSE_LOCATION
                )
            )
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentGameViewBinding.inflate(inflater, container, false)
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        gameViewModel.roomData.value?.let {
            roomData = it
        }

        val random = Random()
        val randomIndex = random.nextInt(100)
        val pagerAdapter = GameViewFragmentAdapter(this)
        val pager = binding.pager.apply {
            adapter = pagerAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    Log.d(TAG, "onPageScrollStateChanged: $position")
                    if (position == 0) {
                        binding.arrowLeft.visibility = View.GONE
                    } else {
                        binding.arrowLeft.visibility = View.VISIBLE

                    }
                    if (position == NUM_PAGES - 1) {
                        binding.arrowRight.visibility = View.GONE
                    } else {
                        binding.arrowRight.visibility = View.VISIBLE

                    }
                }
            })
        }


        gameViewModel.roomData.observe(viewLifecycleOwner) {
            roomData = it
        }

        mainViewModel.user.observe(viewLifecycleOwner) {
            user = it
        }

        binding.arrowLeft.setOnClickListener {
            val cur = pager.currentItem
            pager.currentItem = cur - 1
        }

        binding.arrowRight.setOnClickListener {
            val cur = pager.currentItem
            pager.currentItem = cur + 1
        }

        binding.map.apply {
            onCreate(savedInstanceState)
            getMapAsync(this@GameViewFragment)
//            val mapFragment = childFragmentManager
//                .findFragmentById(R.id.map) as SupportMapFragment
//            mapFragment.getMapAsync(this@GameViewFragment)
        }

        binding.footer.menuButton.apply {
            setOnClickListener {
                if (isMyVideo) {
                    turnOffMyVideo.invoke()
                }
                if (isMenu) {
                    closeMenu.invoke()
                } else {
                    openMenu.invoke()
                }
            }

        }
        binding.footer.faceButton.apply {
            setOnClickListener {
                if (isMenu) {
                    closeMenu.invoke()
                }
                if (isMyVideo) {
                    turnOffMyVideo.invoke()
                } else {
                    turnOnMyVideo.invoke()
                }
            }

        }
        initViews()
        httpClient = CustomHttpClient(APPLICATION_SERVER_URL)
        val sessionId = roomData.id.toString()
        getToken(sessionId)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        (activity as AppCompatActivity).supportActionBar?.hide()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        binding.map.onPause()
    }

    override fun onDestroy() {
        leaveSession()
        super.onDestroy()
        binding.map.onDestroy()
        activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        (activity as AppCompatActivity).supportActionBar?.show()

    }

    override fun onStop() {
        leaveSession()
        mFusedLocationClient.removeLocationUpdates(locationCallback)
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
            mFusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
            if (mMap != null && checkLocationServicesStatus()) mMap!!.isMyLocationEnabled = true
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveSession()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(this, callback)
    }

    override fun onDetach() {
        super.onDetach()
        callback.remove()
    }

    fun addMarkerUser(marker: MutableList<LatLng>) {
        marker.forEachIndexed { index, latLng ->
            val userLatLng = LatLng(latLng.latitude, latLng.longitude)

            val markerOptions = MarkerOptions()
            markerOptions.position(userLatLng)
            markerOptions.icon(BitmapDescriptorFactory.fromResource(R.drawable.game_user_marker))

            if (userMarkers.size <= index) {
                userMarkers.add(index, mMap!!.addMarker(markerOptions))
            } else {
                userMarkers[index] = mMap!!.addMarker(markerOptions)
            }

        }
    }

    private fun setDefaultLocation() {

        //초기 위치를 서울로
        val DEFAULT_LOCATION = LatLng(37.56, 126.97)
        val cameraUpdate = CameraUpdateFactory.newLatLngZoom(DEFAULT_LOCATION, DEFAULT_ZOOM)
        mMap!!.moveCamera(cameraUpdate)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        setDefaultLocation()

        mMap!!.apply {
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isMapToolbarEnabled = false
            uiSettings.setAllGesturesEnabled(false)

            mapType = GoogleMap.MAP_TYPE_NORMAL
        }

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
//        onAddCircle(roomData.distance)
    }

    fun getCurrentAddress(latLng: LatLng): String {
        //지오코더: GPS를 주소로 변환
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        val addresses: List<Address>?
        try {
            addresses = geocoder.getFromLocation(
                latLng.latitude,
                latLng.longitude,
                1
            )
        } catch (ioException: IOException) {
            //네트워크 문제
            Toast.makeText(context, "지오코더 서비스 사용불가", Toast.LENGTH_LONG).show()
            return "지오코더 사용불가"
        } catch (illegalArgumentException: IllegalArgumentException) {
            Toast.makeText(context, "잘못된 GPS 좌표", Toast.LENGTH_LONG).show()
            return "잘못된 GPS 좌표"
        }

        return if (addresses == null || addresses.isEmpty()) {
            Toast.makeText(context, "주소 발견 불가", Toast.LENGTH_LONG).show()
            "주소 발견 불가"
        } else {
            val address = addresses[0]
            address.getAddressLine(0).toString()
        }
    }

    //마커 , 원추가
    private fun onAddCircle(meter: Double) {
        // 반경 1KM원
        circle?.remove()
        circle = mMap!!.addCircle(
            CircleOptions().center(
                if (currentPosition != null) {
                    LatLng(
                        currentPosition!!.latitude,
                        currentPosition!!.longitude
                    )
                } else {
                    defaultLocation
                }
            ) //원점
                .radius(meter) //반지름 단위 : m
                .strokeWidth(2f) //선너비 0f : 선없음
                .strokeColor(ContextCompat.getColor(requireContext(), R.color.blue))
                .fillColor(ContextCompat.getColor(requireContext(), R.color.blue_trans)) //배경색
        )
    }

    fun setCurrentLocation(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        val cameraUpdate = CameraUpdateFactory.newLatLng(currentLatLng)
        mMap!!.moveCamera(cameraUpdate)
    }

    private fun checkLocationServicesStatus(): Boolean {
        val locationManager =
            activity?.getSystemService(AppCompatActivity.LOCATION_SERVICE) as LocationManager
        return (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        mMap.let { map ->
            outState.putParcelable(KEY_LOCATION, currentPosition)
        }
        super.onSaveInstanceState(outState)
    }

    // OpenVidu 토큰을 요청
    // sessionId : 토큰을 원하는 OpenVidu 세션
    private fun getToken(sessionId: String) {
        try {
            // Session Request
            val sessionBody = "{\"customSessionId\": \"$sessionId\"}"
                .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            httpClient?.httpCall(
                "/api/sessions",
                "POST",
                "application/json",
                sessionBody,
                object : Callback {
                    @Throws(IOException::class)
                    override fun onResponse(call: Call, response: Response) {
                        Log.d(TAG, "responseString: " + response.body.string())

                        // Token Request
                        val tokenBody =
                            "{}".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        httpClient!!.httpCall(
                            "/api/sessions/$sessionId/connections",
                            "POST",
                            "application/json",
                            tokenBody,
                            object : Callback {
                                override fun onResponse(call: Call, response: Response) {
                                    var responseString: String? = null
                                    try {
                                        responseString = response.body.string()
                                    } catch (e: IOException) {
                                        Log.e(TAG, "Error getting body", e)
                                    }
                                    if (responseString != null) {
                                        getTokenSuccess(responseString, sessionId)
                                    }
                                }

                                override fun onFailure(call: Call, e: IOException) {
                                    Log.e(
                                        TAG,
                                        "Error POST /api/sessions/SESSION_ID/connections",
                                        e
                                    )
                                    connectionError(APPLICATION_SERVER_URL)
                                }
                            })
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "Error POST /api/sessions", e)
                        connectionError(APPLICATION_SERVER_URL)
                    }
                })
        } catch (e: IOException) {
            Log.e(TAG, "Error getting token", e)
            e.printStackTrace()
            connectionError(APPLICATION_SERVER_URL)
        }
    }

    // 토큰을 얻으면 Session, LocalParticipant를 생성, 카메라를 캡쳐한다.
    private fun getTokenSuccess(token: String, sessionId: String) {
        // Initialize our session
        Log.d(TAG, "getTokenSuccess: fragment : $this")
        session = SessionGame(
            sessionId,
            token,
            binding.pager,
            this
        )

        // Initialize our local participant and start local camera
        val participantName = user.nickname
        val localParticipant =
            LocalParticipantGame(
                participantName,
                session!!,
                requireContext(),
                binding.localGlSurfaceView
            )
        localParticipant.startCamera()

        activity?.runOnUiThread {
            binding.mainParticipant.text = participantName
            binding.mainParticipant.setPadding(20, 3, 20, 3)

        }

        // Initialize and connect the websocket to OpenVidu Server
        startWebSocket()
    }

    private fun startWebSocket() {
        session?.let {

            CustomWebSocketGame(session!!, this@GameViewFragment).apply {
                this.execute()
                session?.setWebSocket(this)
            }
        }
    }

    private fun connectionError(url: String?) {
        val myRunnable = Runnable {
            val toast: Toast =
                Toast.makeText(context, "Error connecting to $url", Toast.LENGTH_LONG)
            toast.show()
        }
        Handler(requireContext().mainLooper).post(myRunnable)
    }

    private fun initViews() {
        val rootEglBase = EglBase.create()
        binding.localGlSurfaceView.init(rootEglBase.eglBaseContext, null)
        binding.localGlSurfaceView.setMirror(true)
        binding.localGlSurfaceView.setEnableHardwareScaler(true)
        binding.localGlSurfaceView.setZOrderMediaOverlay(true)
    }

    fun createRemoteParticipantVideo(remoteParticipant: RemoteParticipantGame) {
        val mainHandler: Handler = Handler(requireContext().mainLooper)
        val myRunnable = Runnable {
//            val rowView: View =
//                this.layoutInflater.inflate(R.layout.peer_video, null)
//            val lp = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            )
//            lp.setMargins(0, 0, 0, 20)
//            rowView.layoutParams = lp
//            val rowId = View.generateViewId()
//            rowView.id = rowId
//            binding.pager.addView(rowView)
//            val videoView =
//                (rowView as ViewGroup).getChildAt(0) as SurfaceViewRenderer
//            remoteParticipant.videoView = videoView
//            videoView.setMirror(false)
//            val rootEglBase = EglBase.create()
//            videoView.init(rootEglBase.eglBaseContext, null)
//            videoView.setZOrderMediaOverlay(true)
//            val textView = rowView.getChildAt(1)
//            remoteParticipant.participantNameText = textView as TextView
//            remoteParticipant.view = rowView
//            remoteParticipant.participantNameText!!.text = remoteParticipant.participantName
//            remoteParticipant.participantNameText!!.setPadding(20, 3, 20, 3)
        }
        mainHandler.post(myRunnable)
    }

    fun setRemoteMediaStream(stream: MediaStream, remoteParticipant: RemoteParticipantGame) {
        val videoTrack = stream.videoTracks[0]
        videoTrack.addSink(remoteParticipant.videoView)
        activity?.runOnUiThread {
            remoteParticipant.videoView?.visibility = View.VISIBLE
        }
    }

    fun leaveSession() {
        session?.leaveSession()
        httpClient?.dispose()
    }

    private fun arePermissionGranted(): Boolean {
        return PermissionHelper.hasCameraPermission(requireContext()) && PermissionHelper.hasAudioPermission(
            requireContext()
        )
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // 위치서비스 활성화 여부 check
        if (!checkLocationServicesStatus()) {
            showDialogForLocationServiceSetting()
        } else {
            if (PermissionHelper.hasLocationPermission(requireContext())) {
                mFusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    null
                )
                if (mMap != null) mMap!!.isMyLocationEnabled = true
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
                        context,
                        "위치 서비스가 꺼져 있어, 현재 위치를 확인할 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    //여기부터는 GPS 활성화를 위한 메소드들
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

    private fun showPermissionDialog(
        msg: String,
        positive: String,
        negative: String
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


    private lateinit var chaserVideoList: List<GameVideoFragment>
    private lateinit var runnerVideoList: List<GameVideoFragment>
    private lateinit var sensorValue: String


    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val r = sqrt(x.pow(2) + y.pow(2) + z.pow(2))

            Log.d("MainActivity", "onSensorChanged: x: $x, y: $y, z: $z, R: $r")
            val xrAngle = (90 - acos(x / r) * 180 / PI).toFloat()
            val yrAngle = (90 - acos(y / r) * 180 / PI).toFloat()

            sensorValue = String.format(
                "x-rotation: %.1f\u00B0 \n y-rotation: %.1f\u00B0", xrAngle, yrAngle
            )
            Log.d(TAG, "onSensorChanged: sensorValue ${sensorValue}")
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    companion object {
        private const val TAG = "추노_GameViewFragment"
        private const val NUM_PAGES = 5
        private const val UPDATE_INTERVAL = 1000L // 1초
        private const val MIN_UPDATE_INTERVAL = 500L // 0.5초
        private const val MIN_UPDATE_DISTANCE = 10f // 10m
        private const val DEFAULT_ZOOM = 15f
        private const val KEY_LOCATION = "location"
    }

    private inner class GameViewFragmentAdapter(fragment: Fragment) :
        FragmentStateAdapter(fragment) {

        override fun getItemCount(): Int = NUM_PAGES

        override fun createFragment(position: Int): Fragment {

            return ShopFragment()
        }
    }
}
