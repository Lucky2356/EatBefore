package com.eatbefore.core.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the stored folder URI into something the exchange can list and write to.
 *
 * A seam, not an abstraction for its own sake: everything around the merge rules was
 * covered, while the half that actually touches the shared folder was not, because a SAF
 * tree URI needs a document provider that a JVM test does not have. With this in the way,
 * a test can hand [SyncManager] an ordinary directory.
 */
fun interface SyncFolderResolver {
    fun resolve(folderUri: String): DocumentFile?
}

@Singleton
class SafSyncFolderResolver @Inject constructor(@ApplicationContext private val context: Context) : SyncFolderResolver {
    override fun resolve(folderUri: String): DocumentFile? =
        DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
}
