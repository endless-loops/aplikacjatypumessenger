package com.example.aplikacjatypumessenger.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.example.aplikacjatypumessenger.MessengerApplication
import com.example.aplikacjatypumessenger.di.AppContainer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Skrót do kontenera zależności z dowolnego miejsca, które ma `Context`. */
val Context.appContainer: AppContainer
    get() = (applicationContext as MessengerApplication).container

/** Dostęp do kontenera zależności w `viewModelFactory { initializer { ... } }`. */
val CreationExtras.appContainer: AppContainer
    get() = (checkNotNull(this[APPLICATION_KEY]) as MessengerApplication).container

/** Zbiera flow tylko wtedy, gdy ekran jest widoczny (bezpieczne względem cyklu życia). */
fun <T> LifecycleOwner.collectWhenStarted(flow: Flow<T>, action: suspend (T) -> Unit) {
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

/** Pokazuje komunikat przygotowany przez ViewModel (zob. [UiText]). */
fun Context.showToast(text: UiText, long: Boolean = false) {
    Toast.makeText(this, text.asString(this), if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

/**
 * Przewija do najnowszej wiadomości, gdy doszły nowe, a użytkownik był na dole listy
 * albo sam właśnie coś wysłał.
 */
fun RecyclerView.scrollToNewMessages(previousCount: Int, lastIsMine: Boolean) {
    val count = adapter?.itemCount ?: 0
    if (count == 0 || count <= previousCount) return
    val layoutManager = layoutManager as? LinearLayoutManager
    // Margines 2 pozycji - użytkownik nie musi być idealnie na samym dole
    val wasAtBottom = previousCount == 0 ||
        (layoutManager?.findLastVisibleItemPosition() ?: 0) >= previousCount - 2
    if (wasAtBottom || lastIsMine) scrollToPosition(count - 1)
}

/**
 * Od targetSdk 35 Android rysuje aplikację pod paskami systemowymi (edge-to-edge).
 * Dodaje do istniejącego paddingu miejsce na paski systemowe i klawiaturę.
 */
fun View.applySystemBarsPadding() {
    // Zapamiętujemy padding z layoutu, bo listener może być wywołany wiele razy
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.setPadding(
            initialLeft + bars.left,
            initialTop + bars.top,
            initialRight + bars.right,
            // Otwarta klawiatura zasłania pasek nawigacji, więc bierzemy większą z wartości
            initialBottom + maxOf(bars.bottom, ime.bottom)
        )
        WindowInsetsCompat.CONSUMED
    }
}
