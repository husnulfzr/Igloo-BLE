package co.igloohome.igloohome_sdk_demo.lock

import android.bluetooth.BluetoothGatt
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.Keep
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import co.igloohome.ble.lock.ActivityLog
import co.igloohome.ble.lock.IglooLock
import co.igloohome.ble.lock.Notification
import co.igloohome.ble.lock.PinType
import co.igloohome.igloohome_sdk_demo.LockViewModel
import co.igloohome.igloohome_sdk_demo.R
import co.igloohome.igloohome_sdk_demo.database.LocalDatabaseManager
import co.igloohome.igloohome_sdk_demo.database.StoredKey
import co.igloohome.igloohome_sdk_demo.databinding.FragmentLockBinding
import co.igloohome.igloohome_sdk_demo.server.*
import co.igloohome.igloohome_sdk_demo.toPlainHex
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onCancel
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.input.input
import com.afollestad.materialdialogs.list.listItemsMultiChoice
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog
import com.wdullaer.materialdatetimepicker.time.TimePickerDialog
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.*


class LockFragment : Fragment() {
    lateinit var lock: IglooLock

    //Disposables
    var commandDisposables = CompositeDisposable()
    var connectionStateDisposable: Disposable? = null
    var lockNotificationDisposable: Disposable? = null

    //mAdapter
    val commandResultAdapter = CommandResultAdapter()

    //Server Api
    val serverApi = ServerApi.create(ServerApi.serverBaseUrl)

    private lateinit var model: LockViewModel
    private lateinit var binding: FragmentLockBinding

    /**
     * To manage getting and retrieving key from local database
     *
     * Using this approach is optional.
     */
    private lateinit var localDbMgr: LocalDatabaseManager

    /**
     * Here we are using a connection manager for scanning, connecting, to manage their disposables.
     *
     * ConnectionManager also has getConnectedLock() method which helps ensure that the lock is connected
     * before a Bluetooth command is performed, provided that the key is valid. For example, see the unlock() command below.
     *
     * Using this approach is optional.
     */
    private lateinit var connectionMgr: ConnectionManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentLockBinding.inflate(inflater, container, false)

