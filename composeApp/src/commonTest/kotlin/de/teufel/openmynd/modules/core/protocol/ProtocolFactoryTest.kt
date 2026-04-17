package de.teufel.openmynd.modules.core.protocol

import de.teufel.openmynd.modules.core.device.Mynd
import de.teufel.openmynd.modules.core.device.ALL_SUPPORTED_DEVICES
import de.teufel.openmynd.modules.core.protocol.actions.ActionsProtocol
import de.teufel.openmynd.testutils.FakeDeviceConnector
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class ProtocolFactoryTest {

    private val connector = FakeDeviceConnector()
    private val factory = DefaultProtocolFactory()

    @Test
    fun createsActionsProtocolForActionsDevice() {
        val protocol = factory.create(Mynd, connector)
        assertNotNull(protocol)
        assertIs<ActionsProtocol>(protocol)
    }

    @Test
    fun createsProtocolForAllKnownSupportedDevices() {
        ALL_SUPPORTED_DEVICES.forEach { deviceType ->
            val protocol = factory.create(deviceType, connector)
            assertNotNull(protocol, "No protocol mapping for ${deviceType.publicName}")
        }
    }
}
