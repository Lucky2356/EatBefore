package com.eatbefore.core.common.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a stored folder URI into something the app can list, read and write.
 *
 * A seam, not an abstraction for its own sake: a SAF tree URI needs a document provider
 * that a JVM test does not have, so the code that touches the user's folders — the
 * household exchange and the automatic backups — went untested precisely where it could
 * lose or overwrite files. With this in the way, a test can hand either of them an
 * ordinary directory.
 */
fun interface FolderResolver {
    fun resolve(folderUri: String): DocumentFile?
}

@Singleton
class SafFolderResolver @Inject constructor(@ApplicationContext private val context: Context) : FolderResolver {
    override fun resolve(folderUri: String): DocumentFile? =
        DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
}
