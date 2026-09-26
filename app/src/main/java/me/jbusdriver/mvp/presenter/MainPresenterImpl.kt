package me.jbusdriver.mvp.presenter

import me.jbusdriver.base.mvp.presenter.BasePresenterImpl
import me.jbusdriver.mvp.MainContract

/** 启动时不再联网拉版本信息, 检查更新收进设置页的手动入口 */
class MainPresenterImpl : BasePresenterImpl<MainContract.MainView>(), MainContract.MainPresenter
