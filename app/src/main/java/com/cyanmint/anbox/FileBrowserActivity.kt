package com.cyanmint.anbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.cyanmint.anbox.databinding.ActivityFileBrowserBinding
import java.io.File

/**
 * A minimal file browser rooted at this app's own internal storage
 * (/data/user/<userId>/com.cyanmint.anbox/files), i.e. [Context.getFilesDir].
 */
class FileBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var rootDir: File
    private lateinit var currentDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.title_activity_file_browser)

        rootDir = filesDir
        rootDir.mkdirs()
        currentDir = rootDir

        binding.listView.setOnItemClickListener { _, _, position, _ ->
            val entries = listEntries()
            val name = entries[position]
            if (name == "..") {
                currentDir = currentDir.parentFile ?: rootDir
            } else {
                val target = File(currentDir, name)
                if (target.isDirectory) {
                    currentDir = target
                } else {
                    openFile(target)
                    return@setOnItemClickListener
                }
            }
            refresh()
        }

        refresh()
    }

    private fun listEntries(): List<String> {
        val children = currentDir.listFiles()?.map { it.name }?.sorted() ?: emptyList()
        return if (currentDir != rootDir) listOf("..") + children else children
    }

    private fun refresh() {
        supportActionBar?.subtitle = "/" + rootDir.toURI().relativize(currentDir.toURI()).path
        val entries = listEntries()
        val labels = entries.map { name ->
            if (name != ".." && File(currentDir, name).isDirectory) "$name/" else name
        }
        binding.listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
    }

    private fun openFile(file: File) {
        try {
            val uri = Uri.parse(
                "content://" + "$packageName.documents/document/" + Uri.encode(file.absolutePath, "/")
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // No viewer available for this file; ignore.
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onBackPressed() {
        if (currentDir != rootDir) {
            currentDir = currentDir.parentFile ?: rootDir
            refresh()
        } else {
            super.onBackPressed()
        }
    }
}
