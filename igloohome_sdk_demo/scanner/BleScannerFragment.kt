package co.igloohome.igloohome_sdk_demo.scanner

import android.bluetooth.le.ScanSettings
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.lifecycle.ViewModelProviders
import co.igloohome.ble.lock.BleManager
import co.igloohome.ble.lock.IglooLock
import co.igloohome.igloohome_sdk_demo.LockViewModel
import co.igloohome.igloohome_sdk_demo.R
import co.igloohome.igloohome_sdk_demo.databinding.FragmentBleScannerBinding
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class BleScannerFragment : Fragment() {

    private var scanDisposable: Disposable? = null

    private val SWITCH_OFF_TEXT = "Swipe to Scan"
    private val SWITCH_ON_TEXT = "Scanning"

    private val foundLocks = ArrayList<IglooLock>()

    private var screenRefreshDisposable: Disposable? = null

    lateinit var mAdapter: BleScannerAdapter

    private lateinit var model: LockViewModel
    private lateinit var binding: FragmentBleScannerBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentBleScannerBinding.inflate(inflater, container, false)

        model = activity?.run {
            ViewModelProviders.of(this).get(LockViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        mAdapter = BleScannerAdapter(foundLocks, model)

        with(binding.scanList) {
            layoutManager = LinearLayoutManager(context)
            adapter = mAdapter
        }

        with(binding.scanSwitch) {
            this.isChecked = false
            setOnCheckedChangeListener {_, checked ->
                Timber.d("Scan switch: $checked")
                if (checked) {
                    this.text = SWITCH_ON_TEXT
                    scanDisposable?.dispose()
                    scanDisposable = BleManager(context).scan(ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            if (!foundLocks.contains(it)) foundLocks.add(it)
                        }, {
                            Timber.e(it, "Error attempting to scan for locks.")
                        })
                    startScreenRefreshing()
                } else {
                    this.text = SWITCH_OFF_TEXT
                    scanDisposable?.dispose()
                    scanDisposable = null
                    stopScreenRefreshing()
                }
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.scanSwitch.run {isChecked = false}
    }

    override fun onPause() {
        super.onPause()
        binding.scanSwitch.run {isChecked = false}
        stopScreenRefreshing()
    }

    fun startScreenRefreshing() {
        screenRefreshDisposable = Observable.interval(1, TimeUnit.SECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                mAdapter.updateView()
            }, {
                Timber.e(it, "Refreshing failed.")
            })
    }

    fun stopScreenRefreshing() {
        screenRefreshDisposable?.dispose()
    }
}
