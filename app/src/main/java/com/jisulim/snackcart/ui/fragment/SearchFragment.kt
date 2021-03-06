package com.jisulim.snackcart.ui.fragment

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import com.dinuscxj.refresh.RefreshView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.support.v4.runOnUiThread
import org.jetbrains.anko.support.v4.toast
import com.jisulim.snackcart.R
import com.jisulim.snackcart.databinding.FragmentSearchBinding
import com.jisulim.snackcart.models.dto.Product
import com.jisulim.snackcart.ui.SiteType
import com.jisulim.snackcart.ui.activity.BaseActivity
import com.jisulim.snackcart.utils.FormatUtil
import com.jisulim.snackcart.utils.Jsoup


class SearchFragment : BaseFragment() {

    private lateinit var binding: FragmentSearchBinding
    lateinit var vhManager: ViewHolderManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_search, container, false)
        binding.apply {
            lifecycleOwner = this@SearchFragment
            setViewHolderManager()
            rvSearchResult.layoutManager = LinearLayoutManager(activity)
            rvSearchResult.adapter = ListAdapter(vhManager, null)
        }

        setPullToRefresh()

        viewModel.userList = resources.getStringArray(R.array.user)

        setSpinner()
        setListener()
        setObserver()

        return binding.root
    }

    private fun setPullToRefresh() {
        val scale: Float = resources.displayMetrics.density * 40
        val layoutParams = ViewGroup.LayoutParams(
            scale.toInt(),
            scale.toInt(),
        )
        binding.layoutRefresh.apply {
            setRefreshView(RefreshView(this.context), layoutParams)
        }
    }

    override fun setViewHolderManager() {
        vhManager = ViewHolderManager(viewModel, glide, LIST_TYPE.SEARCH) { product, _ ->
            if (product != null) {
                when (viewModel.requesterId.value) {
                    0 -> {
                        val title = "????????? ????????? ????????? ???????????????."
                        val message = ""
                        dialog(title, message)
                    }
                    else -> {
                        val minQty = product.ordQty.toInt()
                        if (minQty != 1) {
                            val title = "?????? ?????? ?????? : $minQty"
                            val message = "?????? ?????? ?????? $minQty ?????? ??????????????? ??????????????????????\n" +
                                    "??? ?????? = ${FormatUtil.priceFilter(product.price * minQty)}"
                            val positive = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    insertWishList(product)
                                }
                            }
                            dialog(title = title, message = message, positive = positive, {})
                        } else {
                            CoroutineScope(Dispatchers.IO).launch {
                                insertWishList(product)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun insertWishList(product: Product) {
        when (viewModel.roomRequesterIsExist()) {
            true -> {
                val title = "?????? ?????? ????????? ??????????????????."
                val message = "??? ?????? ??? ?????? ????????? ?????? ??? ????????????.\n?????? ???????????? ?????????????????????????"
                val positive = {
                    viewModel.roomUpdate(product)
                    toast("??????????????? ????????? ???????????????!")
                }
                dialog(title, message, positive = positive, negative = {})
            }
            false -> {
                val title =
                    "${viewModel.userList[viewModel.requesterId.value!!]}???"
                val message = "??????????????? ????????????????\n????????? ???????????? ????????? ????????? ?????? ?????? ??? ??????????????????."
                val positive = {
                    viewModel.roomInsert(product)
                    toast("??????????????? ????????? ???????????????!")
                }
                dialog(title, message, positive = positive, negative = {})
            }
        }
    }

    override fun setObserver() {

        viewModel.searchResultList.observe(this, {
            binding.layoutRefresh.setRefreshing(false)
            try {
                (activity as BaseActivity).setFullScreen()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            binding.tvNoti.text = "?????? ????????? ????????????."
            binding.tvNoti.visibility = viewModel.setTextSearchIsEmpty()
            (binding.rvSearchResult.adapter as ListAdapter).setData(it)
        })

        viewModel.requesterId.observe(this, {
            if (it != 0)
                toast("${viewModel.userList[it]}??? ???????????????.")
        })

        viewModel.siteType.observe(this, {
            Jsoup.changeSiteType(it)
            (binding.rvSearchResult.adapter as ListAdapter).setData(null)
        })

    }

    override fun setListener() {

        binding.btnSearch.setOnClickListener {
            searchProcess()
            keyDown()
        }

        binding.spRequester.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                viewModel.refreshRequesterId(position)
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }

        binding.spSite.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                when (position) {
                    0 -> {
                        viewModel.refreshSiteType(SiteType.EMART)
                    }
                    1 -> {
                        viewModel.refreshSiteType(SiteType.TRADERS)
                    }
                }
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }



        binding.layoutRefresh.setOnRefreshListener {
            binding.layoutRefresh.setRefreshing(false)
        }

        binding.etSearch.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                searchProcess()
                keyDown()
                return@setOnKeyListener true
            }

            return@setOnKeyListener false
        }

    }

    private fun searchProcess() {
        CoroutineScope(Dispatchers.IO).launch {
            val keyword = binding.etSearch.text.toString()

            if (keyword.isEmpty()) {
                runOnUiThread {
                    toast("???????????? ??????????????????.")
                }
                return@launch
            }
            binding.layoutRefresh.setRefreshing(true)
            val data = Jsoup.search(keyword)
            if (data != null)
                viewModel.refreshSearchResultList(data)
            else
                runOnUiThread {
                    toast("?????? ??? ?????? ??????????????????.")
                    binding.layoutRefresh.setRefreshing(false)
                }
        }
    }

    private fun setSpinner() {

        val spinnerRequester: Spinner = binding.spRequester
        val spinnerSite: Spinner = binding.spSite

        val spinnerRequesterList = viewModel.userList
        val spinnerSiteList = resources.getStringArray(R.array.site_list)

        this.context?.let {
            spinnerRequester.adapter = spinnerAdapter(it, spinnerRequesterList)
            spinnerSite.adapter = spinnerAdapter(it, spinnerSiteList)
        }

    }

    private fun spinnerAdapter(it: Context, list: Array<String>) =
        object : ArrayAdapter<String>(it, R.layout.spinner_custom, list) {
            override fun getDropDownView(
                position: Int,
                convertView: View?,
                parent: ViewGroup
            ): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as TextView).gravity = Gravity.CENTER
                return v
            }
        }

}