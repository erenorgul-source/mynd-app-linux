package org.bluez

import org.freedesktop.dbus.exceptions.DBusExecutionException

/**
 * BlueZ D-Bus errors (`org.bluez.Error.*`).
 *
 * dbus-java maps a D-Bus error to an exception by loading the class named after the error
 * (`org.bluez.Error.InProgress` -> `org.bluez.Error$InProgress`), so these nested classes let
 * callers catch specific BlueZ errors. Unknown errors surface as plain
 * [DBusExecutionException].
 */
@Suppress("unused")
class Error private constructor() {
    class Failed(message: String) : DBusExecutionException(message)
    class InProgress(message: String) : DBusExecutionException(message)
    class InvalidArguments(message: String) : DBusExecutionException(message)
    class NotReady(message: String) : DBusExecutionException(message)
    class NotSupported(message: String) : DBusExecutionException(message)
    class NotAvailable(message: String) : DBusExecutionException(message)
    class NotPermitted(message: String) : DBusExecutionException(message)
    class NotAuthorized(message: String) : DBusExecutionException(message)
    class DoesNotExist(message: String) : DBusExecutionException(message)
    class AlreadyExists(message: String) : DBusExecutionException(message)
    class AlreadyConnected(message: String) : DBusExecutionException(message)
    class AuthenticationFailed(message: String) : DBusExecutionException(message)
    class Blocked(message: String) : DBusExecutionException(message)
}
