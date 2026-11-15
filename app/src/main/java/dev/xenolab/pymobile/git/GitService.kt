package dev.xenolab.pymobile.git

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

enum class ChangeKind { ADDED, MODIFIED, REMOVED, UNTRACKED, CONFLICT }

data class GitChange(val path: String, val kind: ChangeKind)

data class GitStatusInfo(val isRepo: Boolean, val branch: String? = null, val changes: List<GitChange> = emptyList())

data class GitCommitInfo(val id: String, val message: String, val author: String, val timeMillis: Long)

/** A GitHub-style token login. Only HTTPS remotes are supported. */
data class GitCredentials(val user: String, val token: String)

/** Git for a project folder, using JGit (a Java implementation: there is no git program on the phone). */
class GitService {

    fun isRepo(dir: File): Boolean = File(dir, ".git").isDirectory

    fun init(dir: File) {
        Git.init().setDirectory(dir).setInitialBranch("main").call().close()
    }

    fun status(dir: File): GitStatusInfo {
        if (!isRepo(dir)) return GitStatusInfo(isRepo = false)
        Git.open(dir).use { git ->
            val s = git.status().call()
            val changes = ArrayList<GitChange>()
            s.added.forEach { changes += GitChange(it, ChangeKind.ADDED) }
            (s.changed + s.modified).toSortedSet().forEach { changes += GitChange(it, ChangeKind.MODIFIED) }
            (s.removed + s.missing).toSortedSet().forEach { changes += GitChange(it, ChangeKind.REMOVED) }
            s.untracked.forEach { changes += GitChange(it, ChangeKind.UNTRACKED) }
            s.conflicting.forEach { changes += GitChange(it, ChangeKind.CONFLICT) }
            val branch = runCatching { git.repository.branch }.getOrNull()
            return GitStatusInfo(true, branch, changes.sortedBy { it.path })
        }
    }

    /** Stages everything (new, changed and deleted files) and commits. Returns the short commit id. */
    fun commitAll(dir: File, message: String, name: String, email: String): String {
        require(message.isNotBlank()) { "The commit message is empty" }
        Git.open(dir).use { git ->
            check(!git.status().call().isClean) { "Nothing to commit" }
            git.add().addFilepattern(".").call()
            git.add().setUpdate(true).addFilepattern(".").call()
            val commit = git.commit()
                .setMessage(message.trim())
                .setAuthor(name, email)
                .setCommitter(name, email)
                .call()
            return commit.id.name.take(7)
        }
    }

    fun log(dir: File, max: Int = 30): List<GitCommitInfo> {
        if (!isRepo(dir)) return emptyList()
        Git.open(dir).use { git ->
            if (git.repository.resolve(Constants.HEAD) == null) return emptyList()
            return git.log().setMaxCount(max).call().map {
                GitCommitInfo(it.id.name.take(7), it.shortMessage, it.authorIdent.name, it.commitTime * 1000L)
            }
        }
    }

    fun clone(url: String, dest: File, credentials: GitCredentials?) {
        require(isSupportedUrl(url)) { "Only https:// addresses are supported" }
        val command = Git.cloneRepository().setURI(url.trim()).setDirectory(dest)
        provider(credentials)?.let { command.setCredentialsProvider(it) }
        command.call().close()
    }

    fun pull(dir: File, credentials: GitCredentials?) {
        Git.open(dir).use { git ->
            val command = git.pull()
            provider(credentials)?.let { command.setCredentialsProvider(it) }
            val result = command.call()
            check(result.isSuccessful) { "Pull failed: ${result.mergeResult?.mergeStatus ?: "unknown reason"}" }
        }
    }

    fun push(dir: File, credentials: GitCredentials?) {
        Git.open(dir).use { git ->
            val command = git.push()
            provider(credentials)?.let { command.setCredentialsProvider(it) }
            for (result in command.call()) {
                for (update in result.remoteUpdates) {
                    val ok = update.status == RemoteRefUpdate.Status.OK || update.status == RemoteRefUpdate.Status.UP_TO_DATE
                    check(ok) { "Push rejected (${update.status}): ${update.message ?: update.remoteName}" }
                }
            }
        }
    }

    private fun provider(credentials: GitCredentials?): UsernamePasswordCredentialsProvider? =
        credentials?.takeIf { it.token.isNotBlank() }
            ?.let { UsernamePasswordCredentialsProvider(it.user.ifBlank { "token" }, it.token) }

    companion object {
        fun isSupportedUrl(url: String): Boolean = url.trim().startsWith("https://") && url.trim().length > 12

        /** "https://github.com/a/b.git" gives "b". */
        fun repoNameFromUrl(url: String): String =
            url.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "repo" }
    }
}
