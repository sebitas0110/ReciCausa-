package pe.edu.upn.recicausa.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "recicausa_prefs")
