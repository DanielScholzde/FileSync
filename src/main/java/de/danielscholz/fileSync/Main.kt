package de.danielscholz.fileSync

import de.danielscholz.fileSync.actions.sync.*
import de.danielscholz.fileSync.common.*
import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.ArgParser
import de.danielscholz.kargparser.ArgParserBuilder
import de.danielscholz.kargparser.ArgParserConfig
import de.danielscholz.kargparser.parser.*


fun main(args: Array<String>) {
    registerShutdownCallback {
        Global.cancel = true
    }
    registerLowMemoryListener()

    val parser = createParser()
    if (!demandedHelp(args, parser)) {
        try {

            parser.parseArgs(args)

        } catch (e: CancelException) {
            println("execution canceled")
            if (isTest()) throw e // throw exception only in test case
        } catch (e: ArgParseException) {
            println(parser.printout(e))
            if (isTest()) throw e // throw exception only in test case
        }
    }
}


@Suppress("DuplicatedCode")
private fun createParser() = ArgParserBuilder(GlobalParams()).buildWith(ArgParserConfig(ignoreCase = true, noPrefixForActionParams = true)) {

    addActionParser("help", "Show all available options and commands") {
        println(printout())
    }

    addActionParser(
        Commands.SYNC_FILES.command,
        ArgParserBuilder(SyncFilesParams()).buildWith {
            add(paramValues::syncName, StringParam())
            add(paramValues::sourceDir, FileParam(checkIsDir = true), required = true)
            add(paramValues::targetDir, FileParam(checkIsDir = true), required = true)
            add(paramValues::excludedPaths, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
            add(paramValues::lockfileSourceDir, FileParam(checkIsDir = true))
            add(paramValues::lockfileTargetDir, FileParam(checkIsDir = true))
            add(paramValues::excludedFiles, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
            add(paramValues::maxChangedFilesWarningPercent, IntParam())
            add(paramValues::minAllowedChanges, IntParam())
            add(paramValues::minDiskFreeSpacePercent, IntParam())
            add(paramValues::minDiskFreeSpaceMB, IntParam())
            add(paramValues::parallelIndexing, BooleanParam())
            add(paramValues::confirmations, BooleanParam())
            add(paramValues::dryRun, BooleanParam())
            //add(paramValues::saveIndexResultDespiteDryRun, BooleanParam())
            add(paramValues::verbose, BooleanParam())
        }) {

        val exclFileNamesSimple = mutableSetOf<String>()
        val exclFileNamesRegex = mutableListOf<Regex>()
        (paramValues.excludedFiles + paramValues.defaultExcludedFiles).forEach { exclFileName ->
            if (exclFileName.contains("*")) {
                val escaped = exclFileName
                    .replace("(", "\\(")
                    .replace("[", "\\[")
                    .replace(".", "\\.")
                val pattern = escaped
                    .replace("*", ".*")
                    .replace("?", ".")
                exclFileNamesRegex += Regex(pattern)
            } else {
                exclFileNamesSimple += exclFileName
            }
        }


        val exclPaths = mutableListOf<PathMatcher>()
        (paramValues.excludedPaths + paramValues.defaultExcludedPaths).forEach { exclPath ->
            when {
                "/" in exclPath && "*" in exclPath -> {
                    val escaped = exclPath
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
                    val regex = Regex(pattern)
                    exclPaths += PathMatcher { path, _ ->
                        regex.matches("/$path")
                    }
                }
                "/" in exclPath -> {
                    exclPaths += PathMatcher { path, _ ->
                        if (exclPath.startsWith("//"))
                            exclPath in "/$path"
                        else
                            exclPath in path
                    }
                }
                "*" in exclPath -> {
                    val escaped = exclPath
                        .replace("(", "\\(")
                        .replace("[", "\\[")
                        .replace(".", "\\.")
                    val pattern = escaped
                        .replace("*", ".*")
                        .replace("?", ".")
                    val regex = Regex(pattern)
                    exclPaths += PathMatcher { _, folderName ->
                        regex.matches(folderName)
                    }
                }
                else -> {
                    exclPaths += PathMatcher { _, folderName ->
                        folderName == exclPath
                    }
                }
            }

        }

        val folderFilter = FolderFilter { fullPath, folderName ->
            if (exclPaths.any { exclPath -> exclPath.matches(fullPath, folderName) }) ExcludedBy.USER else null
        }

        val fileFilter = FileFilter { _, fileName ->
            if (fileName in exclFileNamesSimple ||
                exclFileNamesRegex.any { exclFile -> exclFile.matches(fileName) }
            ) ExcludedBy.USER else null
        }

        val filter = Filter(folderFilter, fileFilter)

        SyncFiles(
            paramValues,
            paramValues.sourceDir!!.canonicalFile,
            paramValues.targetDir!!.canonicalFile,
            filter
        ).sync()
    }

//      addActionParser(
//         Commands.BACKUP_FILES.command,
//         ArgParserBuilder(BackupFilesParams()).buildWith {
//            addConfigParamsForIndexFiles()
//            addConfigParamsForSyncOrBackup()
//            add(paramValues::includedPaths, StringListParam(mapper = { it.replace('\\', '/').removePrefix("/").removeSuffix("/") }))
//            addNamelessLast(paramValues::sourceDir, FileParam(checkIsDir = true), required = true)
//            addNamelessLast(paramValues::targetDir, FileParam(checkIsDir = true), required = true)
//         }) {
//         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<File2>?, provideResult: Boolean ->
//            BackupFiles(pl).run(
//               paramValues.sourceDir!!.canonicalFile,
//               paramValues.targetDir!!.canonicalFile,
//               paramValues.includedPaths,
//            )
//            null
//         }
//      }
//      addActionParser(
//         Commands.VERIFY_FILES.command,
//         ArgParserBuilder(VerifyFilesParams()).buildWith {
//            add(Config.INST::fastMode, BooleanParam())
//            add(Config.INST::ignoreHashInFastMode, BooleanParam())
//            addNamelessLast(paramValues::dir, FileParam(checkIsDir = true), required = true)
//         },
//         "Verify",
//         {
//            Config.INST.fastMode = false // deactivate fastMode only on verify as default
//         }) {
//         outerCallback.invoke(false) { pl: PersistenceLayer, pipelineResult: List<File2>?, provideResult: Boolean ->
//            VerifyFiles(pl, true).run(paramValues.dir!!.canonicalFile)
//            null
//         }
//      }
}


private fun interface PathMatcher {
    fun matches(path: String, folderName: String): Boolean
}

private fun demandedHelp(args: Array<String>, parser: ArgParser<GlobalParams>): Boolean {
    // offer some more options for showing help and to get help for a specific command
    val helpArguments = setOf("/?", "--?", "?", "--help", "help")
    val foundIdx = args.indexOfFirst { it in helpArguments }
    if (foundIdx >= 0) {
        val argumentsWithoutHelp = args.toMutableList()
        argumentsWithoutHelp.removeAt(foundIdx)
        println(parser.printout(argumentsWithoutHelp.toTypedArray(), false))
        return true
    }
    return false
}
