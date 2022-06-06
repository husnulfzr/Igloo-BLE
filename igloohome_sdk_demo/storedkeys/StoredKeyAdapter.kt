package co.igloohome.igloohome_sdk_demo.storedkeys

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import co.igloohome.igloohome_sdk_demo.R
import co.igloohome.igloohome_sdk_demo.database.StoredKey
import co.igloohome.igloohome_sdk_demo.databinding.FragmentStoredkeyBinding

class StoredKeyAdapter(
    private val viewListener: ViewHolderClickListener,
    private val keyList: List<StoredKey>
) : RecyclerView.Adapter<StoredKeyAdapter.ViewHolder>() {

    private lateinit var binding : FragmentStoredkeyBinding
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        binding = FragmentStoredkeyBinding.inflate(inflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = keyList[position]
        holder.binding.storedkeyLockName.text = item.lockName
        holder.binding.storedkeyKey.text = item.key
        holder.binding.storedkeyMasterPin.text = item.masterPin

        holder.binding.root.setOnClickListener {view ->
            viewListener.onViewHolderClick(view, item)
        }
    }

    override fun getItemCount(): Int = keyList.size

    inner class ViewHolder(val binding: FragmentStoredkeyBinding) : RecyclerView.ViewHolder(binding.root)


    // For alerting StoredKeyFragment to clicks on viewholder
    interface ViewHolderClickListener {
        fun onViewHolderClick(view: View, storedKey: StoredKey)
    }
}
