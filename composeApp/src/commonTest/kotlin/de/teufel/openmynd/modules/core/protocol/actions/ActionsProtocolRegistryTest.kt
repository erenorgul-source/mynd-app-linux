package de.teufel.openmynd.modules.core.protocol.actions

import de.teufel.openmynd.modules.core.device.Mynd
import de.teufel.openmynd.modules.core.feature.Feature
import de.teufel.openmynd.testutils.FakeDeviceConnector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ActionsProtocolRegistryTest {

    private val protocol = ActionsProtocol(FakeDeviceConnector())

    @Test
    fun supportedTypesMatchMyndFeatureDefinitions() {
        val expected = Mynd.features.map { it::class }.toSet()
        assertEquals(expected, protocol.supportedFeatureTypes())
    }

    @Test
    fun createsFeatureImplementationsForAllMyndDefinitions() {
        Mynd.features.forEach { definition ->
            val instance = protocol.createFeature(definition)
            assertNotNull(instance, "No implementation for ${definition::class.simpleName}")
        }
    }

    @Test
    fun returnsNullForUnsupportedFeatureDefinition() {
        val unsupportedDefinition = object : Feature {}
        val instance = protocol.createFeature(unsupportedDefinition)
        assertNull(instance)
    }
}
