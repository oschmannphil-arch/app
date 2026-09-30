package com.nextlesson.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.nextlesson.app.data.Freund
import com.nextlesson.app.data.FreundeStore
import kotlinx.coroutines.flow.StateFlow

class FreundeViewModel(app: Application) : AndroidViewModel(app) {

    private val store = FreundeStore(app)

    val freunde: StateFlow<List<Freund>> = store.freunde

    fun speichern(freund: Freund) = store.speichern(freund)

    fun loeschen(id: String) = store.loeschen(id)
}
