package me.jbusdriver.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import me.jbusdriver.R
import me.jbusdriver.base.common.BaseFragment
import me.jbusdriver.base.common.C
import me.jbusdriver.base.toast
import me.jbusdriver.databinding.FragmentSearchPageBinding

/**
 * 底部样式的「搜索」页签: 关键字输入后把结果页(带标签页)嵌在同一个页面里,
 * 不再像抽屉样式那样跳到独立的搜索结果 Activity。
 */
class SearchPageFragment : BaseFragment() {

    private var binding: FragmentSearchPageBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = FragmentSearchPageBinding.inflate(inflater, container, false)
        .also { binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return
        b.btnSearch.setOnClickListener { submit(b.etSearchKeyword.text?.toString()) }
        b.etSearchKeyword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit(b.etSearchKeyword.text?.toString()); true
            } else false
        }
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
        hideKeyboard()
        childFragmentManager.beginTransaction()
            .replace(R.id.fl_search_result, SearchResultPagesFragment().apply {
                arguments = Bundle().apply { putString(C.BundleKey.Key_1, query) }
            })
            .commit()
    }

    /** 键盘一直顶着的话, 结果页的标签页点不到 */
    private fun hideKeyboard() {
        val token = view?.windowToken ?: return
        (viewContext.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(token, 0)
    }

    companion object {
        fun newInstance() = SearchPageFragment()
    }
}
