package de.teufel.openmynd.modules.core.feature.sourceselection

import de.teufel.openmynd.modules.core.feature.base.SelectorFeature
import kotlinx.coroutines.flow.StateFlow

/**
 * Feature for controlling and monitoring connected audio sources
 */
interface SourceSelectionFeature : SelectorFeature<SourceType> {
    /**
     * Current source as a type-safe enum value
     */
    val currentSource: StateFlow<SourceType?>
    override val selected: StateFlow<SourceType?> get() = currentSource
    
    /**
     * Currently connected sources as a bitmask
     */
    val connectedSources: StateFlow<Set<SourceType>?>
    
    /**
     * Set the current audio source
     * @param source The source to activate
     * @param channel The channel to use (typically 0 for single channel devices)
     */
    suspend fun setSource(source: SourceType, channel: Byte = 0): Boolean

    override suspend fun select(value: SourceType): Boolean = setSource(value)
}

/**
 * Represents available audio sources
 */
enum class SourceType(val id: Byte, val displayName: String) {
    BLUETOOTH(0x00, "Bluetooth"),
    AUX(0x01, "AUX"),
    USB(0x02, "USB");
    
    companion object {
        fun fromId(id: Int): SourceType? = values().find { it.id.toInt() == id }
        
        fun fromConnectedSourcesBitmask(bitmask: Int): Set<SourceType> {
            val sources = mutableSetOf<SourceType>()
            if ((bitmask and 0x01) != 0) sources.add(BLUETOOTH)
            if ((bitmask and 0x02) != 0) sources.add(USB)
            if ((bitmask and 0x04) != 0) sources.add(AUX)
            return sources
        }
    }
} 