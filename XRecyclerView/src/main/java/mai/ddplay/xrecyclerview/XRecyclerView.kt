package mai.ddplay.xrecyclerview

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil

/**
 * Create by 光廷 on 2023/02/07
 * 功能：XRecyclerView 本體，可上滑刷新、下滑更新。
 * 來源：https://github.com/limxing/LFRecyclerView-Android
 *
 * 註：
 * 1. 使用 XRecyclerView 不須再定義 LayoutManager
 * 2. 目前只能使用垂直頁面。
 *
 * 使用姿勢：
 * 1. 初始化 RecyclerView 時，要先定義
 *    => setAutoLoadMore()、setLoadMore()、setRefresh()
 * 2. 當資料回傳時，要主動呼叫
 *    => stopRefresh() 或 stopLoadMore()
 *
 * (options). 如果有需要，可以繼承 Interface
 *    => OnItemClickListener、XRecyclerViewListener、XRecyclerViewScrollChange
 */
class XRecyclerView(context: Context, attrs: AttributeSet?) : RecyclerView(context, attrs) {
    companion object {
        const val OFFSET_RADIO = 1.8f
        const val PULL_LOAD_MORE_DELTA = 50
        const val SCROLL_DURATION = 400
        const val SCROLL_BACK_HEADER = 4
        const val SCROLL_BACK_FOOTER = 3
    }

    constructor(context: Context) : this(context, null)

    private lateinit var mLayoutManager: LinearLayoutManager

    private var mScroller: Scroller
    private lateinit var mAdapter: XRecyclerViewAdapter
    private var isAutoLoadMore = false
    private var isLoadMore = true
    private var isRefresh = true
    private var mPullLoad = false

    private var itemListener: OnItemClickListener? = null
    private var mRecyclerViewListener: XRecyclerViewListener? = null
    private var scrollerListener: XRecyclerViewScrollChange? = null

    private var recyclerViewHeader: XRecyclerViewHeader
    private var recyclerViewFooter: XRecyclerViewFooter

    private var mHeaderViewHeight = 0
    private var mScrollBack = 0
    // 上一次Y值
    private var mLastY = 0f
    // 是否正在更新
    private var mPullRefreshing = false
    // 是否正在加載
    private var mPullLoading = false

    private lateinit var adapter: Adapter<*>
    private var observer: XAdapterDataObserve? = null

    private var currentLastNum = 0
    private var num = 0

    // HeadView / FooterView
    private var headerView: View? = null
    private var footerView: View? = null

    // 初始化
    init {
        mScroller = Scroller(context, DecelerateInterpolator())
        recyclerViewHeader = XRecyclerViewHeader(context)
        recyclerViewFooter = XRecyclerViewFooter(context)

        recyclerViewHeader.viewTreeObserver?.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                mHeaderViewHeight = recyclerViewHeader.getContentHeight()
                viewTreeObserver.removeGlobalOnLayoutListener(this)
            }
        })

        val linearLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        layoutManager = linearLayoutManager

        addOnScrollListener(object : OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
//                super.onScrolled(recyclerView, dx, dy)
                onScrollChange(recyclerView, dx, dy)
            }
        })
        observer = XAdapterDataObserve()
        // 調整顯示Header
        resetHeaderHeight()
    }

    /**
     * 可調用方法
     */
    // 設定是否開啟自動載入更多功能。預設是False
    fun setAutoLoadMore(autoLoadMore: Boolean) {
        isAutoLoadMore = autoLoadMore
    }

    // 定義是否開啟刷新更多功能。預設是True
    fun setRefresh(b: Boolean) {
        isRefresh = b
    }

    // 定義是否開啟載入更多功能。預設是True
    fun setLoadMore(b: Boolean) {
        isLoadMore = b
        if (isLoadMore) recyclerViewFooter.show()
        else recyclerViewFooter.hide()
    }

    // 強制停止全部刷新/加載。
    fun stopAll() {
        stopRefresh()
        stopLoadMore()
    }

    // 當資料回傳時，需主動停止刷新。
    fun stopRefresh(isSuccess: Boolean = true) {
        if (mPullRefreshing) {
            if (isSuccess)
                recyclerViewHeader.setState(XRecyclerViewState.STATE_SUCCESS)
            else
                recyclerViewHeader.setState(XRecyclerViewState.STATE_FAILED)

            recyclerViewHeader.postDelayed({
                mPullRefreshing = false
                resetHeaderHeight()
            }, 500)
        }
    }

    // 當資料回傳時，需主動停止呼叫更多資料。
    fun stopLoadMore() {
        if (mPullLoading) {
            mPullLoad = false
            mPullLoading = false
            recyclerViewFooter.setState(XRecyclerViewState.STATE_NORMAL)
            resetFooterHeight()
        }
    }

    // 當沒有更多資料時，可調用此方法
    fun setNoMoreData() {
        isLoadMore = true
        recyclerViewFooter.setNoMoreData()
    }

    fun getMyLayoutManager(): LinearLayoutManager =
        mLayoutManager

    // 滾動到指定位置
    fun scrollToPositionWithOffset(pos: Int, bias: Int) {
        mLayoutManager.scrollToPositionWithOffset(pos, bias)
    }

    // 第一個顯示的位置
    fun findFirstVisibleItemPosition() = mLayoutManager.findFirstVisibleItemPosition()

    // 最後一個顯示位置
    fun findLastVisibleItemPosition() = mLayoutManager.findLastVisibleItemPosition()

    // 設定 HeaderView
    fun setHeaderView(headerView: View) {
        this.headerView = headerView
        if (::mAdapter.isInitialized)
            mAdapter.setHeaderView(headerView)
    }

    // 設定 FooterView
    fun setFooterView(footerView: View) {
        this.footerView = footerView
        if (::mAdapter.isInitialized)
            mAdapter.setFooterView(footerView)
    }

    fun setOnItemClickListener(itemListener: OnItemClickListener) {
        this.itemListener = itemListener
    }

    fun setXRecyclerViewListener(l: XRecyclerViewListener) {
        mRecyclerViewListener = l
    }

    fun setScrollChangeListener(listener: XRecyclerViewScrollChange) {
        scrollerListener = listener
    }

    /**
     * 使用預設 Item 動畫
     */
