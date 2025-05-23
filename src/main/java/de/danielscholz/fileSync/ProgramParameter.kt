package de.danielscholz.fileSync

import de.danielscholz.kargparser.Description
import java.io.File


class GlobalParams

class SyncFilesParams {

    @Description("Name for sync of this pair of directories")
    var syncName: String? = null

    @Description("Considers this index database also to prevent unnecessary indexing operations (only useful when synchronization is done with multiple sources or targets)")
    var considerOtherIndexedFilesWithSyncName: String? = null

    @Description("Source directory")
    var sourceDir: File? = null

    @Description("Target directory")
    var targetDir: File? = null

    @Description("Should sourceDir and targetDir be indexed in parallel?")
    var parallelIndexing: Boolean = true

    @Description("BackupMode")
    var backupMode: Boolean = false

    @Description("Skip indexing phase. Useful, when last run was a dry-run and no files changed since then")
    var skipIndexing: Boolean = false

    @Description("")
    var warnIfFileCopyHasNoOriginal: Boolean = false

    @Description("Maximum of allowed file changes in percent. If more files changed, a confirmation popup window will appear (can be disabled with parameter '--confirmations no').")
    var maxChangedFilesWarningPercent = 5

    @Description("Minimum of allowed file changes. If more files changed, a confirmation popup window will appear (can be disabled with parameter '--confirmations no').")
    var minAllowedChanges = 50

    @Description("Minimum of free disk space in percent. If there is not enough disk space, the whole task is not started.")
    var minDiskFreeSpacePercent = 5

    @Description("Minimum of free disk space in MB. If there is not enough disk space, the whole task is not started.")
    var minDiskFreeSpaceMB = 1000

    @Description(
        "Part of filename (without path). You can use * for an arbitrary pattern. To exclude files by extension, use: \"*.jpg\"\n" +
                "Hint: a full filename is matched by \"name\". If the underlying filesystem is case-sensitive, these entries are also."
    )
    var excludedFiles: Set<String> = setOf()

    @Description(
        "Part of path OR absolute path (both without filename). Separator char is \"/\". You can use * for an arbitrary pattern. An absolute path is defined by starting with \"//\", e.g. \"//absolute/path/\"\n" +
                "Hint: a full directory name is matched by \"name\". If the underlying filesystem is case-sensitive, these entries are also."
    )
    var excludedPaths: Set<String> = setOf()

    @Description(
        "Part of path OR absolute path (both without filename). Separator char is \"/\". You can use * for an arbitrary pattern. An absolute path is defined by starting with \"//\", e.g. \"//absolute/path/\"\n" +
                "Hint: a full directory name is matched by \"name\". If the underlying filesystem is case-sensitive, these entries are also."
    )
    var encryptSourcePaths: Set<String> = setOf()

    @Description(
        "Part of path OR absolute path (both without filename). Separator char is \"/\". You can use * for an arbitrary pattern. An absolute path is defined by starting with \"//\", e.g. \"//absolute/path/\"\n" +
                "Hint: a full directory name is matched by \"name\". If the underlying filesystem is case-sensitive, these entries are also."
    )
    var encryptTargetPaths: Set<String> = setOf()

    @Description("Password for encryption of files within source directory")
    var passwordSource: String? = null

    @Description("Password for encryption of files within target directory")
    var passwordTarget: String? = null

    @Description("Directory where the synchronization lockfile should be placed (for sourceDir)")
    var lockfileSourceDir: File? = null

    @Description("Directory where the synchronization lockfile should be placed (for targetDir)")
    var lockfileTargetDir: File? = null

    @Description("Should a test run be done without any file changes (except the index database)?")
    var dryRun = false

//    @Description("Save indexed files metadata despite dry run")
//    var saveIndexResultDespiteDryRun = false

    @Description("Should a confirmation popup window file changes (e.g. more files than allowed were changed) be shown?")
    var confirmations = true

    @Description("Should possible conflicts/problems are ignored?")
    var ignoreConflicts = false

    @Description("Should more information be printed to console?")
    var verbose = false

    val defaultExcludedFiles = setOf("thumbs.db", "desktop.ini")
    val defaultExcludedPaths = setOf("\$RECYCLE.BIN", "System Volume Information")
}

//class VerifyFilesParams {
//    @Description("Directory")
//    var dir: File? = null
//}
