package com.syhung.androidaiautomation.agent

import android.app.Activity
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.syhung.androidaiautomation.agent.backup.CadssBackupManager
import com.syhung.androidaiautomation.agent.casl.CaslValidator
import com.syhung.androidaiautomation.agent.casl.SkillRegistry
import com.syhung.androidaiautomation.agent.data.CadssRepository
import com.syhung.androidaiautomation.agent.domain.CocDomainRepository
import com.syhung.androidaiautomation.agent.domain.CocResourceCalculator
import com.syhung.androidaiautomation.agent.domain.ResourceAmounts
import com.syhung.androidaiautomation.agent.domain.UpgradeCost
import com.syhung.androidaiautomation.agent.vision.VisionMemoryStore
import org.json.JSONObject
import java.io.File

class AgentDashboardActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var domain: CocDomainRepository
    private lateinit var repository: CadssRepository
    private lateinit var skills: SkillRegistry
    private val createBackupCode = 8101
    private val restoreBackupCode = 8102
    private val importImageCode = 8103

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        domain = CocDomainRepository(this)
        repository = CadssRepository(this)
        skills = SkillRegistry(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 28, 28, 28)
        }
        root.addView(TextView(this).apply { text = "CoC AI Companion"; textSize = 25f }, match())
        status = TextView(this).apply {
            text = "Local-first companion tools. Verify game facts against a current source."
            textSize = 14f
            setPadding(0, 12, 0, 18)
        }
        root.addView(status, match())
        addButton(root, "Create account profile", ::createAccount)
        addButton(root, "Create base layout record", ::createLayout)
        addButton(root, "Create army composition", ::createArmy)
        addButton(root, "Browse stored records", ::browseRecords)
        addButton(root, "Resource / upgrade calculator", ::resourceCalculator)
        addButton(root, "Validate and save CASL skill draft", ::createSkillDraft)
        addButton(root, "View skill registry", ::browseSkills)
        addButton(root, "Create crop from a stored original", ::createVisionCrop)
        addButton(root, "Import image into Vision Memory", {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }, importImageCode)
        })
        addButton(root, "Export full local backup", {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
                putExtra(Intent.EXTRA_TITLE, "coc-agent-backup.zip")
            }, createBackupCode)
        })
        addButton(root, "Restore from backup", {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/zip"
            }, restoreBackupCode)
        })
        addButton(root, "Return to automation tools") { finish() }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addButton(root: LinearLayout, label: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = label; setOnClickListener { action() } }, match())
    }

    private fun match() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 10 }

    private fun inputDialog(title: String, fields: List<Pair<String, String>>, done: (List<String>) -> Unit) {
        val edits = fields.map { (hint, value) -> EditText(this).apply { this.hint = hint; setText(value) } }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 0)
            edits.forEach { addView(it, match()) }
        }
        AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ -> done(edits.map { it.text.toString().trim() }) }
            .show()
    }

    private fun createAccount() = inputDialog("New account profile", listOf(
        "Display name" to "", "Player tag (optional)" to "", "Town Hall level" to ""
    )) { values ->
        val th = values[2].toIntOrNull()
        if (values[0].isBlank() || (values[2].isNotBlank() && th == null)) status.text = "Enter a profile name and valid optional Town Hall level."
        else try {
            val id = domain.saveAccount(displayName = values[0], playerTag = values[1].takeIf { it.isNotBlank() }, townHall = th)
            status.text = "Account profile saved locally: " + id
        } catch (e: Exception) { status.text = e.message ?: "Could not save profile." }
    }

    private fun createLayout() = inputDialog("New base layout record", listOf(
        "Layout name" to "", "Account ID (optional)" to "", "Town Hall level" to "",
        "Type: home / war / builder" to "home", "Source image ID (optional)" to ""
    )) { v ->
        val th = v[2].toIntOrNull()
        if (v[0].isBlank() || th == null) status.text = "Enter a layout name and Town Hall level."
        else try {
            val id = domain.saveLayout(v[1].takeIf { it.isNotBlank() }, v[0], th, v[3].lowercase(), v[4].takeIf { it.isNotBlank() })
            status.text = "Layout metadata saved (unverified): " + id
        } catch (e: Exception) { status.text = e.message ?: "Invalid layout." }
    }

    private fun createArmy() = inputDialog("New army composition", listOf(
        "Army name" to "", "Account ID (optional)" to "", "Town Hall level" to "",
        "Army capacity" to "", "Composition note (JSON or plain text)" to ""
    )) { v ->
        val th = v[2].toIntOrNull()
        val capacity = v[3].toIntOrNull()
        if (v[0].isBlank() || th == null || capacity == null) status.text = "Enter name, Town Hall and capacity."
        else {
            val composition = try { JSONObject(v[4]) } catch (_: Exception) { JSONObject().put("notes", v[4]) }
            try {
                val id = domain.saveArmy(v[1].takeIf { it.isNotBlank() }, v[0], th, capacity, composition)
                status.text = "Army record saved (unverified): " + id
            } catch (e: Exception) { status.text = e.message ?: "Invalid army record." }
        }
    }


    private fun createVisionCrop() = inputDialog("Create linked Vision Memory crop", listOf(
        "Original image ID" to "",
        "Crop label" to "region",
        "Left pixel" to "0",
        "Top pixel" to "0",
        "Right pixel" to "100",
        "Bottom pixel" to "100"
    )) { values ->
        val coords = values.drop(2).map { it.toIntOrNull() }
        if (values[0].isBlank() || values[1].isBlank() || coords.any { it == null }) {
            status.text = "Enter an image ID, label and four integer coordinates."
        } else try {
            val bounds = Rect(coords[0]!!, coords[1]!!, coords[2]!!, coords[3]!!)
            val asset = VisionMemoryStore(this, repository).saveCropFromOriginal(values[0], bounds, values[1])
            status.text = "Crop saved and linked to original image: " + asset.id
        } catch (e: Exception) {
            status.text = e.localizedMessage ?: "Could not create image crop."
        }
    }

    private fun browseRecords() {
        val records = repository.list(includeArchived = true, limit = 300)
        val lines = records.joinToString("\n\n") { record ->
            record.type + " | " + record.id + "\nRevision " + record.revision + " · " + record.status + "\n" + record.payload.take(260)
        }.ifBlank { "No records stored yet." }
        AlertDialog.Builder(this).setTitle("CADSS records (" + records.size + ")")
            .setMessage(lines).setPositiveButton("Close", null).show()
    }

    private fun resourceCalculator() = inputDialog("Resource / upgrade calculator", listOf(
        "Current Gold" to "0", "Current Elixir" to "0", "Current Dark Elixir" to "0",
        "Upgrade Gold cost" to "0", "Upgrade Elixir cost" to "0", "Upgrade Dark Elixir cost" to "0",
        "Duration seconds" to "0", "Game data source/version" to ""
    )) { v ->
        val n = v.take(7).map { it.toLongOrNull() }
        if (n.any { it == null || it < 0L }) status.text = "All resource values and duration must be non-negative numbers."
        else {
            val current = ResourceAmounts(n[0]!!, n[1]!!, n[2]!!)
            val cost = ResourceAmounts(n[3]!!, n[4]!!, n[5]!!)
            val plan = CocResourceCalculator.calculate(current, UpgradeCost("manual", cost, n[6]!!, v[7], v[7].isNotBlank()))
            AlertDialog.Builder(this).setTitle("Calculation result")
                .setMessage("Missing resources:\nGold: " + plan.missing.gold + "\nElixir: " + plan.missing.elixir +
                    "\nDark Elixir: " + plan.missing.darkElixir + "\n\nAffordable now: " + plan.affordable +
                    "\n" + plan.warnings.joinToString("\n"))
                .setPositiveButton("Close", null).show()
        }
    }

    private fun createSkillDraft() {
        val manifest = EditText(this).apply {
            hint = "Skill manifest JSON"
            minLines = 5
            setText("""{"skill_id":"coc.example","name":"Example companion skill","skill_version":"1.0.0","casl_version":"1.0","cadss_min_version":"1.0.0","capabilities":["knowledge.read"],"status":"draft"}""")
        }
        val workflow = EditText(this).apply {
            hint = "Workflow JSON"
            minLines = 5
            setText("""{"version":1,"steps":[{"id":"step-1","op":"OUTPUT","input":{"message":"Draft only"}}]}""")
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 8, 20, 0)
            addView(manifest, match())
            addView(workflow, match())
        }
        AlertDialog.Builder(this).setTitle("Validate CASL skill draft")
            .setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Validate and save") { _, _ ->
                val result = CaslValidator.validate(manifest.text.toString(), workflow.text.toString())
                if (!result.valid) {
                    AlertDialog.Builder(this).setTitle("Skill rejected").setMessage(result.errors.joinToString("\n"))
                        .setPositiveButton("Close", null).show()
                } else try {
                    val saved = skills.saveDraft(manifest.text.toString(), workflow.text.toString())
                    status.text = "CASL draft saved: " + saved.skillId + " revision " + saved.revision + ". It is not active."
                } catch (e: Exception) { status.text = e.message ?: "Could not save skill." }
            }.show()
    }

    private fun browseSkills() {
        val rows = skills.listRecent().joinToString("\n\n") {
            it.skillId + " · revision " + it.revision + " · " + it.status + "\n" + it.manifest.take(240)
        }
        AlertDialog.Builder(this).setTitle("Skill registry")
            .setMessage(rows.ifBlank { "No skill drafts saved yet." })
            .setPositiveButton("Close", null).show()
    }

    @Deprecated("Use Activity Result APIs in a future UI refactor")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri = data.data!!
        try {
            when (requestCode) {
                importImageCode -> {
                    val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                        ?: throw IllegalArgumentException("Unable to decode selected image")
                    val asset = VisionMemoryStore(this, repository).saveOriginal(bitmap, "user_import")
                    bitmap.recycle()
                    status.text = "Image stored in Vision Memory: " + asset.id + " (" + asset.width + "×" + asset.height + ")"
                }
                createBackupCode -> {
                    val temp = File(cacheDir, "cadss-export.zip")
                    CadssBackupManager(this, repository).createBackup(temp)
                    contentResolver.openOutputStream(uri)?.use { output -> temp.inputStream().use { it.copyTo(output) } }
                    status.text = "Backup exported successfully."
                }
                restoreBackupCode -> {
                    val temp = File(cacheDir, "cadss-import.zip")
                    contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it) } }
                    val count = CadssBackupManager(this, repository).restoreBackup(temp)
                    status.text = "Restored " + count + " records. Existing records were merged by ID."
                }
            }
        } catch (e: Exception) {
            status.text = "Backup operation failed: " + (e.localizedMessage ?: "Invalid archive")
        }
    }
}
