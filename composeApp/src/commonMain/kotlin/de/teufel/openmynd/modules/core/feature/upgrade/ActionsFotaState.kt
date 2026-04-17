package de.teufel.openmynd.modules.core.feature.upgrade

sealed class ActionsFotaState {

    val isAwaitingReboot: Boolean
        get() = this is TRANSFERRED

    val isInProgress: Boolean
        get() = this is PREPARING || this is TRANSFERRING

    data object UNKNOWN : ActionsFotaState()
    data object IDLE : ActionsFotaState()

    data class FILE_SELECTED(val fileName: String, val fileSize: Int) : ActionsFotaState() {
        override fun toString() = "FILE_SELECTED ($fileName, ${fileSize / 1024} KB)"
    }

    data object PREPARING : ActionsFotaState()
    data object PREPARED : ActionsFotaState()

    data class TRANSFERRING(val progress: Int, val bytesTransferred: Int = 0, val totalBytes: Int = 0) : ActionsFotaState() {
        override fun toString() = "TRANSFERRING $progress% (${bytesTransferred / 1024}/${totalBytes / 1024} KB)"
    }

    data object TRANSFERRED : ActionsFotaState()
    data object COMPLETED : ActionsFotaState()
    data object FAILED : ActionsFotaState()
}
