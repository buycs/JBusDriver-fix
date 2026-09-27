package me.jbusdriver.mvp.presenter

import me.jbusdriver.mvp.ForumCollectContract
import me.jbusdriver.mvp.bean.ForumPost

class ForumCollectPresenterImpl :
    BaseAbsCollectPresenter<ForumCollectContract.ForumCollectView, ForumPost>(),
    ForumCollectContract.ForumCollectPresenter {

    override fun lazyLoad() {
        onFirstLoad()
    }
}
