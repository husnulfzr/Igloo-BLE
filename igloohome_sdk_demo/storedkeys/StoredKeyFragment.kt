package co.igloohome.igloohome_sdk_demo.storedkeys

import android.bluetooth.le.ScanSettings
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import co.igloohome.ble.lock.BleManager
import co.igloohome.igloohome_sdk_demo.LockViewModel
import co.igloohome.igloohome_sdk_demo.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit
import co.igloohome.igloohome_sdk_demo.database.StoredKey
import co.igloohome.igloohome_sdk_demo.database.StoredKeyDatabase
import co.igloohome.igloohome_sdk_demo.databinding.FragmentStoredkeyBinding
import co.igloohome.igloohome_sdk_demo.databinding.FragmentStoredkeyListBinding
import kotlinx.coroutines.*

class StoredKeyFragment: Fragment(), StoredKeyAdapter.ViewHolderClickListener {

    private var columnCount = 1

    private var scanDisposable: Disposable? = null

    private lateinit var model: LockViewModel

    private val database by lazy {
        StoredKeyDatabase.getInstance(context!!).storedKeyDatabaseDao
    }
    private val job = Job()
    private val databaseScope = CoroutineScope(Dispatchers.Main + job)
    private lateinit var binding: FragmentStoredkeyListBinding
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentStoredkeyListBinding.inflate(inflater, container, false)

        model = activity?.run {
            ViewModelProvider(this).get(LockViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        // Set the mAdapter
        with(binding.list) {
            layoutManager = when {
                columnCount <= 1 -> LinearLayoutManager(context)
                else -> GridLayoutManager(context, columnCount)
            }

            databaseScope.launch {
                withContext(Dispatchers.IO) {
                    database.getAll()
                }?.also {
                    adapter = StoredKeyAdapter(this@StoredKeyFragment, it)
                }
            }
        }

        binding.scanButton.setOnClickListener {
            it.findNavController().navigate(R.id.action_storedKeyFragment_to_bleScannerFragment)
        }

        return binding.root
    }

    override fun onViewHolderClick(view: View, storedKey: StoredKey) {
        Timber.d("onStoredKeySelected(): $storedKey")
        //Attempt to scan for lock.
        Toast.makeText(context, "Attempting to scan for lock.", Toast.LENGTH_LONG).show()
        disableButton(view)
        scanDisposable = BleManager(context!!).scan(ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build())
            .doOnDispose { Timber.d("Scanning has stopped") }
            .filter { it.name == storedKey.lockName }
            .firstOrError()
            .timeout(20, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Timber.d("Found lock selected through scanning.")
                enableButton(view)
                model.onLockSelected(it)
                view.findNavController().navigate(R.id.action_storedKeyFragment_to_lockFragment)
            }, {
                Timber.e(it ,"Error finding lock through scanning.")
                handleMissingLock(view)
            })
    }

    override fun onPause() {
        super.onPause()
        scanDisposable?.dispose()
    }

    private fun disableButton(view: View) {
        activity?.runOnUiThread {
            view.isEnabled = false
            view.isClickable = false
        }
    }

    private fun enableButton(view: View) {
        view.isEnabled = true
        view.isClickable = true
    }

    private fun handleMissingLock(view: View) {
        activity?.runOnUiThread {
            enableButton(view)
            Toast.makeText(context, "Could not find lock.", Toast.LENGTH_LONG).show()
        }
    }
}
