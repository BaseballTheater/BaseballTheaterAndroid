package com.jakelauer.baseballtheater.experiences.gamelist.gamedetail.highlights

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.constraint.ConstraintLayout
import android.support.v4.content.ContextCompat.startActivity
import android.support.v7.app.AlertDialog
import android.support.v7.widget.CardView
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.jakelauer.baseballtheater.MlbDataServer.DataStructures.Highlight
import com.jakelauer.baseballtheater.MlbDataServer.DataStructures.HighlightSearchResult
import com.jakelauer.baseballtheater.MlbDataServer.ProgressListener
import com.jakelauer.baseballtheater.R
import com.jakelauer.baseballtheater.base.AdapterChildItem
import com.jakelauer.baseballtheater.base.BaseActivity
import com.jakelauer.baseballtheater.base.ItemViewHolder
import com.jakelauer.baseballtheater.experiences.gamelist.gamedetail.OpenHighlightAsyncTask
import com.jakelauer.baseballtheater.utils.PrefUtils
import com.jakelauer.baseballtheater.utils.PrefUtils.Companion.BEHAVIOR_HIDE_SCORES
import libs.ButterKnife.bindView


/**
 * Created by Jake on 10/26/2017.
 */

class HighlightItem(highlight: HighlightData, val m_activity: BaseActivity)
	: AdapterChildItem<HighlightItem.HighlightData, HighlightItem.ViewHolder>(highlight)
{
	private var m_prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(m_activity)

	override fun getLayoutResId() = R.layout.highlight_item

	override fun createViewHolder(view: View) = ViewHolder(view)

	override fun onBindView(viewHolder: ViewHolder, context: Context)
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
		{
			setElevation(viewHolder)
		}

		viewHolder.m_thumbnail.loadUrl(m_data.thumb)

		val title = if (m_data.recap) context.getString(R.string.highlight_recap) else m_data.headline

		viewHolder.m_title.text = title

		if (m_data.bigblurb != null)
		{
			viewHolder.m_subtitle.text = m_data.bigblurb
		}

		val hideQualities = !m_prefs.getBoolean("behavior_show_video_quality_options", true)
		if (hideQualities)
		{
			viewHolder.m_qualityHigh.visibility = View.GONE
			viewHolder.m_qualityMid.visibility = View.GONE
			viewHolder.m_qualityLow.visibility = View.GONE
		}

		if ((m_data.recap || m_data.condensed))
		{
			viewHolder.m_title.setTypeface(viewHolder.m_title.typeface, Typeface.BOLD)

			if (PrefUtils.getBoolean(context, BEHAVIOR_HIDE_SCORES))
			{
				viewHolder.m_subtitle.visibility = View.GONE
			}
			else
			{
				viewHolder.m_subtitle.visibility = View.VISIBLE
			}
		}
		else
		{
			viewHolder.m_title.typeface = Typeface.create(viewHolder.m_title.typeface, Typeface.NORMAL)
		}


		setListeners(viewHolder)
	}

	private fun setListeners(viewHolder: ViewHolder)
	{
		viewHolder.m_infoWrapper.setOnClickListener(HighlightClickListener(m_activity, m_data, getDefaultUrl()))

		viewHolder.m_qualityLow.setOnClickListener(HighlightClickListener(m_activity, m_data, m_data.video_s))
		viewHolder.m_qualityMid.setOnClickListener(HighlightClickListener(m_activity, m_data, m_data.video_m))
		viewHolder.m_qualityHigh.setOnClickListener(HighlightClickListener(m_activity, m_data, m_data.video_l))
	}

	private fun getDefaultUrl(): String
	{
		return m_data.video_m ?: m_data.video_s ?: m_data.video_l
		?: throw Exception("No urls available")
	}

	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
	private fun setElevation(viewHolder: ViewHolder)
	{
		if (m_data.recap || m_data.condensed)
		{
			viewHolder.m_container.elevation = 20f
		}
		else
		{
			viewHolder.m_container.elevation = 4f
		}
	}

	class ViewHolder(view: View) : ItemViewHolder(view)
	{
		var m_container: CardView by bindView(R.id.HIGHLIGHT_container)
		var m_infoWrapper: ConstraintLayout by bindView(R.id.HIGHLIGHT_info_wrapper)
		var m_thumbnail: ImageView by bindView(R.id.HIGHLIGHT_thumbnail)
		var m_title: TextView by bindView(R.id.HIGHLIGHT_title)
		var m_subtitle: TextView by bindView(R.id.HIGHLIGHT_subtitle)
		var m_qualityLow: TextView by bindView(R.id.HIGHLIGHT_quality_low)
		var m_qualityMid: TextView by bindView(R.id.HIGHLIGHT_quality_mid)
		var m_qualityHigh: TextView by bindView(R.id.HIGHLIGHT_quality_high)
	}

	class HighlightClickListener(val m_activity: BaseActivity, val m_highlight: HighlightData, val m_url: String?) : View.OnClickListener
	{
		override fun onClick(view: View)
		{
			val task = OpenHighlightAsyncTask(view.context, ProgressListener {
				if (it && m_url != null)
				{
					openLink(m_url, view.context)
				}
				else
				{
					val alertDialog = AlertDialog.Builder(view.context).create()
					alertDialog.setMessage("Unable to find a video at the specified quality - please try another quality link")
					alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", { dialog, _ -> dialog.dismiss() })
					alertDialog.show()
				}
			})
			task.execute(m_url)
		}

		private fun openLink(url: String, context: Context)
		{
			val sm = CastContext.getSharedInstance(context).sessionManager
			val castSession = sm.currentCastSession
			val remoteMediaClient = castSession?.remoteMediaClient
			if (remoteMediaClient != null)
			{
				val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
				metadata.putString(MediaMetadata.KEY_TITLE, m_highlight.headline)
				metadata.putString(MediaMetadata.KEY_SUBTITLE, m_highlight.blurb)

				val mediaInfo = MediaInfo.Builder(url)
						.setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
						.setContentType("videos/mp4")
						.setMetadata(metadata)
						.setStreamDuration(m_highlight.durationMilliseconds)
						.build()

				remoteMediaClient.load(mediaInfo)

				val controlFragment = HighlightCastControlFragment.newInstance(m_highlight)
				controlFragment.show(m_activity.supportFragmentManager, "castControlFragment")
			}
			else
			{
				val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
				startActivity(context, browserIntent, null)
			}
		}
	}

	class HighlightData
	{
		val recap: Boolean
		val condensed: Boolean
		val blurb: String
		val bigblurb: String
		val headline: String
		val durationMilliseconds: Long
		val thumb: String
		var video_l: String? = null
		var video_m: String? = null
		var video_s: String? = null

		constructor(highlight: Highlight)
		{
			recap = highlight.recap
			condensed = highlight.condensed
			blurb = highlight.blurb
			headline = highlight.headline
			bigblurb = highlight.bigblurb
			thumb = highlight.thumbs.thumbs[highlight.thumbs.thumbs.size - 4]
			durationMilliseconds = highlight.durationMilliseconds()
			video_l = highlight.urls[highlight.urls.size - 1]
			video_s = highlight.urls[0]
			video_m = highlight.urls[highlight.urls.size / 2]
		}

		constructor(highlightSearchResult: HighlightSearchResult)
		{
			recap = highlightSearchResult.recap ?: false
			condensed = highlightSearchResult.condensed ?: false
			headline = highlightSearchResult.headline ?: ""
			blurb = highlightSearchResult.blurb ?: ""
			bigblurb = highlightSearchResult.bigBlurb ?: ""
			thumb = highlightSearchResult.thumb_m ?: highlightSearchResult.thumb_l ?: highlightSearchResult.thumb_s ?: throw Exception("No thumbnail available")
			durationMilliseconds = highlightSearchResult.getDurationMilliseconds()
			video_l = highlightSearchResult.video_l
			video_m = highlightSearchResult.video_m
			video_s = highlightSearchResult.video_s

		}
	}
}