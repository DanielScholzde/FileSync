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

    val excludedPathMatchers = (paramValues.excludedPaths + paramValues.defaultExcludedPaths).map { path ->
        createPathMatcher(path, false)
    }

    val folderFilter = FolderFilter { fullPath, folderName ->
        if (excludedPathMatchers.any { it.matches(fullPath, folderName) }) ExcludedBy.USER else null
    }

    val fileFilter = FileFilter { path, fileName ->
        if (excludedFilenameMatchers.any { it.matches(path, fileName) }) ExcludedBy.USER else null
    }

    return Filter(folderFilter, fileFilter)
}


fun createPathMatcher(path: String, considerFullPath: Boolean): PathMatcher {

    fun checkFullPath(fullPath: String) {
        if (!fullPath.startsWith('/') || !fullPath.endsWith('/')) throw IllegalArgumentException("Full path must start and end with '/'")
    }

    val excludedPath = path.replace('\\', '/')
    val excludedPathLC = excludedPath.lowercase()
    return when {
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
                checkFullPath(fullPath)
                regex.matches("/$fullPath")
            }
        }
        "/" in excludedPath -> {
            PathMatcher { fullPath, _ ->
                checkFullPath(fullPath)
                if (excludedPathLC.startsWith("//")) {
                    "/$fullPath".lowercase().startsWith(excludedPathLC)
                } else {
                    excludedPathLC in fullPath.lowercase()
                }
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

            if (considerFullPath) {
                PathMatcher { fullPath, _ ->
                    checkFullPath(fullPath)
                    fullPath.split('/').any { regex.matches(it) }
                }
            } else {
                PathMatcher { fullPath, folderName ->
                    checkFullPath(fullPath)
                    regex.matches(folderName)
                }
            }
        }
        else -> {
            if (considerFullPath) {
                val p = "/$excludedPath/"
                PathMatcher { fullPath, _ ->
                    checkFullPath(fullPath)
                    fullPath.contains(p, ignoreCase = true)
                }
            } else {
                PathMatcher { fullPath, folderName ->
                    checkFullPath(fullPath)
                    folderName.equals(excludedPath, ignoreCase = true)
                }
            }
        }
    }
}


private fun interface FilenameMatcher {
    fun matches(path: String, filename: String): Boolean
}

fun interface PathMatcher {
    // fullPath must start and end with a '/'
    fun matches(fullPath: String, folderName: String): Boolean
}