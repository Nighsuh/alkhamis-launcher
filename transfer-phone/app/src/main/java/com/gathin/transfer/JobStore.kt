package com.gathin.transfer

import android.content.Context
import java.io.File

/** The queue survives the service being killed, so the next start continues it. */
object JobStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "transfer-queue.txt")

    fun load(ctx: Context): List<Job> {
        val file = file(ctx)
        if (!file.isFile) return emptyList()
        return file.readLines().mapNotNull { parse(it) }
    }

    fun save(ctx: Context, jobs: List<Job>) {
        file(ctx).writeText(jobs.joinToString("\n") { encode(it) })
    }

    fun add(ctx: Context, job: Job) {
        save(ctx, load(ctx) + job)
    }

    fun remove(ctx: Context, id: String) {
        save(ctx, load(ctx).filter { it.id != id })
    }

    fun replace(ctx: Context, job: Job) {
        save(ctx, load(ctx).map { if (it.id == job.id) job else it })
    }

    private fun encode(job: Job): String {
        return listOf(job.id, job.host, job.port.toString(), job.pin, job.name, job.uri ?: "", job.text ?: "", job.attempts.toString())
            .joinToString("\t") { it.replace("\t", " ").replace("\n", " ") }
    }

    private fun parse(line: String): Job? {
        val p = line.split("\t")
        if (p.size < 8) return null
        return Job(p[0], p[1], p[2].toIntOrNull() ?: return null, p[3], p[4],
            p[5].ifEmpty { null }, p[6].ifEmpty { null }, p[7].toIntOrNull() ?: 0)
    }
}
