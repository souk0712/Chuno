package com.leesfamily.chuno.util.custom

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import com.leesfamily.chuno.R
import com.leesfamily.chuno.databinding.CreateRoomDialog1Binding
import com.leesfamily.chuno.room.roomlist.RoomListFragment
import com.leesfamily.chuno.util.custom.DialogSizeHelper.dialogFragmentResize
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


class CreateRoomDialog1(
    context: Context,
    createRoomDialogInterface: CreateRoomDialogInterface
) : DialogFragment(), View.OnClickListener, MyCustomDialogInterface {
    private lateinit var binding: CreateRoomDialog1Binding
    private val createRoomViewModel: CreateRoomViewModel by activityViewModels()

    private var createRoomDialogInterface: CreateRoomDialogInterface? = null

    var mContext: Context? = null

    // 최대 인원
    var step = 2
    var defValue = 6
    var minValue = 4
    var maxValue = 10
    var curValue = defValue

    var isReadOnly: Boolean = false

    // 방제목
    var title: String? = null

    // 비밀번호
    var password: String? = null

    // 예약일자
    var reservationDate: String? = null
    var yearValue: Int? = null
    var monthValue: Int? = null
    var dayValue: Int? = null

    // 예약시간
    var reservationHour: Int? = null
    var reservationMin: Int? = null

    var isPublic: Boolean = true

    var isToday: Boolean = true

    // 인터페이스 연결
    init {
        this.createRoomDialogInterface = createRoomDialogInterface
        mContext = context
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        mContext?.dialogFragmentResize(this, 0.8f)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = CreateRoomDialog1Binding.inflate(inflater, container, false)
        dialog?.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        binding.nextButton.setOnClickListener(this)
        binding.publicButton.setOnClickListener(this)
        binding.privateButton.setOnClickListener(this)
        binding.closeButton.setOnClickListener(this)
        binding.calendarView.setOnClickListener(this)

        val numberView = binding.number.apply {
            text = defValue.toString()
        }
        binding.numberPlusBtn.apply {
            setOnClickListener {
                curValue += step
                checkRange(numberView, curValue, minValue, maxValue)
            }
        }

        binding.numberMinusBtn.apply {
            setOnClickListener {
                curValue -= step
                checkRange(numberView, curValue, minValue, maxValue)
            }
        }

        setReadOnly()
        isCancelable = false
        return binding.root
    }


    private fun checkRange(numberView: TextView, curValue: Int, minValue: Int, maxValue: Int) {

        when {
            curValue in minValue..maxValue -> {
                Log.d(TAG, "checkRange: minValue <= curValue <= maxValue")
                numberView.text = curValue.toString()
                binding.numberText.visibility = View.GONE
            }
            curValue < minValue -> {
                Log.d(TAG, "checkRange: minValue > curValue")
                numberView.text = minValue.toString()
                this.curValue = minValue
                binding.numberText.apply {
                    text = getString(R.string.min_person_count_message)
                    visibility = View.VISIBLE
                    setVisibilityGone(this)
                }
                binding.roomMaxCountEdit.animation =
                    AnimationUtils.loadAnimation(mContext, R.anim.shake)
            }
            else -> {
                Log.d(TAG, "checkRange: curValue > maxValue")
                numberView.text = maxValue.toString()
                this.curValue = maxValue
                binding.numberText.apply {
                    text = getString(R.string.max_person_count_message)
                    visibility = View.VISIBLE
                    setVisibilityGone(this)
                }
                binding.roomMaxCountEdit.animation =
                    AnimationUtils.loadAnimation(mContext, R.anim.shake)
            }
        }

    }

    private fun setVisibilityGone(textView: TextView) {
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                binding.root.post {
                    textView.visibility = View.GONE
                }
                timer.cancel()
            }
        }, 1000)
    }

    private fun saveViewModel() {
        createRoomViewModel.setCurValue(curValue)
        reservationHour?.let { createRoomViewModel.setReservationHour(it) }
        reservationMin?.let { createRoomViewModel.setReservationMin(it) }
        password?.let { createRoomViewModel.setPw(it) }
        monthValue?.let { createRoomViewModel.setMonthValue(it) }
        dayValue?.let { createRoomViewModel.setDayValue(it) }
        createRoomViewModel.setIsPublic(isPublic)
        title?.let { createRoomViewModel.setTitle(it) }
        yearValue?.let { createRoomViewModel.setYearValue(it) }
        isReadOnly.let { createRoomViewModel.setReadOnly(it) }
        isPublic.let { createRoomViewModel.setIsPublic(it) }
        isToday.let { createRoomViewModel.setIsToday(it) }
    }

    override fun onClick(view: View?) {
        when (view) {
            binding.nextButton -> {
                if (checkEmptyInput(view)) {
                    createRoomDialogInterface?.onNextButtonClicked(binding.root)
                    if (!isReadOnly)
                        saveViewModel()
                    dismiss()
                }
            }
            binding.closeButton -> {
                dismiss()
            }
            binding.calendarView -> {
                if (!isReadOnly) {
                    DateTimePicker(mContext!!, object : DateTimePickerInterface {

                        override fun onOkButtonClicked(
                            date: Calendar?,
                            hourValue: Int?,
                            minuteValue: Int?,
                            isToday: Boolean
                        ) {
                            val df: DateFormat = SimpleDateFormat("yyyy-MM-dd")
                            this@CreateRoomDialog1.isToday = isToday
                            reservationDate = date?.time?.let { df.format(it) }

                            yearValue = date?.get(Calendar.YEAR)
                            monthValue = date?.get(Calendar.DAY_OF_MONTH)
                            dayValue = date?.get(Calendar.DATE)

                            reservationHour = hourValue
                            reservationMin = minuteValue

                            binding.reservationDate.text = reservationDate
                            binding.reservationHour.text = reservationHour.toString()
                            binding.reservationMin.text = reservationMin.toString()
                        }

                    }).show(childFragmentManager, "datePicker")
                }
            }
            binding.publicButton -> {
                if (!isPublic) {
                    binding.publicButton.background =
                        ContextCompat.getDrawable(mContext!!, R.drawable.btn_pressed_left)
                    binding.privateButton.background =
                        ContextCompat.getDrawable(mContext!!, R.drawable.btn_right_default_selector)
                    isPublic = !isPublic
                }
                binding.passwordEdit.visibility = View.GONE
                binding.passwordText.visibility = View.GONE
            }

            binding.privateButton -> {
                if (isPublic) {
                    binding.publicButton.background =
                        ContextCompat.getDrawable(mContext!!, R.drawable.btn_left_default_selector)
                    binding.privateButton.background =
                        ContextCompat.getDrawable(mContext!!, R.drawable.btn_pressed_right)
                    isPublic = !isPublic
                }
                binding.passwordEdit.visibility = View.VISIBLE
                binding.passwordText.visibility = View.VISIBLE
            }
        }
    }

    private fun setReadOnly() {
        if (isReadOnly) {
            Log.d(TAG, "setReadOnly: $isReadOnly")
            binding.titleView.text = getString(R.string.room_info_text)
            binding.roomTitleEdit.isEnabled = !isReadOnly
            binding.passwordEdit.isEnabled = !isReadOnly
            binding.reservationDate.text = reservationDate
            binding.reservationHour.text = reservationHour.toString()
            binding.reservationMin.text = reservationMin.toString()
            binding.reservationDate.text = reservationDate
            binding.passwordEdit.visibility = View.GONE
            binding.passwordText.visibility = View.GONE
            binding.number.text = curValue.toString()
            binding.numberPlusBtn.isEnabled = false
            binding.numberMinusBtn.isEnabled = false
        }
    }

    private fun checkEmptyInput(view: View?): Boolean {
        if (!isReadOnly) {
            view?.let {
                if (binding.roomTitleEdit.text.toString().isEmpty()) {
                    showCustomDialog(0)
                    return false
                } else {
                    this.title = binding.roomTitleEdit.text.toString()
                }
                if (!isPublic) {
                    if (binding.passwordEdit.text.toString().isEmpty()) {
                        showCustomDialog(1)
                        return false
                    } else {
                        this.password = binding.passwordEdit.text.toString()
                    }
                }
                if (binding.reservationDate.text.toString().isEmpty() ||
                    binding.reservationHour.text.toString().isEmpty() ||
                    binding.reservationMin.text.toString().isEmpty()
                ) {
                    showCustomDialog(2)
                    return false
                } else {
//                this.reservationDate = binding.reservationDate.text
                }
                return true
            }
            return false
        }
        return true
    }

    private fun showCustomDialog(flag: Int) {
        MyCustomDialog(mContext!!, this).apply {
            when (flag) {
                0 -> {
                    message = getString(R.string.room_no_title_message)
                }
                1 -> {
                    message = getString(R.string.room_no_password_message)
                }
                2 -> {
                    message = getString(R.string.room_no_reservation_message)
                }
            }
            yesMsg = getString(R.string.ok)
            show()
        }
    }

    companion object {
        private const val TAG = "추노_CreateRoomDialog1"
    }

    override fun onYesButtonClicked() {
    }

    override fun onNoButtonClicked() {
    }
}