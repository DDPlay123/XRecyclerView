package com.side.project.customrecyclerview.customView

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.side.project.customrecyclerview.R
import com.side.project.customrecyclerview.databinding.XRecyclerViewHeaderBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Create by 光廷 on 2023/02/07
 * 功能：XRecyclerView 頂部，一般用於刷新資料。
 */
class XRecyclerViewHeader(context: Context, attrs: AttributeSet?) :
    ConstraintLayout(context, attrs) {
    val binding = XRecyclerViewHeaderBinding.inflate(LayoutInflater.from(context), this, true)
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = KEY_DATASTORE)

    // 刷新狀態
    private var mState: XRecyclerViewState = XRecyclerViewState.STATE_NORMAL

    // 箭頭動畫
    private var mRotateUpAnim: Animation = RotateAnimation(
        0.0f, -180.0f,
        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
        0.5f
    )

    private var mRotateDownAnim: Animation = RotateAnimation(
        -180.0f, 0.0f,
        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF,
        0.5f
    )

    constructor(context: Context) : this(context, null)

    init {
        mRotateUpAnim.duration = ROTATE_ANIM_DURATION.toLong()
        mRotateUpAnim.fillAfter = true

        mRotateDownAnim.duration = ROTATE_ANIM_DURATION.toLong()
        mRotateDownAnim.fillAfter = true
    }

    fun getTvState(): TextView = binding.tvState

    fun setState(state: XRecyclerViewState) {
        if (state == mState) return

        if (state == XRecyclerViewState.STATE_REFRESHING) {
            // 顯示HUD
            binding.apply {
                imgArrow.clearAnimation()
                imgArrow.invisible()
                customLoading.visible()
            }
        } else {
            // 顯示箭頭
            binding.apply {
                imgArrow.visible()
                customLoading.invisible()
            }
        }

        when (state) {
            XRecyclerViewState.STATE_NORMAL -> binding.apply {
                imgArrow.setImageResource(R.drawable.ic_arrow_bottom)

                if (mState == XRecyclerViewState.STATE_READY)
                    imgArrow.animation = mRotateDownAnim
                if (mState == XRecyclerViewState.STATE_REFRESHING)
                    imgArrow.clearAnimation()

                tvState.text = context.getString(R.string.header_hint_normal)
            }

            XRecyclerViewState.STATE_READY -> binding.apply {
                if (mState != XRecyclerViewState.STATE_READY) {
                    imgArrow.clearAnimation()
                    imgArrow.startAnimation(mRotateUpAnim)

                    tvState.text = context.getString(R.string.header_hint_ready)
                }
            }

            XRecyclerViewState.STATE_REFRESHING -> binding.apply {
                tvState.text = context.getText(R.string.header_hint_loading)
            }

            XRecyclerViewState.STATE_SUCCESS -> binding.apply {
                tvState.text = context.getText(R.string.header_hint_success)
                putUpdateTime(System.currentTimeMillis())
            }

            XRecyclerViewState.STATE_FAILED -> binding.apply {
                tvState.text = context.getText(R.string.header_hint_failed)
                putUpdateTime(System.currentTimeMillis())
            }
        }

        mState = state
    }

    fun setVisibleHeight(h: Int) {
        val lp = binding.root.layoutParams as LinearLayout.LayoutParams
        lp.height = if (h < 0) 0 else h
        binding.root.layoutParams = lp
    }

    fun getVisibleHeight(): Int = binding.root.layoutParams.height

    // 顯示上次更新的文字描述
    fun refreshUpdatedAtValue() {
        // 上次更新時間
        val lastUpdateTime = getUpdateTime()
        // 當前時間
        val currentTime = System.currentTimeMillis()
        // 比較時間
        val timePassed = currentTime - lastUpdateTime

        val timeIntoFormat: Long
        val updateAtValue: String

        if (lastUpdateTime == -1L) {
            updateAtValue = context.getString(R.string.hint_not_updated_yet)
        } else if (timePassed < 0) {
            updateAtValue = context.getString(R.string.hint_time_error)
        } else if (timePassed < ONE_MINUTE) {
            updateAtValue = context.getString(R.string.hint_updated_just_now)
        } else if (timePassed < ONE_HOUR) {
            timeIntoFormat = timePassed / ONE_MINUTE
            val value = timeIntoFormat.toString() + context.getString(R.string.minute)
            updateAtValue = String.format(context.getString(R.string.hint_updated_at), value)
        } else if (timePassed < ONE_DAY) {
            timeIntoFormat = timePassed / ONE_HOUR
            val value = timeIntoFormat.toString() + context.getString(R.string.hour)
            updateAtValue = String.format(context.getString(R.string.hint_updated_at), value)
        } else if (timePassed < ONE_MONTH) {
            timeIntoFormat = timePassed / ONE_DAY
            val value = timeIntoFormat.toString() + context.getString(R.string.day)
            updateAtValue = String.format(context.getString(R.string.hint_updated_at), value)
        } else if (timePassed < ONE_YEAR) {
            timeIntoFormat = timePassed / ONE_MONTH
            val value = timeIntoFormat.toString() + context.getString(R.string.month)
            updateAtValue = String.format(context.getString(R.string.hint_updated_at), value)
        } else {
            timeIntoFormat = timePassed / ONE_YEAR
            val value = timeIntoFormat.toString() + context.getString(R.string.year)
            updateAtValue = String.format(context.getString(R.string.hint_updated_at), value)
        }

        binding.tvStateTime.text = updateAtValue
    }

    // 上傳更新時間
    private fun putUpdateTime(value: Long) = runBlocking {
        context.dataStore.edit {
            it[longPreferencesKey(UPDATE_TIME)] = value
        }
    }

    // 取得更新時間
    private fun getUpdateTime(): Long = runBlocking {
        return@runBlocking context.dataStore.data.map {
            it[longPreferencesKey(UPDATE_TIME)] ?: -1L
        }.first()
    }
}