package de.teufel.openmynd.modules.ui

interface IUiState {
    val isRefreshing: Boolean
    val isLoadingMore: Boolean
    val isError: Boolean
    val isEmpty: Boolean
    val errorMessage: String?
}