        model = activity?.run {
            ViewModelProviders.of(this).get(LockViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        localDbMgr = LocalDatabaseManager(requireContext())
        connectionMgr = ConnectionManager(requireContext())

        model.lock.observe(viewLifecycleOwner, {
            lock = it
            binding.lockFragmentName.text = it.name

            //get existing key if any
            commandDisposables.add(
                localDbMgr.getAdminKeyFromLocalDb(lock.name)
                    .subscribe({
                        connectionMgr.setKey(lock.name, it.key)
                },{})
            )
        })

        with(binding.commandButton) {
            setOnClickListener {
                if (connectionStateDisposable == null) {
                    Toast.makeText(context, "Not yet connected.", Toast.LENGTH_LONG).show()
                } else {
                    MaterialDialog(context).show {
                        listItemsSingleChoice(if (lock.pairedStatus) R.array.ble_commands_paired else R.array.ble_commands_unpaired) { _, _, text ->
                            executeCommand(text.toString())
                        }
                    }
                }
            }
        }

        with(binding.connectionSwitch) {
            setOnCheckedChangeListener { _, checked ->
                if (checked==true) {
                    if (binding.connectionSwitch.tag != "ignore") {
                        if (!lock.pairedStatus) {
                            connectForPairing()
                        } else {
                            MaterialDialog(context).show {
                                title(text = "Connection Type")
                                listItemsSingleChoice(items = listOf("Admin", "Guest")) { _, _, text ->
                                    if (text == "Admin") {
                                        localDbMgr.getAdminKeyFromLocalDb(lock.name)
                                            .subscribeOn(AndroidSchedulers.mainThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                connectToLock(it.key)
                                            }, {
                                                Toast.makeText(
                                                    context,
                                                    it.message,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            })
                                    } else {
                                        getGuestKey()
                                            .subscribeOn(AndroidSchedulers.mainThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe({
                                                Timber.d("Successfully retrieved guest key from server: $it")
                                                connectToLock(it)
                                            }, {
                                                Timber.e(it, "Failed to get guest key from server.")
                                                isChecked = false
                                            })
                                    }
                                }
                            }
                        }
                    }
                } else {
                    disconnectFromLock()
                }
            }
        }

        with(binding.commandResultList) {
            layoutManager = LinearLayoutManager(context)
            adapter = commandResultAdapter
        }

        return binding.root
    }

    @Keep
    override fun onResume() {
        super.onResume()
        connectionStateDisposable = lock.observeConnectionStateChange()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    when (it.state) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            binding.lockFragmentConnectionStatus.text = "Connected"
                            //Toggle connected
                            binding.connectionSwitch.setTag("ignore")
                            binding.connectionSwitch.isChecked = true
                            binding.connectionSwitch.setTag(null)
                        }
                        BluetoothGatt.STATE_CONNECTING -> binding.lockFragmentConnectionStatus.text = "Connecting"
                        BluetoothGatt.STATE_DISCONNECTING -> binding.lockFragmentConnectionStatus.text = "Disconnecting"
                        BluetoothGatt.STATE_DISCONNECTED -> binding.lockFragmentConnectionStatus.text = "Disconnected"
                    }
                }, {
                    Timber.e(it, "Error observing connection state.")
                    throw RuntimeException("This is a crash")
                }
            )

        lockNotificationDisposable = lock.lockNotifications()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                Timber.d("Received notification: $it")
                when (it) {
                    is Notification.CardDeregistered -> {
                        displayCommandResult(
                            "Card Deregistered",
                            null,
                            mapOf("Card ID" to (it.cardUid?.toPlainHex() ?: ""))
                        )
                    }
                    is Notification.CardRegistered -> {
                        displayCommandResult(
                            "Card Registered",
                            null,
                            mapOf("Card ID" to (it.cardUid?.toPlainHex() ?: ""))
                        )
                    }
                    is Notification.Status -> {
                        displayCommandResult(
                            "Status Notification",
                            null,
                            mapOf(
                                "Active" to (it.woken == 1).toString(),
                                "Has Logs" to (it.hasLogs == 1).toString(),
                                "Lock Open" to (it.lockOpen == 1).toString()
                            )
                        )
                    }
                }
            }, {
                Timber.e(it, "Error subscribing for notifications.")
            })
    }

    override fun onPause() {
        super.onPause()
        connectionStateDisposable?.dispose()
        connectionStateDisposable = null
        lockNotificationDisposable?.dispose()
        lockNotificationDisposable = null
        localDbMgr.cancelLocalDbJob()
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromLock()
        commandDisposables.clear()
    }

    private fun executeCommand(command: String) {
        Timber.d("executeCommand() =  command: $command")
        val pairedCommands = resources.getStringArray(R.array.ble_commands_paired)
        val unpairedCommands = resources.getStringArray(R.array.ble_commands_unpaired)
        when (command) {
            unpairedCommands[0] -> { //Pair
                val timezone = TimeZone.getDefault().id
                commandDisposables.add(
                    //Get pre-pairing data from server
                    serverApi.getTimezoneConfiguration(timezone)
                        .subscribeOn(Schedulers.io())
                        //Execute pairing with pre-pairing data
                        .flatMap {
                            //Extract payload
                            lock
                                .pair(
                                    null,
                                    it.gmtOffset,
                                    null,
                                    it.getDaylightSavings(),
                                    null
                                )
                        }
                        //Submit pairing data to server to get key.
                        .flatMap {
                            serverApi.submitPairingData(
                                PairingDataRequest(
                                    it,
                                    timezone
                                )
                            )
                        }
                        //Commit pairing to lock.
                        .flatMap {
                            val epochTime = (System.currentTimeMillis() / 1000).toInt()
                            lock.commitPairing(
                                epochTime
                            )
                                .andThen(
                                    Single.just(
                                        Pair(epochTime, it)
                                    )
                                )
                        }
                        //Save key to local db
                        .flatMap {
                            Single.fromCallable {
                                val key = it.second.bluetoothAdminKey
                                val masterPin = it.second.masterPin
                                val storedKey = StoredKey(
                                    lock.name,
                                    key!!,
                                    masterPin
                                )
                                localDbMgr.saveKeyToLocalDb(storedKey)
                                it
                            }
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            //Disconnecting from the lock is required after pairing is successful.
                            disconnectFromLock()
                            val key = it.second.bluetoothAdminKey
                            val masterPin = it.second.masterPin
                            val lockTime = it.first
                            displayCommandResult(
                                "Pairing",
                                null,
                                mapOf(
                                    "Key" to key!!,
                                    "Master PIN" to masterPin,
                                    "Lock's Time" to lockTime.toString(),
                                    "NOTE" to "Pairing is successful. Now to perform a Bluetooth command, please Connect first."
                                )
                            )
                        }, {
                            displayCommandResult(command, it)
                            Timber.e(it, "Error.")
                        })
                )
            }
            pairedCommands[0] -> { //Unlock
                commandDisposables.add(
                    //An example of using getConnectedLock() firstly before performing an unlock(),
                    //which ensures the lock is connected before the unlock() is performed.
                    connectionMgr.getConnectedLock(lock)
                        .flatMapCompletable{ lockConnected ->
                            lockConnected.unlock( (System.currentTimeMillis() / 1000).toLong() )
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[1] -> { //Unpair
                //Unpair with stored key.
                commandDisposables.add(
                    //You might want to call getConnectedLock() first here. See unlock() command above.
                    lock.unpair()
                        .andThen(
                            //Delete key from local db.
                            Completable.create {
                                try {
                                    localDbMgr.deleteKeyFromLocalDb(lock.name)
                                    if (!it.isDisposed) it.onComplete()
                                } catch (e: Exception) {
                                    if (!it.isDisposed) it.onError(e)
                                }
                            }
                        )
                        //Delete lock from igloohome server.
                        .andThen(
                            serverApi.deleteLock(lock.name)
                                .subscribeOn(Schedulers.io())
                        )
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            //Disconnecting from the lock is required after unpairing is successful.
                            disconnectFromLock()
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[2] -> { //Edit Master PIN
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("Master PIN"))
                        .flatMap {
                            //Edit the PIN on the lock.
                            if (it.count() != 1) throw Exception("Invalid user input: $it")
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.setMasterPin(it[0])
                                .andThen(Single.just(it[0]))
                        }.flatMapCompletable { masterPin ->
                            //Update PIN on local db.
                            Completable.create {
                                try {
                                    localDbMgr.updatePinOnLocalDb(lock.name, masterPin)
                                    if (!it.isDisposed) it.onComplete()
                                } catch (e: Exception) {
                                    if (!it.isDisposed) it.onError(e)
                                }
                            }
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[3] -> { //Set Sensor Relock Timer
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("Enable (Boolean)", "Time in Secs (Int)"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 2) throw Exception("Invalid user input: $it")
                            val enable = it[0].toBoolean()
                            val time = it[1].toInt()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.setAutoRelock(enable, time)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[4] -> { //Set Volume
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("Volume (Int)"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 1) throw Exception("Invalid user input: $it")
                            val volume = it[0].toInt()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.setVolume(volume)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[5] -> { //Set Max Incorrect PINs
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("Enable (Boolean)", "Attempts (Int)"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 2) throw Exception("Invalid user input: $it")
                            val enable = it[0].toBoolean()
                            val attempts = it[1].toIntOrNull()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.setMaxIncorrectPin(enable, attempts)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[6] -> { //Get Battery Level
                //Get user input for PIN
                commandDisposables.add(
                    lock.getBatteryLevel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(
                                command,
                                returnVals = mapOf("Battery Level" to it.toString())
                            )
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[7] -> { //Enable Keycard Registration
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("Duration in sec (Int)"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 1) throw Exception("Invalid user input: $it")
                            val time = it[0].toInt()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.enableCardRegistration(time)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[8] -> { //Enable Keycard De-Registration
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("Duration in secs (Int)"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 1) throw Exception("Invalid user input: $it")
                            val time = it[0].toInt()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.enableCardDeregistration(time)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )

            }
            pairedCommands[9] -> { //Create Bluetooth PIN
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("PIN (String)", "PIN Type", "Start Time?", "End Time?"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 4) throw Exception("Invalid user input: $it")
                            val pin = it[0]
                            val pinType = PinType.valueOf(it[1])
                            val startTime = it[2].toLongOrNull()
                            val endTime = it[3].toLongOrNull()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.createPin(pin, pinType, startTime, endTime)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[10] -> { //Edit PIN
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(
                        listOf(
                            "Original PIN (String)",
                            "New PIN (String)",
                            "PIN Type",
                            "Start Time",
                            "End Time"
                        )
                    )
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 5) throw Exception("Invalid user input: $it")
                            val originalPin = it[0]
                            val newPin = if (it[1].isEmpty()) null else it[1]
                            val pinType = if (it[2].isNotBlank()) PinType.valueOf(it[2]) else null
                            val startTime = it[3].toLongOrNull()
                            val endTime = it[4].toLongOrNull()
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.editPin(originalPin, newPin, pinType, startTime, endTime)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }
            pairedCommands[11] -> { //Delete PIN
                //Get user input for PIN
                commandDisposables.add(
                    getUserInput(listOf("PIN (String)"))
                        .flatMapCompletable {
                            //Collect user input and update lock.
                            if (it.count() != 1) throw Exception("Invalid user input: $it")
                            val pin = it[0]
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.deletePin(pin)
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(command)
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }

            pairedCommands[12] -> { //Get Lock Status
                //Get user input for PIN
                commandDisposables.add(
                    lock.isLockOpen()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(
                                command,
                                returnVals = mapOf("Lock Open" to it.toString())
                            )
                        }, {
                            displayCommandResult(command, it)
                        })
                )

            }

            pairedCommands[13] -> { //Set Time
                //Get user input for time
                commandDisposables.add(
                    getDateFromUser()
                        .flatMap { getTimeFromUser(it) }
                        .flatMap { calendar ->
                            val epochTime = (calendar.timeInMillis / 1000).toLong()
                            Timber.d("Time from user: $epochTime")
                            //You might want to call getConnectedLock() first here. See unlock() command above.
                            lock.setTime(epochTime)
                                .andThen(
                                    Single.just(epochTime)
                                )
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({
                            displayCommandResult(
                                command,
                                returnVals = mapOf("Time" to it.toString())
                            )
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }

            pairedCommands[14] -> { //Get Activity Log
                var hasMore = false
                commandDisposables.add(
                    lock.getLogs()
                        .flatMap {
                            val payload = it.first.log
                            hasMore = it.second
                            if (payload.isNotEmpty()) {
                                val requestBody = ActivityLogRequest(payload)
                                serverApi.submitActivityLog(lock.name, requestBody)
                            }
                            else {
                                Single.error(Exception("log payload is empty"))
                            }
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .repeatUntil { !hasMore }
                        .subscribe({
                            displayCommandResult(
                                command,
                                returnVals = mapOf("Activity Log" to it.toString())
                            )
                        }, {
                            displayCommandResult(command, it)
                        })
                )
            }

            //TODO:: add lock command
            else -> {
                displayCommandResult(command)
            }
        }
    }

    private fun disconnectFromLock() {
        connectionMgr.disconnectLock(lock)
        binding.connectionSwitch.isChecked = false
    }

    private fun connectForPairing() {
        connectionMgr.connectForPairing(lock)
            .subscribe({
                Timber.d("Successfully connected to lock for pairing.")
            }, {
                Timber.e(it, "Error connecting to lock for pairing.")
                Toast.makeText(context, "Error connecting to lock for pairing.", Toast.LENGTH_SHORT).show()
                binding.connectionSwitch?.isChecked = false
            })
    }

    private fun connectToLock(keyStr: String? = null) {
        connectionMgr.setKey(lock.name, keyStr)
        connectionMgr.connect(lock)
            .subscribe({
                Timber.d("Successfully connected to lock.")
            }, {
                Timber.e(it, "Error connecting to lock.")
                Toast.makeText(context, "Error connecting to lock.", Toast.LENGTH_SHORT).show()
                binding.connectionSwitch?.isChecked = false
            })
    }

    private fun displayCommandResult(
        commandName: String,
        error: Throwable? = null,
        returnVals: Map<String, String>? = null
    ) {
        if (error != null) Timber.e(error, "Error from command result.")
        commandResultAdapter.addCommandResult(
            CommandResultAdapter.CommandResult(commandName, error, returnVals)
        )
    }

    private fun getUserInput(requiredInput: List<String>): Single<List<String>> {
        var dialog: MaterialDialog? = null
        return Single.create<List<String>> { emitter ->
            dialog = MaterialDialog(this.requireContext()).show {
                if (requiredInput.count() == 1) {
                    input(hint = requiredInput[0]) { _, charSequence ->
                        if (!emitter.isDisposed) emitter.onSuccess(listOf(charSequence.toString()))
                    }
                } else {
                    val layout = LinearLayout(context)
                    layout.orientation = LinearLayout.VERTICAL
                    val editTexts = requiredInput.map {
                        val editTexts = EditText(context)
                        editTexts.hint = it
                        return@map editTexts
                    }
                    editTexts.forEach { layout.addView(it) }
                    customView(view = layout, scrollable = true)
                    positiveButton(text = "Submit") { _ ->
                        if (!emitter.isDisposed) emitter.onSuccess(editTexts.map { it.editableText.toString() })
                    }
                }
                onCancel { if (!emitter.isDisposed) emitter.onError(Exception("Dialog cancelled.")) }
            }
        }.doOnDispose { dialog?.cancel() }
            .doOnError { dialog?.cancel() }
    }

    private fun getDateFromUser(): Single<Calendar> {
        return Single.create<Calendar> { emitter ->
            val calendar = Calendar.getInstance()
            val dpd = DatePickerDialog.newInstance { view, year, monthOfYear, dayOfMonth ->
                calendar.set(year, monthOfYear, dayOfMonth)
                if (!emitter.isDisposed) emitter.onSuccess(calendar)
            }
            emitter.setCancellable { dpd.dismiss() }
            dpd.setOnCancelListener { if (!emitter.isDisposed) emitter.onError(Exception("User cancelled.")) }
            dpd.show(requireFragmentManager(), "DatePicker")
        }
    }

    private fun getTimeFromUser(oldCalendar: Calendar? = null): Single<Calendar> {
        return Single.create<Calendar> { emitter ->
            val calendar = oldCalendar ?: Calendar.getInstance()
            val tpd = TimePickerDialog.newInstance({ _: TimePickerDialog?, hourOfDay: Int, minute: Int, second: Int ->
                calendar.set(
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH),
                    hourOfDay,
                    minute,
                    second
                )
                if (!emitter.isDisposed) emitter.onSuccess(calendar)
            }, false)
            emitter.setCancellable { tpd.dismiss() }
            tpd.setOnCancelListener { if (!emitter.isDisposed) emitter.onError(Exception("User cancelled.")) }
            tpd.show(requireFragmentManager(), "TimePicker")
        }
    }

    private fun getGuestKey(): Single<String> {
        //Collect permissions for guest key from user
        return Single.create<Array<EKeyRequest.Permission>> { emitter ->
            //var selectedPermissions = emptyList<String>()
            val dialog = MaterialDialog(this.requireContext()).show {
                listItemsMultiChoice(
                    items = EKeyRequest.Permission.values().map { it.name }) { dialog, indices, items ->
                    if (!emitter.isDisposed) emitter.onSuccess(items.map { EKeyRequest.Permission.valueOf(it.toString()) }
                        .toTypedArray())
                }
                onCancel { if (!emitter.isDisposed) emitter.onError(Exception("Selection window cancelled.")) }
                positiveButton {}
            }
            emitter.setCancellable { dialog.cancel() }
            //Collect End date and time. In this example, the Start date and time is NOW
        }.flatMap { permissions ->
            getDateFromUser()
                .flatMap { getTimeFromUser(it) }
                .map { Pair(permissions, it) }
        }
            //Create guest key from server.
            .map {
                EKeyRequest(Date(), it.second.time, it.first.toList())
            }
            .flatMap { serverApi.getGuestKey(lock.name, it).subscribeOn(Schedulers.io()) }
            .map {
                it.get("bluetoothGuestKey").asString ?: throw Exception("Server response did not contain guest key.")
            }
    }
}
