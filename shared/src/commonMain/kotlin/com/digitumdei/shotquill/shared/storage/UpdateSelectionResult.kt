package com.digitumdei.shotquill.shared.storage

sealed class UpdateSelectionResult {
    data object Success : UpdateSelectionResult()
    data object DraftNotFound : UpdateSelectionResult()
    data object AssetNotOwnedByDraft : UpdateSelectionResult()
}
