package me.jbusdriver.mvp.presenter

import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import me.jbusdriver.base.mvp.BaseView
import me.jbusdriver.base.mvp.bean.PageInfo
import me.jbusdriver.base.mvp.model.BaseModel
import me.jbusdriver.base.mvp.presenter.AbstractRefreshLoadMorePresenterImpl
import me.jbusdriver.common.bean.ICollectCategory
import me.jbusdriver.common.bean.ILink
import me.jbusdriver.common.bean.db.ActressCategory
import me.jbusdriver.common.bean.db.Category
import me.jbusdriver.common.bean.db.ForumCategory
import me.jbusdriver.common.bean.db.LinkCategory
import me.jbusdriver.common.bean.db.MovieCategory
import me.jbusdriver.db.service.CategoryService
import me.jbusdriver.db.service.LinkService
import me.jbusdriver.mvp.ActressCollectContract
import me.jbusdriver.mvp.ForumCollectContract
import me.jbusdriver.mvp.MovieCollectContract
import me.jbusdriver.mvp.bean.CollectLinkWrapper
import me.jbusdriver.mvp.bean.convertDBItem
import me.jbusdriver.mvp.model.CollectModel
import org.jsoup.nodes.Document


abstract class BaseAbsCollectPresenter<V : BaseView.BaseListWithRefreshView, T : ICollectCategory> :
    AbstractRefreshLoadMorePresenterImpl<V, T>(), BaseCollectPresenter<T> {

    private val ancestor by lazy {
        when {
            this is MovieCollectContract.MovieCollectPresenter -> MovieCategory
            this is ActressCollectContract.ActressCollectPresenter -> ActressCategory
            this is ForumCollectContract.ForumCollectPresenter -> ForumCategory
            else -> LinkCategory
        }
    }

    override val collectGroupMap: MutableMap<Category, List<T>> = mutableMapOf()

    override val adapterDelegate: BaseCollectPresenter.CollectMultiTypeDelegate<T> =
        BaseCollectPresenter.CollectMultiTypeDelegate()


    override fun onFirstLoad() {
        //通过refresh加载，loadData4Page
        onRefresh()
    }

    /** 收藏按分类树一次性全部加载, 所以这里忽略 page 参数, 结束时直接 loadMoreEnd */
    override fun loadData4Page(page: Int) {
        Flowable.just(ancestor)
            .filter { ancestor.id != null }
            .flatMap { Flowable.fromIterable(CategoryService.queryCategoryTreeLike(it.id!!)) }
            .map { cate ->
                val parent = CollectLinkWrapper<T>(cate).apply {
                    adapterDelegate.needInjectType.add(level)
                }
                val list = LinkService.queryByCategory(cate)
                val items = mutableListOf<T>()
                list.forEach {
                    val mapValue = it.getLinkValue() as? T
                    if (mapValue != null) {
                        parent.addSubItem(CollectLinkWrapper(cate, mapValue).apply {
                            adapterDelegate.needInjectType.add(level)
                        })
                        items.add(mapValue)
                    }
                }
                collectGroupMap[cate] = items
                parent
            }

            .toList()
            .doOnSubscribe { mView?.showLoading() }
            .doAfterTerminate { mView?.dismissLoading() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy({
                mView?.showError(it)
            }, {
                mView?.resetList()
                mView?.showContents(it)
                mView?.loadMoreComplete()
                mView?.loadMoreEnd()

            })
            .addTo(rxManager)
    }

    override fun onRefresh() {
        mView?.showLoading()
        collectGroupMap.clear()
        mView?.resetList()
        loadData4Page(1)
    }

    override val model: BaseModel<Int, Document>
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    override fun stringMap(page: PageInfo, str: Document): List<T> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }


    override fun setCategory(t: T, category: Category) {
        require(t is ILink && category.id != null)
        val dbItem = (t as ILink).convertDBItem().apply { categoryId = category.id!! }
        CollectModel.update(dbItem)
    }
}