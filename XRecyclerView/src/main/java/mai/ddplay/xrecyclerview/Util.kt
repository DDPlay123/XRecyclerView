package mai.ddplay.xrecyclerview

import android.view.View

/**
 * 上拉、下滑狀態
 */
enum class XRecyclerViewState {
    STATE_NORMAL, STATE_READY, STATE_REFRESHING, STATE_SUCCESS, STATE_FAILED
}

/**
 * 各時間單位的豪秒值，用於判斷上次更新
 */
const val ONE_MINUTE: Long = 60 * 1000

const val ONE_HOUR: Long = 60 * ONE_MINUTE

const val ONE_DAY: Long = 24 * ONE_HOUR

const val ONE_MONTH: Long = 30 * ONE_DAY

const val ONE_YEAR: Long = 12 * ONE_MONTH

/**
 * 其他
 */
const val KEY_DATASTORE = "KEY_DATASTORE"
const val UPDATE_TIME = "UPDATE_TIME"

const val ROTATE_ANIM_DURATION = 180

/**
 * 擴充函數
 */
fun View.visible() { this.visibility = View.VISIBLE }

fun View.invisible() { this.visibility = View.INVISIBLE }

fun View.gone() { this.visibility = View.GONE }