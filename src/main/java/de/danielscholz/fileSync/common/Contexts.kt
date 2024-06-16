package de.danielscholz.fileSync.common

import de.danielscholz.fileSync.actions.Folders
import de.danielscholz.fileSync.actions.MutableFolders


open class FoldersContext(open val foldersCtx: Folders)

class MutableFoldersContext(override val foldersCtx: MutableFolders) : FoldersContext(foldersCtx)


class CaseSensitiveContext(val isCaseSensitive: Boolean)