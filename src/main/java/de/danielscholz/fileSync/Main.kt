package de.danielscholz.fileSync

import de.danielscholz.fileSync.actions.*
import de.danielscholz.fileSync.common.CancelException
import de.danielscholz.fileSync.common.isTest
import de.danielscholz.fileSync.common.registerLowMemoryListener
import de.danielscholz.fileSync.common.registerShutdownCallback
import de.danielscholz.kargparser.ArgParseException
import de.danielscholz.kargparser.ArgParser
import de.danielscholz.kargparser.ArgParserBuilder
import de.danielscholz.kargparser.ArgParserConfig
import de.danielscholz.kargparser.parser.*
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.reflect.KProperty0


private val logger = LoggerFactory.getLogger("Main")

fun main(args: Array<String>) {
    registerShutdownCallback {
        Global.cancel = true
        (LoggerFactory.getILoggerFactory() as ch.qos.logback.classic.LoggerContext).stop()
    }
    registerLowMemoryListener()

    val parser = createParser()
    if (!demandedHelp(args, parser)) {
        try {

            parser.parseArgs(args)

        } catch (e: CancelException) {
            logger.error("execution canceled")
            if (isTest()) throw e // throw exception only in test case
        } catch (e: ArgParseException) {
            logger.info(parser.printout(e))
            if (isTest()) throw e // throw exception only in test case
        }
    }
}


@Suppress("DuplicatedCode")
private fun createParser(): ArgParser<GlobalParams> {

    fun ArgParserBuilder<*>.addConfigParamsForIndexFiles() {
//        add(Config.INST::fastMode, BooleanParam())
//        add(Config.INST::ignoreHashInFastMode, BooleanParam())
//        add(Config.INST::createHashOnlyForFirstMb, BooleanParam())
//        add(Config.INST::alwaysCheckHashOnIndexForFilesSuffix, StringSetParam(typeDescription = ""))
    }

    fun loggerInfo(property: KProperty0<*>) {
        loggerInfo(property.name, property.get())
    }

    return ArgParserBuilder(GlobalParams()).buildWith(ArgParserConfig(ignoreCase = true, noPrefixForActionParams = true)) {

        addActionParser("help", "Show all available options and commands") {
            logger.info(printout())
        }

        add(paramValues::dryRun, BooleanParam())
        add(paramValues::verbose, BooleanParam())
//        add(Config.INST::logLevel, StringParam())
        add(paramValues::confirmations, BooleanParam())
//      add(paramValues::timeZone, TimeZoneParam())

        val globalParams = paramValues

        addActionParser(
            Commands.SYNC_FILES.command,
            ArgParserBuilder(SyncFilesParams()).buildWith {
                addConfigParamsForIndexFiles()
                add(paramValues::excludedPaths, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
                add(paramValues::excludedFiles, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
                add(paramValues::maxChangedFilesWarningPercent, IntParam())
                add(paramValues::minAllowedChanges, IntParam())
                add(paramValues::minDiskFreeSpacePercent, IntParam())
                add(paramValues::minDiskFreeSpaceMB, IntParam())
                add(paramValues::sourceDir, FileParam(checkIsDir = true), required = true)
                add(paramValues::targetDir, FileParam(checkIsDir = true), required = true)
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

            //setRootLoggerLevel()

            SyncFiles(globalParams, paramValues).sync(
                paramValues.sourceDir!!.canonicalFile,
                paramValues.targetDir!!.canonicalFile,
                filter
            )
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
}


fun interface PathMatcher {
    fun matches(path: String, folderName: String): Boolean
}

private fun demandedHelp(args: Array<String>, parser: ArgParser<GlobalParams>): Boolean {
    // offer some more options for showing help and to get help for a specific command
    val helpArguments = setOf("/?", "--?", "?", "--help", "help")
    val foundIdx = args.indexOfFirst { it in helpArguments }
    if (foundIdx >= 0) {
        val argumentsWithoutHelp = args.toMutableList()
        argumentsWithoutHelp.removeAt(foundIdx)
        logger.info(parser.printout(argumentsWithoutHelp.toTypedArray(), false))
        return true
    }
    return false
}


private fun loggerInfo(propertyName: String, propertyValue: Any?) {
    fun convertSingle(value: Any?): String? {
        if (value is String) return "\"$value\""
        if (value is Boolean) return BooleanParam().convertToStr(value)
        if (value is File) return FileParam().convertToStr(value)
//      if (value is TimeZone) return TimeZoneParam().convertToStr(value)
        if (value is IntRange) return IntRangeParam().convertToStr(value)
        return if (value != null) value.toString() else ""
    }

    var value: Any? = propertyValue
    if (value is Collection<*>) {
        value = value.joinToString(transform = { convertSingle(it).toString() })
    } else {
        value = convertSingle(value)
    }
    logger.info("$propertyName = $value")
}