//    override fun setItemAnimator(animator: ItemAnimator?) {
//        super.setItemAnimator(DefaultItemAnimator())
//    }

    /**
     * 由於此處已定義 LayoutManager，所以實際使用時，不須再定義一次。
     */
    override fun setLayoutManager(layout: LayoutManager?) {
        super.setLayoutManager(layout)
        mLayoutManager = layout as LinearLayoutManager
    }

    /**
     * 調整 Adapter 及定義 XRecyclerViewAdapter 基本功能。
     */
    override fun setAdapter(adapter: Adapter<*>?) {
        if (adapter == null) return

        this.adapter = adapter
        if (observer == null)
            observer = XAdapterDataObserve()

        adapter.registerAdapterDataObserver(observer ?: return)

        mAdapter = XRecyclerViewAdapter(adapter as Adapter<ViewHolder>)
        mAdapter.setRecyclerViewHeader(recyclerViewHeader)
        mAdapter.setRecyclerViewFooter(recyclerViewFooter)

        headerView?.let { mAdapter.setHeaderView(it) }
        footerView?.let { mAdapter.setFooterView(it) }

        mAdapter.setLoadMore(isLoadMore)
        mAdapter.setRefresh(isRefresh)
        itemListener?.let { mAdapter.setOnItemClickListener(it) }

        super.setAdapter(mAdapter)
    }

    /**
     * 計算滾動用以調整 Header 和 Footer
     */
    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            if (mScrollBack == SCROLL_BACK_HEADER)
                recyclerViewHeader.setVisibleHeight(mScroller.currY)
            else
                recyclerViewFooter.setBottomMargin(mScroller.currY)

            postInvalidate()
        }
        super.computeScroll()
    }

    /**
     * Item事件處理
     */
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (mLastY == -1F || mLastY == 0F) {
            mLastY = ev.rawY
            if (!mPullRefreshing && mLayoutManager.findFirstVisibleItemPosition() <= 1)
                recyclerViewHeader.refreshUpdatedAtValue()
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> mLastY = ev.rawY

            MotionEvent.ACTION_MOVE -> {
                val moveY = ev.rawY - mLastY
                mLastY = ev.rawY
                if (isRefresh && !mPullLoad && mLayoutManager.findFirstVisibleItemPosition() <= 1 && (recyclerViewHeader.getVisibleHeight() > 0 || moveY > 0))
                    updateHeaderHeight(moveY / OFFSET_RADIO)
                else if (isLoadMore && !mPullRefreshing && !mPullLoad && mLayoutManager.findLastVisibleItemPosition() == mAdapter.itemCount - 1 && (recyclerViewFooter.getBottomMargin() > 0 || moveY < 0) && adapter.itemCount > 0)
                    updateFooterHeight(-moveY / OFFSET_RADIO)
            }

            MotionEvent.ACTION_UP -> {
                mLastY = -1F // reset
                if (!mPullRefreshing && mLayoutManager.findFirstVisibleItemPosition() == 0) {
                    // invoke refresh
                    if (isRefresh && recyclerViewHeader.getVisibleHeight() > mHeaderViewHeight) {
                        mPullRefreshing = true
                        recyclerViewHeader.setState(XRecyclerViewState.STATE_REFRESHING)
                        mRecyclerViewListener?.onRefresh()
                    }
                }
                if (isLoadMore && mPullLoading && mLayoutManager.findLastVisibleItemPosition() == mAdapter.itemCount - 1 && recyclerViewFooter.getBottomMargin() > PULL_LOAD_MORE_DELTA) {
                    recyclerViewFooter.setState(XRecyclerViewState.STATE_REFRESHING)
                    mPullLoad = true
                    startLoadMore()
                }
                resetHeaderHeight()
                resetFooterHeight()
            }
        }
        return super.onTouchEvent(ev)
    }

    /**
     * 處理 Header 高度
     */
    private fun updateHeaderHeight(delta: Float) {
        recyclerViewHeader.setVisibleHeight(delta.toInt() + recyclerViewHeader.getVisibleHeight())
        if (isRefresh && !mPullRefreshing) {
            if (recyclerViewHeader.getVisibleHeight() > mHeaderViewHeight)
                recyclerViewHeader.setState(XRecyclerViewState.STATE_READY)
            else
                recyclerViewHeader.setState(XRecyclerViewState.STATE_NORMAL)
        }
    }

    private fun resetHeaderHeight() {
        val height: Int = recyclerViewHeader.getVisibleHeight()
        if (height == 0 || (mPullRefreshing && height <= mHeaderViewHeight)) return

        var finalHeight = 0
        if (mPullRefreshing && height > mHeaderViewHeight)
            finalHeight = mHeaderViewHeight

        mScrollBack = SCROLL_BACK_HEADER
        mScroller.startScroll(0, height, 0, finalHeight - height, SCROLL_DURATION)
        invalidate()
    }

    /**
     * 處理 Footer 高度
     */
    private fun updateFooterHeight(delta: Float) {
        val height = recyclerViewFooter.getBottomMargin() + delta.toInt()
        if (isLoadMore) {
            if (height > PULL_LOAD_MORE_DELTA) {
                recyclerViewFooter.setState(XRecyclerViewState.STATE_READY)
                mPullLoading = true
            } else {
                recyclerViewFooter.setState(XRecyclerViewState.STATE_NORMAL)
                mPullLoading = false
                mPullLoad = false
            }
        }
        recyclerViewFooter.setBottomMargin(height)
    }

    private fun resetFooterHeight() {
        val bottomMargin = recyclerViewFooter.getBottomMargin()
        if (bottomMargin > 0) {
            mScrollBack = SCROLL_BACK_FOOTER
            mScroller.startScroll(0, bottomMargin, 0, -bottomMargin, SCROLL_DURATION)
            invalidate()
        }
    }

    /**
     * 其他
     */
    private fun startLoadMore() {
        if (mRecyclerViewListener != null) {
            recyclerViewFooter.setState(XRecyclerViewState.STATE_REFRESHING)
            mRecyclerViewListener?.onLoadMore()
        }
    }

    private fun onScrollChange(view: View?, i: Int, i1: Int) {
        if (mAdapter.itemHeight > 0 && num == 0)
            num = ceil((height / mAdapter.itemHeight).toDouble()).toInt()

        if (isAutoLoadMore && (mLayoutManager.findLastVisibleItemPosition() == mAdapter.itemCount - 1) && currentLastNum != mLayoutManager.findLastVisibleItemPosition() && num > 0 && adapter.itemCount > num && !mPullLoading) {
            currentLastNum = mLayoutManager.findLastVisibleItemPosition()
            mPullLoading = true
            startLoadMore()
        }

        scrollerListener?.onRecyclerViewScrollChange(view, i, i1)
    }

    /**
     * 重新定義 監聽事件
     */
    interface OnItemClickListener {
        fun onClick(position: Int)

        fun onLongClick(position: Int)
    }

    interface XRecyclerViewListener {
        fun onRefresh()
        fun onLoadMore()
    }

    interface XRecyclerViewScrollChange {
        fun onRecyclerViewScrollChange(view: View?, i: Int, i1: Int)
    }

    /**
     * 觀察Adapter更新變化，再根據Header、Footer調整
     */
    inner class XAdapterDataObserve : AdapterDataObserver() {
        override fun onChanged() {
            mAdapter.notifyDataSetChanged()
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            mAdapter.notifyItemRangeChanged(positionStart + mAdapter.mHeaderCount, itemCount)
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            mAdapter.notifyItemRangeChanged(positionStart + mAdapter.getHeaderViewCount(), itemCount, payload)
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            mAdapter.notifyItemRangeInserted(positionStart + mAdapter.getHeaderViewCount(), itemCount)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            mAdapter.notifyItemRangeRemoved(positionStart + mAdapter.getHeaderViewCount(), itemCount)
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            mAdapter.notifyItemMoved(fromPosition + mAdapter.getHeaderViewCount(), toPosition + mAdapter.getHeaderViewCount())
        }
    }
}