package org.sugr.gearshift.ui.view

import android.content.Context
import android.graphics.Rect
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v7.widget.*
import android.util.AttributeSet
import android.view.ContextThemeWrapper
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import com.transitionseverywhere.TransitionManager
import io.reactivex.Flowable
import io.reactivex.android.schedulers.AndroidSchedulers
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.TorrentListContentBinding
import org.sugr.gearshift.viewmodel.TorrentListViewModel

class TorrentListView(context: Context?, attrs: AttributeSet?) :
        FrameLayout(context, attrs),
        TorrentListViewModel.Consumer,
        ViewModelConsumer<TorrentListViewModel>,
        ToolbarMenuItemClickListener,
		ToolbarConsumer,
		ContextMenuProvider {

	lateinit private var viewModel : TorrentListViewModel
	lateinit private var toolbar : Toolbar

	private var selectedTorrentStatusData: SelectedTorrentStatus? = null

	companion object {
		val SEARCH_VISIBLE = -2
		val SEARCH_HIDDEN = -3
	}

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!inLayout()) {
            return
        }

        val binding = TorrentListContentBinding.bind(this)
        binding.viewModel = viewModel

        val res = context.resources

        binding.list.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        binding.list.addItemDecoration(SpacerDecoration(
                res.getDimension(R.dimen.torrent_list_top_space),
                res.getDimension(R.dimen.torrent_list_bottom_space)
        ))
        binding.list.layoutManager = GridLayoutManager(context, res.getInteger(R.integer.torrent_list_columns))
        binding.list.adapter = viewModel.adapter(context)

		val errorBar = Snackbar.make(parent as View, "", 0).apply {
			view.layoutParams = (view.layoutParams as CoordinatorLayout.LayoutParams).apply {
				setMargins(leftMargin, topMargin, rightMargin,
						context.resources.getDimensionPixelSize(R.dimen.torrent_list_status_height))
			}
		}

		val behavior = BottomSheetBehavior.from((parent as View).findViewById(R.id.torrent_list_bottom_sheet))

		viewModel.errorFlowable().subscribe { hasError ->
			behavior.state =
					if (hasError) BottomSheetBehavior.STATE_EXPANDED
					else BottomSheetBehavior.STATE_COLLAPSED
		}

		val searchView = SearchView(ContextThemeWrapper(context, R.style.AppTheme_AppBarOverlay)).apply {
			setIconifiedByDefault(false)
			setQuery(viewModel.searchSubject.value, true)
			isFocusable = true

			setOnQueryTextListener(object: SearchView.OnQueryTextListener {
				override fun onQueryTextSubmit(query: String?): Boolean {
					return false
				}

				override fun onQueryTextChange(newText: String?): Boolean {
					if (viewModel.searchSubject.value != newText) {
						viewModel.searchSubject.onNext(newText)
					}
					return true
				}

			})
		}

		var title = ""

		viewModel.contextToolbarFlowable().map {
			it == SEARCH_VISIBLE
		}.observeOn(AndroidSchedulers.mainThread()).subscribe { visible ->
			val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
			if (visible && searchView.parent == null) {
				title = toolbar.title.toString()
				toolbar.addView(searchView)
				toolbar.title = ""
				searchView.requestFocusFromTouch()
				imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
			} else if (!visible && searchView.parent != null) {
				toolbar.removeView(searchView)
				toolbar.title = title
				imm.hideSoftInputFromWindow(windowToken, 0)
			}
		}

		viewModel.searchSubject.observeOn(AndroidSchedulers.mainThread()).subscribe { text ->
			if (text != searchView.query) {
				searchView.setQuery(text, false)
			}
		}

		viewModel.titleFlowable().subscribe({
			if (searchView.parent == null) {
				toolbar.title = it
			} else {
				title = it.toString()
			}
		}, Throwable::printStackTrace)

		viewModel.bind(this)

    }

	override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        viewModel.unbind()
    }

    override fun setViewModel(viewModel: TorrentListViewModel) {
        this.viewModel = viewModel
    }

    override fun onToolbarMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
			R.id.search -> viewModel.onSearchToggle()
            R.id.select_all -> viewModel.onSelectAllTorrents()
            R.id.selection_resume -> viewModel.onResumeSelected()
			R.id.selection_pause -> viewModel.onPauseSelected()
        }
        return false
    }

	override fun setToolbar(toolbar: Toolbar) {
		this.toolbar = toolbar
	}

	override fun onToolbarMenuChanged() {
		if (selectedTorrentStatusData != null) {
			toolbar.menu.findItem(R.id.selection_resume)?.isVisible = selectedTorrentStatusData?.paused ?: false
			toolbar.menu.findItem(R.id.selection_pause)?.isVisible = selectedTorrentStatusData?.running ?: false

			selectedTorrentStatusData = null
		}
	}

	override fun contextMenu(): Flowable<Int> {
		return viewModel.contextToolbarFlowable().map { menu ->
			if (menu == SEARCH_VISIBLE) 0
			else if (menu == SEARCH_HIDDEN) -1
			else menu
		}
	}

	override fun closeContextMenu() {
		viewModel.clearSelection()
		viewModel.clearSearch()
	}

	override fun selectedTorrentStatus(paused: Boolean, running: Boolean, empty: Boolean) {
		if (toolbar.menu.findItem(R.id.selection_resume) == null) {
			selectedTorrentStatusData = SelectedTorrentStatus(paused, running)
			return
		}

		if (empty) {
			return
		}

		TransitionManager.beginDelayedTransition(toolbar)

		toolbar.menu.findItem(R.id.selection_resume)?.isVisible = paused
		toolbar.menu.findItem(R.id.selection_pause)?.isVisible = running
	}
}

private data class SelectedTorrentStatus(val paused: Boolean, val running: Boolean)

class SpacerDecoration(val first: Float = 0f, val last: Float = 0f): RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State?) {
        if (parent.getChildAdapterPosition(view) == 0) {
            outRect.top = first.toInt()
        }

        if (parent.getChildAdapterPosition(view) == parent.adapter.itemCount - 1) {
            outRect.bottom = last.toInt()
        }
    }
}