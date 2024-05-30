package de.danielscholz.fileSync.common

import java.io.IOException


class FileSizeChangedException : IOException("File size changed!")

class CancelException : RuntimeException("Cancel")