package com.efectossetup

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONException

data class Pedal(
    val id: String,
    val name: String,
    val accentColor: Int,
    val defaultLines: List<String>
)

class PedalAdapter(
    private val context: Context,
    private val pedals: List<Pedal>,
    private val prefs: SharedPreferences
) : RecyclerView.Adapter<PedalAdapter.ViewHolder>() {

    private val expandedStates = BooleanArray(pedals.size) { false }

    private val currentLines: MutableList<MutableList<String>> = pedals.map { pedal ->
        loadLines(pedal.id, pedal.defaultLines).toMutableList()
    }.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: LinearLayout = view.findViewById(R.id.header)
        val accentStrip: View = view.findViewById(R.id.accentStrip)
        val tvPedalName: TextView = view.findViewById(R.id.tvPedalName)
        val tvChevron: TextView = view.findViewById(R.id.tvChevron)
        val divider: View = view.findViewById(R.id.divider)
        val expandedContent: LinearLayout = view.findViewById(R.id.expandedContent)
        val linesContainer: LinearLayout = view.findViewById(R.id.linesContainer)
        val btnEdit: Button = view.findViewById(R.id.btnEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_pedal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pedal = pedals[position]
        val lines = currentLines[position]
        val expanded = expandedStates[position]

        // Apply accent color
        holder.accentStrip.setBackgroundColor(pedal.accentColor)
        holder.tvPedalName.setTextColor(pedal.accentColor)
        holder.btnEdit.backgroundTintList = ColorStateList.valueOf(pedal.accentColor)
        holder.btnEdit.setTextColor(Color.BLACK)

        // Name and chevron
        holder.tvPedalName.text = pedal.name
        holder.tvChevron.text = if (expanded) "▲" else "▼"
        holder.tvChevron.setTextColor(
            if (expanded) pedal.accentColor else Color.parseColor("#5A5A5A")
        )

        // Expand / collapse
        val visibility = if (expanded) View.VISIBLE else View.GONE
        holder.expandedContent.visibility = visibility
        holder.divider.visibility = visibility

        // Build value lines dynamically
        holder.linesContainer.removeAllViews()
        lines.forEach { lineText ->
            val tv = TextView(context).apply {
                text = lineText
                textSize = 36f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                letterSpacing = 0.05f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 6 }
            }
            holder.linesContainer.addView(tv)
        }

        // Header tap: toggle expand/collapse
        holder.header.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                expandedStates[pos] = !expandedStates[pos]
                notifyItemChanged(pos)
            }
        }

        // Edit button
        holder.btnEdit.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                showEditDialog(pos, pedals[pos], currentLines[pos])
            }
        }
    }

    private fun showEditDialog(position: Int, pedal: Pedal, lines: MutableList<String>) {
        val scrollView = ScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 48, 56, 36)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }
        scrollView.addView(root)

        val editContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(editContainer)

        val editTexts = mutableListOf<EditText>()

        fun addLine(initialText: String = "") {
            val et = EditText(context).apply {
                setText(initialText)
                textSize = 22f
                typeface = Typeface.MONOSPACE
                setTextColor(Color.WHITE)
                setHintTextColor(Color.parseColor("#5A5A5A"))
                hint = "ej: 9 | 12 | 6"
                inputType = InputType.TYPE_CLASS_TEXT
                setBackgroundColor(Color.parseColor("#2A2A2A"))
                setPadding(20, 16, 20, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = 14 }
            }
            editTexts.add(et)
            editContainer.addView(et)
        }

        lines.forEach { addLine(it) }

        val addLineBtn = Button(context).apply {
            text = "+ LÍNEA"
            textSize = 12f
            setTextColor(Color.BLACK)
            backgroundTintList = ColorStateList.valueOf(pedal.accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = 10 }
            setOnClickListener { addLine() }
        }
        root.addView(addLineBtn)

        val dialog = AlertDialog.Builder(context, R.style.DialogTheme)
            .setTitle(pedal.name)
            .setView(scrollView)
            .setPositiveButton("GUARDAR") { _, _ ->
                val newLines = editTexts
                    .map { it.text.toString().trim() }
                    .filter { it.isNotEmpty() }
                currentLines[position] = newLines.toMutableList()
                saveLines(pedal.id, newLines)
                notifyItemChanged(position)
            }
            .setNegativeButton("CANCELAR", null)
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(pedal.accentColor)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.parseColor("#7A7A7A"))
    }

    private fun loadLines(id: String, defaults: List<String>): List<String> {
        val json = prefs.getString("lines_$id", null) ?: return defaults
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: JSONException) {
            defaults
        }
    }

    private fun saveLines(id: String, lines: List<String>) {
        prefs.edit().putString("lines_$id", JSONArray(lines).toString()).apply()
    }

    override fun getItemCount() = pedals.size
}

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("efectos_prefs", Context.MODE_PRIVATE)

        val pedals = listOf(
            Pedal(
                id = "tubescreamer",
                name = "TUBESCREAMER",
                accentColor = Color.parseColor("#4CAF50"),
                defaultLines = listOf("9  |  9  |  12")
            ),
            Pedal(
                id = "simplifier",
                name = "SIMPLIFIER",
                accentColor = Color.parseColor("#FF9800"),
                defaultLines = listOf(
                    "12  |  12  |  9  |  9",
                    "12",
                    "12  |  12  |  9  |  12  |  12"
                )
            ),
            Pedal(
                id = "rat",
                name = "RAT",
                accentColor = Color.parseColor("#F44336"),
                defaultLines = listOf("10  |  3  |  9")
            ),
            Pedal(
                id = "flanger",
                name = "FLANGER",
                accentColor = Color.parseColor("#AB47BC"),
                defaultLines = listOf("12  |  9  |  12  |  12")
            ),
            Pedal(
                id = "chorus",
                name = "CHORUS",
                accentColor = Color.parseColor("#29B6F6"),
                defaultLines = listOf("11  |  12")
            )
        )

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PedalAdapter(this, pedals, prefs)
    }
}
