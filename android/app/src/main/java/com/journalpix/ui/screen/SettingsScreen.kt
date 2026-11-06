package com.journalpix.ui.screen

import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.NavController
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import com.journalpix.R

private const val TAG_SETTINGS = "JPXSS"

/** Preference key for the global synchronization switch (default OFF). */
const val PREF_SYNC_ENABLED = "sync_enabled"

/** True when the user enabled synchronization in Settings (default false). */
fun isSyncEnabled(context: android.content.Context): Boolean {
  return PreferenceManager.getDefaultSharedPreferences(context)
    .getBoolean(PREF_SYNC_ENABLED, false)
}

/** Native Android settings fragment (PreferenceFragmentCompat). */
class SettingsFragment : PreferenceFragmentCompat() {

  private val exportLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")
  ) { uri ->
    if (uri != null) {
      val ok = com.journalpix.data.ExportImport.exportToZip(requireContext(), uri)
      android.widget.Toast.makeText(
        requireContext(),
        if (ok) "Journal exported" else "Export failed",
        android.widget.Toast.LENGTH_LONG
      ).show()
    }
  }

  private val importLauncher = registerForActivityResult(
    androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      val ok = com.journalpix.data.ExportImport.importFromZip(requireContext(), uri)
      android.widget.Toast.makeText(
        requireContext(),
        if (ok) "Journal imported — restart to see changes" else "Import failed",
        android.widget.Toast.LENGTH_LONG
      ).show()
    }
  }

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    Log.i(TAG_SETTINGS, "onCreatePreferences: building native settings screen, rootKey='$rootKey'")
    val screen = preferenceManager.createPreferenceScreen(requireContext())
    SwitchPreferenceCompat(requireContext()).apply {
      key = PREF_SYNC_ENABLED
      title = "Synchronization"
      summary = "Sync trips, places and photos with the server (off = fully offline, no network access)"
      setDefaultValue(false)
      isIconSpaceReserved = false
    }.also { screen.addPreference(it) }

    androidx.preference.Preference(requireContext()).apply {
      key = "export_zip"
      title = "Export journal (ZIP)"
      summary = "Save all trips, places and photos to a ZIP file"
      isIconSpaceReserved = false
      onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener {
        Log.i(TAG_SETTINGS, "Export tapped — launching file picker")
        exportLauncher.launch("journalpix-export.zip")
        true
      }
    }.also { screen.addPreference(it) }

    androidx.preference.Preference(requireContext()).apply {
      key = "import_zip"
      title = "Import journal (ZIP)"
      summary = "Replace the current journal with a ZIP backup (photos included)"
      isIconSpaceReserved = false
      onPreferenceClickListener = androidx.preference.Preference.OnPreferenceClickListener {
        Log.i(TAG_SETTINGS, "Import tapped — launching file picker")
        importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
        true
      }
    }.also { screen.addPreference(it) }

    preferenceScreen = screen
  }
}

@Composable
fun SettingsScreen(navController: NavController) {
  Log.i(TAG_SETTINGS, "SettingsScreen: rendering native settings fragment")
  AndroidView(
    modifier = Modifier.fillMaxSize(),
    factory = { ctx ->
      FragmentContainerView(ctx).apply {
        id = R.id.settings_fragment_container
      }
    },
    update = { container ->
      val fm = (container.context as? androidx.fragment.app.FragmentActivity)
        ?.supportFragmentManager
      if (fm != null && fm.findFragmentById(R.id.settings_fragment_container) == null) {
        fm.beginTransaction()
          .replace(R.id.settings_fragment_container, SettingsFragment())
          .commit()
      }
    }
  )
}
