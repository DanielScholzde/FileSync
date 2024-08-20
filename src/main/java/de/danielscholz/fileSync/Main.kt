package de.danielscholz.fileSync

import de.danielscholz.fileSync.actions.sync.SyncFiles
import de.danielscholz.fileSync.actions.sync.createPathMatcher
import de.danielscholz.fileSync.actions.sync.getFilter
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
            add(paramValues::syncName, StringParam(), required = true)
            add(paramValues::considerOtherIndexedFilesWithSyncName, StringParam())
            add(paramValues::sourceDir, FileParam(checkIsDir = true), required = true)
            add(paramValues::targetDir, FileParam(checkIsDir = true), required = true)
            add(paramValues::excludedPaths, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
            add(paramValues::encryptSourcePaths, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
            add(paramValues::encryptTargetPaths, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
            add(paramValues::passwordSource, StringParam())
            add(paramValues::passwordTarget, StringParam())
            add(paramValues::lockfileSourceDir, FileParam(checkIsDir = true))
            add(paramValues::lockfileTargetDir, FileParam(checkIsDir = true))
            add(paramValues::excludedFiles, StringSetParam(mapper = { it.replace('\\', '/') }, typeDescription = ""))
            add(paramValues::maxChangedFilesWarningPercent, IntParam())
            add(paramValues::minAllowedChanges, IntParam())
            add(paramValues::minDiskFreeSpacePercent, IntParam())
            add(paramValues::minDiskFreeSpaceMB, IntParam())
            add(paramValues::parallelIndexing, BooleanParam())
            add(paramValues::backupMode, BooleanParam())
            add(paramValues::confirmations, BooleanParam())
            add(paramValues::dryRun, BooleanParam())
            add(paramValues::skipIndexing, BooleanParam())
            add(paramValues::ignoreConflicts, BooleanParam())
            //add(paramValues::saveIndexResultDespiteDryRun, BooleanParam())
            add(paramValues::verbose, BooleanParam())
            add(paramValues::warnIfFileCopyHasNoOriginal, BooleanParam())
        }) {

        SyncFiles(
            paramValues,
            paramValues.sourceDir!!.canonicalFile,
            paramValues.targetDir!!.canonicalFile,
            getFilter(paramValues.excludedFiles + paramValues.defaultExcludedFiles, paramValues.excludedPaths + paramValues.defaultExcludedPaths),
            paramValues.encryptSourcePaths.map { createPathMatcher(it, true) },
            paramValues.encryptTargetPaths.map { createPathMatcher(it, true) },
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
