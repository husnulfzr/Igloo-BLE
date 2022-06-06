package co.igloohome.igloohome_sdk_demo.lock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import co.igloohome.igloohome_sdk_demo.PrettyPrintingMap
import co.igloohome.igloohome_sdk_demo.R
import co.igloohome.igloohome_sdk_demo.databinding.LockCommandResultBinding
import java.util.*

class CommandResultAdapter: RecyclerView.Adapter<CommandResultViewHolder>() {
    val commandList = LinkedList<CommandResult>()
    lateinit var binding : LockCommandResultBinding
    data class CommandResult(val commandName: String, val error: Throwable?, val returnVals: Map<String, String>?)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommandResultViewHolder {
        val inflator = LayoutInflater.from(parent.context)
        binding = LockCommandResultBinding.inflate(inflator, parent, false)
        return CommandResultViewHolder(binding.root)
    }

    override fun getItemCount(): Int {
        return commandList.size
    }

    override fun onBindViewHolder(holder: CommandResultViewHolder, position: Int) {
        holder.commandName.text = commandList[position].commandName
        holder.commandSuccess.text = (commandList[position].error == null).toString()
        holder.commandError.text = if (commandList[position].error != null) commandList[position].error!!.message else ""
//        val returnValsString = StringBuilder()
//        commandList[position].returnVals?.forEach {
//            returnValsString.append("${it.key}: ${it.value}\n")
//        }
        holder.commandReturnVals.text = if (commandList[position].returnVals != null) PrettyPrintingMap(commandList[position].returnVals!!).toString() else ""
    }

    fun addCommandResult(commandResult: CommandResult) {
        commandList.addFirst(commandResult)
        notifyDataSetChanged()
    }
}

class CommandResultViewHolder(val view: View): RecyclerView.ViewHolder(view) {
    val commandName = view.findViewById<TextView>(R.id.command_result_name)
    val commandSuccess = view.findViewById<TextView>(R.id.command_result_success)
    val commandError = view.findViewById<TextView>(R.id.command_result_error)
    val commandReturnVals = view.findViewById<TextView>(R.id.command_result_return_values)
}