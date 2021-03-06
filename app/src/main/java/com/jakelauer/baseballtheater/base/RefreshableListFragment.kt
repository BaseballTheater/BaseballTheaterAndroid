package com.jakelauer.baseballtheater.base

import android.support.v4.widget.SwipeRefreshLayout
import com.jakelauer.baseballtheater.R
import libs.ButterKnife.bindView

/**
 * Created by Jake on 10/30/2017.
 */
abstract class RefreshableListFragment<out TData:Any> : FlexibleListFragment<TData>()
{
	protected var m_refreshView: SwipeRefreshLayout by bindView(R.id.refresh)

	override fun onBindView()
	{
		m_refreshView.setOnRefreshListener {
			loadData()
		}
	}
}