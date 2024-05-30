package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.actions.FoldersMutable


open class FoldersContext(open val foldersCtx: Folders)

class MutableFoldersContext(override val foldersCtx: FoldersMutable) : FoldersContext(foldersCtx)


class CaseSensitiveContext(val isCaseSensitive: Boolean)