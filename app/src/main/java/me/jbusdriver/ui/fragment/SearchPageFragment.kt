package me.jbusdriver.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnPreDraw
import androidx.transition.TransitionManager
import me.jbusdriver.R
import me.jbusdriver.base.common.BaseFragment
import me.jbusdriver.base.common.C
import me.jbusdriver.base.inflate
import me.jbusdriver.base.toast
import me.jbusdriver.databinding.FragmentSearchPageBinding

/**
 * 底部样式的「搜索」页签。
 *
 * 录入态: 搜索框停在上半屏的中间, 下面是搜索历史(最多露 5 行, 多的收进 + 号)。
 * 搜索后: 搜索框升到页面顶部, 历史让位给结果页(带标签页)。
 *
 * 两种状态共用一套 ConstraintLayout, 位置靠搜索框的 verticalBias 切换 ——
 * 所以历史条数变了也不会把搜索框顶走, 详见 fragment_search_page.xml。
 */
class SearchPageFragment : BaseFragment() {

    private var binding: FragmentSearchPageBinding? = null

    private val history = ArrayList<String>()
    private var historyExpanded = false

    /** 搜索框升上去就不再退回来, 记一次就够 */
    private var searching = false

    private val historyPrefs by lazy {
        requireContext().getSharedPreferences(HISTORY_PREF, Context.MODE_PRIVATE)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSearchPageBinding.inflate(inflater, container, false)
        .also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        loadHistory()
        renderHistory()
        b.etSearchKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit(b.etSearchKeyword.text?.toString()); true
            } else false
        }
        // 放大镜和键盘上的「搜索」等价
        b.ivSearchAction.setOnClickListener { submit(b.etSearchKeyword.text?.toString()) }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun submit(word: String?) {
        val query = word?.trim().orEmpty()
        if (query.isEmpty()) {
            toast("关键字不能为空!")
            return
        }
        remember(query)
        hideKeyboard()
        enterSearching()
        childFragmentManager.beginTransaction()
            .replace(R.id.fl_search_result, SearchResultPagesFragment().apply {
                arguments = Bundle().apply { putString(C.BundleKey.Key_1, query) }
            })
            .commit()
    }

    /** 搜索框升到顶部, 历史让位给结果 */
    private fun enterSearching() {
        val b = binding ?: return
        if (searching) return
        searching = true
        TransitionManager.beginDelayedTransition(b.clSearchRoot)
        (b.llSearchBox.layoutParams as ConstraintLayout.LayoutParams).verticalBias = 0f
        // 改 layoutParams 不会自己触发布局, 得手动 request 一次, 上面那个动画才有得可动
        b.llSearchBox.requestLayout()
        b.svHistory.visibility = View.GONE
        b.flSearchResult.visibility = View.VISIBLE
    }

    /** 键盘一直顶着的话, 结果页的标签页点不到 */
    private fun hideKeyboard() {
        val token = view?.windowToken ?: return
        (viewContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(token, 0)
    }

    //region 搜索历史
    private fun loadHistory() {
        history.clear()
        historyPrefs.getString(KEY_HISTORY, null)
            ?.split(SEPARATOR)
            ?.filter { it.isNotBlank() }
            ?.let { history.addAll(it) }
    }

    /** 搜过的词提到最前, 重复的不留两份; 存够上限就把最老的挤掉 */
    private fun remember(word: String) {
        history.remove(word)
        history.add(0, word)
        while (history.size > HISTORY_MAX) history.removeAt(history.lastIndex)
        historyExpanded = false
        historyPrefs.edit().putString(KEY_HISTORY, history.joinToString(SEPARATOR)).apply()
        renderHistory()
    }

    /**
     * 历史词满行流式排 —— 一行放多个, 放不下才换行; 最多露 HISTORY_ROWS 行。
     *
     * 超出的词由 FlowLayout 自己按 maxRows 藏掉, 藏到了才摆「+」。
     * 关键词是单行录入的, 不会有换行符, 所以直接拿换行当分隔符存。
     */
    private fun renderHistory() {
        val b = binding ?: return
        val box = b.llHistory
        box.removeAllViews()
        if (history.isEmpty()) {
            b.svHistory.visibility = View.GONE
            return
        }
        // 搜索中历史是收起的, 别在这里又把它放出来
        if (!searching) b.svHistory.visibility = View.VISIBLE

        box.maxRows = if (historyExpanded) 0 else HISTORY_ROWS
        history.forEach { word ->
            val chip = viewContext.inflate(R.layout.layout_search_history_item, box) as TextView
            chip.text = word
            chip.setOnClickListener {
                b.etSearchKeyword.setText(word)
                b.etSearchKeyword.setSelection(word.length)
                submit(word)
            }
            box.addView(chip)
        }

        b.tvHistoryToggle.text = if (historyExpanded) "收起" else "+"
        b.tvHistoryToggle.visibility = View.GONE
        b.tvHistoryToggle.setOnClickListener {
            historyExpanded = !historyExpanded
            renderHistory()
        }
        // 有没有词被行数限制藏掉, 得等测量跑完才知道 —— preDraw 时 overflowCount 已经算好了
        box.doOnPreDraw {
            b.tvHistoryToggle.visibility =
                if (historyExpanded || box.overflowCount > 0) View.VISIBLE else View.GONE
        }
    }
    //endregion

    companion object {
        private const val HISTORY_PREF = "search_history"
        private const val KEY_HISTORY = "keywords"

        /** 关键词是单行录入的, 拿换行当分隔符最省事, 不用引 JSON */
        private const val SEPARATOR = "\n"

        /** 收起时露几行 */
        private const val HISTORY_ROWS = 5

        /** 最多存几条, 再老的就挤掉 */
        private const val HISTORY_MAX = 20

        fun newInstance() = SearchPageFragment()
    }
}
