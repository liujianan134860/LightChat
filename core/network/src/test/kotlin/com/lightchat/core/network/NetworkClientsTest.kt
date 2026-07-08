package com.lightchat.core.network

import org.junit.Assert.assertSame
import org.junit.Test

class NetworkClientsTest {
    @Test
    fun derivedClientsShareConnectionInfrastructure() {
        assertSame(NetworkClients.base.connectionPool, NetworkClients.http.connectionPool)
        assertSame(NetworkClients.base.connectionPool, NetworkClients.webSocket.connectionPool)
        assertSame(NetworkClients.base.dispatcher, NetworkClients.image.dispatcher)
    }
}
