package com.example.groundcontrolapp

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class MapListAdapter(
    context: Context,
    private val items: MutableList<String>
) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, items) {

    fun setItems(newItems: List<String>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        (view as? TextView)?.setTextColor(Color.parseColor("#eaeaea"))
        return view
    }
}