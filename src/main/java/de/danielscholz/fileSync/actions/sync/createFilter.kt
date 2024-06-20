package de.danielscholz.fileSync.actions.sync

import de.danielscholz.fileSync.SyncFilesParams


fun getFilter(paramValues: SyncFilesParams): Filter {

    val excludedFilenameMatchers = (paramValues.excludedFiles + paramValues.defaultExcludedFiles).map { excludedFilename ->
        if (excludedFilename.contains("*")) {
            val escaped = excludedFilename
                .replace("(", "\\(")
                .replace("[", "\\[")
                .replace(".", "\\.")
            val pattern = escaped
                .replace("*", ".*")
                .replace("?", ".")
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            FilenameMatcher { _, filename ->
                regex.matches(filename)
            }
        } else {
            FilenameMatcher { _, filename ->
                filename.equals(excludedFilename, ignoreCase = true)
            }
        }
    }

    val excludedPathMatchers = (paramValues.excludedPaths + paramValues.defaultExcludedPaths).map {
        val excludedPath = it.replace('\\', '/')
        val excludedPathLC = excludedPath.lowercase()
        when {
            "/" in excludedPath && "*" in excludedPath -> {
                val escaped = excludedPath
                    .replace("(", "\\(")
                    .replace("[", "\\[")
                    .replace(".", "\\.")
                var pattern = escaped
                    .replace("*", ".*")
                    .replace("?", ".")
                if (!pattern.endsWith(".*"))
                    pattern += ".*"
                if (!pattern.startsWith("//"))
                    pattern = ".*$pattern"
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                PathMatcher { fullPath, _ ->
                    regex.matches("/$fullPath")
                }
            }
            "/" in excludedPath -> {
                PathMatcher { fullPath, _ ->
                    if (excludedPathLC.startsWith("//"))
                        excludedPathLC in "/$fullPath".lowercase()
                    else
                        excludedPathLC in fullPath.lowercase()
                }
            }
            "*" in excludedPath -> {
                val escaped = excludedPath
                    .replace("(", "\\(")
                    .replace("[", "\\[")
                    .replace(".", "\\.")
                val pattern = escaped
                    .replace("*", ".*")
                    .replace("?", ".")
                val regex = Regex(pattern, RegexOption.IGNORE_CASE)
                PathMatcher { _, folderName ->
                    regex.matches(folderName)
                }
            }
            else -> {
                PathMatcher { _, folderName ->
                    folderName.equals(excludedPath, ignoreCase = true)
                }
            }
        }
    }

    val folderFilter = FolderFilter { fullPath, folderName ->
        if (excludedPathMatchers.any { it.matches(fullPath, folderName) }) ExcludedBy.USER else null
    }

    val fileFilter = FileFilter { path, fileName ->
        if (excludedFilenameMatchers.any { it.matches(path, fileName) }) ExcludedBy.USER else null
    }

    return Filter(folderFilter, fileFilter)
}


private fun interface FilenameMatcher {
    fun matches(path: String, filename: String): Boolean
}

private fun interface PathMatcher {
    fun matches(fullPath: String, folderName: String): Boolean
}