package co.igloohome.igloohome_sdk_demo.scanner

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.findNavController
import co.igloohome.ble.lock.IglooLock
import co.igloohome.igloohome_sdk_demo.LockViewModel
import co.igloohome.igloohome_sdk_demo.R
import co.igloohome.igloohome_sdk_demo.databinding.ScanViewholderBinding

class BleScannerAdapter(
    private val scans: List<IglooLock>,
    private val model: LockViewModel
): RecyclerView.Adapter<BleScanViewHolder>() {

    private lateinit var binding : ScanViewholderBinding
    override fun onCreateViewHolder(viewGroup : ViewGroup, p1: Int): BleScanViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        binding = ScanViewholderBinding.inflate(inflater, viewGroup, false)
        return BleScanViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BleScanViewHolder, p1: Int) {
        holder.binding.scanViewholderName.text = scans[p1].name
        holder.binding.scanViewholderPaired.text = scans[p1].pairedStatus.toString()
        holder.binding.scanViewholderActive.text = scans[p1].active.toString()

        holder.binding.root.setOnClickListener {
            model.onLockSelected(scans[p1])
            it.findNavController().navigate(R.id.action_bleScannerFragment_to_lockFragment)
        }
    }

    override fun getItemCount(): Int {
        return scans.count()
    }

    fun updateView() {
        notifyDataSetChanged()
    }
}

class BleScanViewHolder(val binding: ScanViewholderBinding): RecyclerView.ViewHolder(binding.root)
