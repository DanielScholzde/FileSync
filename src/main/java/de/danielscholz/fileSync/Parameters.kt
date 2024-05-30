package de.danielscholz.fileSync

import de.danielscholz.kargparser.Description
import java.io.File
import java.util.*


class GlobalParams {

    var timeZone: TimeZone = TimeZone.getDefault()

    @Description("Should a test run be done without any file changes (except the database)?")
    var dryRun = false

    @Description("Should a confirmation popup window for file changes be shown?")
    var confirmations = true

    @Description("Should more information be printed to console?")
    var verbose = false
}

class SyncFilesParams {
    @Description("Source directory")
    var sourceDir: File? = null

    @Description("Target directory")
    var targetDir: File? = null

//   @Description("Included directories; base path is 'sourceDir'. Separator char is \"/\". Specify only if needed")
//   var includedPaths: List<String> = listOf()

    @Description("Maximum of allowed file changes in percent. If more files changed, a confirmation popup window will appear (can be disabled with parameter '--silent').")
    var maxChangedFilesWarningPercent = 5

    @Description("Minimum of allowed file changes. If more files changed, a confirmation popup window will appear (can be disabled with parameter '--silent').")
    var minAllowedChanges = 50

    @Description("Minimum of free disk space in percent. If there is not enough disk space, the whole task is not started.")
    var minDiskFreeSpacePercent = 10

    @Description("Minimum of free disk space in MB. If there is not enough disk space, the whole task is not started.")
    var minDiskFreeSpaceMB = 1000

    @Description(
        "Part of filename (without path). You can use * for an arbitrary pattern. To exclude files by extension, use: \"*.jpg\"\n" +
                "Hint: a full filename is matched by \"name\". If the underlying filesystem is case-sensitive, these entries are also."
    )
    var excludedFiles: Set<String> = setOf()

    @Description(
        "Part of path (without filename) OR absolute path. Separator char is \"/\". You can use * for an arbitrary pattern. An absolute path is defined by starting with \"//\", e.g. \"//absolute/path/\"\n" +
                "Hint: a full directory name is matched by \"name\". If the underlying filesystem is case-sensitive, these entries are also."
    )
    var excludedPaths: Set<String> = setOf()

    val defaultExcludedFiles = setOf("thumbs.db", "desktop.ini")
    val defaultExcludedPaths = setOf("\$RECYCLE.BIN", "System Volume Information")
}

class VerifyFilesParams {
    @Description("Directory")
    var dir: File? = null
}
