package de.teufel.openmynd.modules.core.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object MapStateFlow {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun <I, O> map(input: StateFlow<I>, transform: (I) -> O): StateFlow<O> {
        val out = MutableStateFlow(transform(input.value))
        scope.launch {
            input.collectLatest { value ->
                out.value = transform(value)
            }
        }
        return out
    }
}


