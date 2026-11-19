package com.mobilelinuxstudio.app.runtime

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.utils.CountingInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class CommandResult(val exitCode: Int, val output: String)

class LinuxRuntime(private val context: Context) {

    private val nativeDir: String = context.applicationInfo.nativeLibraryDir
    private val baseDir: File = File(context.filesDir, "mobile-linux")
    private val rootfs: File = File(baseDir, "rootfs")
    private val downloads: File = File(baseDir, "downloads")
    private val workspace: File = File(baseDir, "workspace")

    private val linuxMarker = File(rootfs, ".dsh-linux-studio-rootfs")
    private val nodeMarker = File(rootfs, ".dsh-linux-studio-node")
    private val dshMarker = File(rootfs, ".dsh-linux-studio-dsh")
    private val codeMarker = File(rootfs, ".dsh-linux-studio-code-server")

    private var dshProcess: Process? = null
    private var codeProcess: Process? = null

    fun ensureDirs() {
        arrayOf(baseDir, rootfs, downloads, workspace).forEach { it.mkdirs() }
        File(rootfs, "root/workspace").mkdirs()
        File(rootfs, "etc/profile.d").mkdirs()
        File(rootfs, "root/.dsh").mkdirs()
        File(rootfs, "tmp").mkdirs()
    }

    fun status(): RuntimeStatus {
        return RuntimeStatus(
            linuxInstalled = linuxMarker.exists(),
            nodeInstalled = nodeMarker.exists(),
            dshInstalled = dshMarker.exists(),
            codeServerInstalled = codeMarker.exists(),
            dshRunning = dshProcess?.isAlive == true,
            codeServerRunning = codeProcess?.isAlive == true,
            rootfsPath = rootfs.absolutePath,
            workspacePath = workspace.absolutePath
        )
    }

    suspend fun installLinux(onStep: (String, Int) -> Unit): CommandResult = withContext(Dispatchers.IO) {
        ensureDirs()
        try {
            if (!linuxMarker.exists()) {
                onStep("下载 Ubuntu 24.04 arm64 根文件系统", 5)
                val rootfsArchive = File(downloads, ROOTFS_FILE)
                download(
                    ROOTFS_URL,
                    ROOTFS_SHA256,
                    rootfsArchive,
                    onProgress = { p -> onStep("下载 Ubuntu 根文件系统 $p%", 5 + p / 5) }
                )

                onStep("校验并解压 Ubuntu 根文件系统", 30)
                extractTarGz(rootfsArchive, rootfs, stripComponents = 0) { p ->
                    onStep("解压 Ubuntu 根文件系统 $p%", 30 + p / 4)
                }

                configureRootfs()
                linuxMarker.writeText("ok\n")

                onStep("安装基础开发工具（git / curl / python3 / build-essential）", 55)
                val baseTools = execForResult(
                    "apt-get update -qq && " +
                        "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends " +
                        "git curl wget ca-certificates python3 python3-pip build-essential openssh-client"
                )
                if (baseTools.exitCode != 0) {
                    AppState.log("基础工具安装出现警告：${baseTools.output.takeLast(800)}")
                }
            }

            if (!nodeMarker.exists()) {
                onStep("下载 Node.js 24.22.3", 68)
                val nodeArchive = File(downloads, NODE_FILE)
                download(
                    NODE_URL,
                    NODE_SHA256,
                    nodeArchive,
                    onProgress = { p -> onStep("下载 Node.js $p%", 68 + p / 5) }
                )

                onStep("解压 Node.js", 78)
                val nodeDir = File(rootfs, "opt/node")
                nodeDir.mkdirs()
                extractTarGz(nodeArchive, nodeDir, stripComponents = 1) { p ->
                    onStep("解压 Node.js $p%", 78 + p / 5)
                }

                writeFile(
                    File(rootfs, "etc/profile.d/mobile-dev.sh"),
                    "export PATH=/opt/node/bin:\$PATH\n"
                )
                nodeMarker.writeText("ok\n")
            }

            onStep("Linux 环境就绪", 100)
            CommandResult(0, "Linux environment ready: ${rootfs.absolutePath}")
        } catch (e: Exception) {
            AppState.log("安装 Linux 环境失败：${e.message}")
            CommandResult(-1, e.stackTraceToString())
        }
    }

    suspend fun installDsh(onStep: (String, Int) -> Unit): CommandResult = withContext(Dispatchers.IO) {
        ensureDirs()
        if (!nodeMarker.exists()) {
            return@withContext CommandResult(-1, "请先安装 Linux 环境和 Node.js")
        }
        try {
            onStep("正在安装 @deepseek-ai/dsh", 20)
            val result = execForResult(
                "npm install -g @deepseek-ai/dsh@latest --registry=https://registry.npmjs.org"
            )
            if (result.exitCode != 0) {
                return@withContext CommandResult(result.exitCode, result.output)
            }

            onStep("验证 dsh 版本", 80)
            val version = execForResult("dsh --version")
            if (version.exitCode != 0) {
                return@withContext CommandResult(version.exitCode, version.output)
            }
            dshMarker.writeText("ok\n")
            onStep("DeepSeek Harness 安装完成", 100)
            version
        } catch (e: Exception) {
            CommandResult(-1, e.stackTraceToString())
        }
    }

    suspend fun installCodeServer(onStep: (String, Int) -> Unit): CommandResult = withContext(Dispatchers.IO) {
        ensureDirs()
        if (!nodeMarker.exists()) {
            return@withContext CommandResult(-1, "请先安装 Linux 环境和 Node.js")
        }
        try {
            onStep("正在安装 code-server", 30)
            val result = execForResult(
                "npm install -g code-server --registry=https://registry.npmjs.org"
            )
            if (result.exitCode == 0) {
                codeMarker.writeText("ok\n")
            }
            onStep("code-server 安装完成", 100)
            result
        } catch (e: Exception) {
            CommandResult(-1, e.stackTraceToString())
        }
    }

    @Synchronized
    fun startDsh(apiKey: String, port: Int): CommandResult {
        if (!dshMarker.exists()) return CommandResult(-1, "DeepSeek Harness 尚未安装")
        stopDsh()
        val extra = mutableMapOf<String, String>()
        if (apiKey.isNotBlank()) {
            extra["DEEPSEEK_API_KEY"] = apiKey
        }
        val command = "dsh web --no-open --host 127.0.0.1 --port $port"
        dshProcess = startGuest(command, extra)
        AppState.setDshUrl("http://127.0.0.1:$port")
        return CommandResult(0, "dsh 已启动：${AppState.dshUrl.value}")
    }

    @Synchronized
    fun dshProcessOrNull(): Process? = dshProcess

    @Synchronized
    fun stopDsh() {
        dshProcess?.let { runCatching { it.destroy() } }
        dshProcess = null
    }

    @Synchronized
    fun startCodeServer(port: Int): CommandResult {
        if (!codeMarker.exists()) return CommandResult(-1, "code-server 尚未安装")
        stopCodeServer()
        val command = "code-server --host 127.0.0.1 --port $port --auth none " +
            "--user-data-dir /root/.local/share/code-server"
        codeProcess = startGuest(command, emptyMap())
        AppState.setCodeUrl("http://127.0.0.1:$port")
        return CommandResult(0, "code-server 已启动：${AppState.codeUrl.value}")
    }

    @Synchronized
    fun codeProcessOrNull(): Process? = codeProcess

    @Synchronized
    fun stopCodeServer() {
        codeProcess?.let { runCatching { it.destroy() } }
        codeProcess = null
    }

    fun startTerminal(command: String): Process {
        return startGuest(command, emptyMap())
    }

    fun execForResult(command: String, extraEnv: Map<String, String> = emptyMap()): CommandResult {
        val process = startGuest(command, extraEnv)
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val finished = process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            return CommandResult(-1, "$output\n命令超时")
        }
        return CommandResult(process.exitValue(), output)
    }

    private fun startGuest(command: String, extraEnv: Map<String, String>): Process {
        ensureDirs()
        val proot = File(nativeDir, "libproot.so").absolutePath
        val loader = File(nativeDir, "libprootloader.so").absolutePath
        val args = mutableListOf(
            proot,
            "--kill-on-exit",
            "-0",
            "-r", rootfs.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${workspace.absolutePath}:/root/workspace",
            "-w", "/root/workspace",
            "/bin/bash",
            "-lc",
            command
        )

        val builder = ProcessBuilder(args)
        builder.environment().putAll(baseEnv(loader))
        builder.environment().putAll(extraEnv)
        builder.redirectErrorStream(true)
        return builder.start()
    }

    private fun baseEnv(loader: String): Map<String, String> {
        val nodeBin = File(rootfs, "opt/node/bin").absolutePath
        return mapOf(
            "PATH" to "$nodeBin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME" to "/root",
            "USER" to "root",
            "LOGNAME" to "root",
            "SHELL" to "/bin/bash",
            "TERM" to "xterm-256color",
            "LANG" to "C.UTF-8",
            "LC_ALL" to "C.UTF-8",
            "DEBIAN_FRONTEND" to "noninteractive",
            "TMPDIR" to "/tmp",
            "DSH_HOME" to "/root/.dsh",
            "PROOT_TMP_DIR" to context.cacheDir.absolutePath,
            "PROOT_LOADER" to loader,
            "LD_LIBRARY_PATH" to nativeDir
        )
    }

    private fun configureRootfs() {
        writeFile(
            File(rootfs, "etc/resolv.conf"),
            "nameserver 223.5.5.5\nnameserver 119.29.29.29\nnameserver 8.8.8.8\n"
        )
        writeFile(
            File(rootfs, "etc/apt/sources.list"),
            "deb http://ports.ubuntu.com/ubuntu-ports noble main restricted universe multiverse\n" +
                "deb http://ports.ubuntu.com/ubuntu-ports noble-updates main restricted universe multiverse\n" +
                "deb http://ports.ubuntu.com/ubuntu-ports noble-security main restricted universe multiverse\n"
        )
        writeFile(
            File(rootfs, "etc/profile.d/mobile-dev.sh"),
            "export PATH=/opt/node/bin:\$PATH\n"
        )
        writeFile(File(rootfs, "root/.bashrc"), "export PATH=/opt/node/bin:\$PATH\nPS1='[dsh-studio \\w]# '\n")
    }

    private fun writeFile(file: File, content: String, executable: Boolean = false) {
        file.parentFile?.mkdirs()
        file.writeText(content)
        if (executable) file.setExecutable(true, false)
    }

    private fun download(
        url: String,
        expectedSha256: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        if (target.exists() && sha256(target).equals(expectedSha256, ignoreCase = true)) {
            onProgress(100)
            return
        }
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DshLinuxStudio/0.1")
        }
        connection.use { conn ->
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("下载失败 HTTP $code: $url")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val total = conn.contentLengthLong
            var downloaded = 0L
            var lastPercent = -1
            BufferedOutputStream(FileOutputStream(temp, false)).use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = min(99, (downloaded * 100 / total).toInt())
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                temp.delete()
                throw IllegalStateException("SHA256 校验失败：期望 $expectedSha256，实际 $actual")
            }
        }
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        onProgress(100)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractTarGz(
        archive: File,
        dest: File,
        stripComponents: Int,
        onProgress: (Int) -> Unit
    ) {
        val root = dest.toPath().toAbsolutePath().normalize()
        val counting = CountingInputStream(BufferedInputStream(FileInputStream(archive)))
        val total = archive.length().coerceAtLeast(1)
        var lastPercent = -1
        GZIPInputStreamWrapper(counting).use { gzip ->
            TarArchiveInputStream(gzip).use { tar ->
                var entry: TarArchiveEntry? = tar.nextTarEntry
                while (entry != null) {
                    val parts = entry.name.split('/').filter { it.isNotBlank() }
                    val relative = if (stripComponents > 0) parts.drop(stripComponents) else parts
                    if (relative.isNotEmpty()) {
                        val target = relative.fold(root) { path, part -> path.resolve(part) }.normalize()
                        if (!target.startsWith(root)) {
                            throw IllegalStateException("不安全的压缩包路径：${entry.name}")
                        }
                        val file = target.toFile()
                        when {
                            entry.isDirectory -> file.mkdirs()
                            entry.isSymbolicLink -> {
                                file.parentFile?.mkdirs()
                                if (file.exists() || Files.isSymbolicLink(target)) {
                                    Files.deleteIfExists(target)
                                }
                                runCatching {
                                    Files.createSymbolicLink(target, Paths.get(entry.linkName))
                                }
                            }
                            entry.isLink -> {
                                file.parentFile?.mkdirs()
                                val linkTarget = root.resolve(entry.linkName).normalize()
                                if (Files.exists(linkTarget)) {
                                    runCatching { Files.createLink(target, linkTarget) }
                                }
                            }
                            entry.isFile -> {
                                file.parentFile?.mkdirs()
                                FileOutputStream(file, false).use { out ->
                                    tar.copyTo(out, 64 * 1024)
                                }
                                if (entry.mode and 0b111 != 0) {
                                    file.setExecutable(true, false)
                                }
                            }
                        }
                    }
                    val percent = min(99, (counting.bytesRead * 100 / total).toInt())
                    if (percent != lastPercent) {
                        lastPercent = percent
                        onProgress(percent)
                    }
                    entry = tar.nextTarEntry
                }
            }
        }
        onProgress(100)
    }

    private class GZIPInputStreamWrapper(input: InputStream) : java.util.zip.GZIPInputStream(input)

    private inline fun HttpURLConnection.use(block: (HttpURLConnection) -> Unit) {
        try {
            block(this)
        } finally {
            disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MINUTES = 15L

        private const val ROOTFS_FILE = "ubuntu-base-24.04.5-base-arm64.tar.gz"
        private const val ROOTFS_URL =
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/$ROOTFS_FILE"
        private const val ROOTFS_SHA256 =
            "a91d5a93010193712d346d761372b7c9db6dfcf093893161c64ca107f05914f2"

        private const val NODE_FILE = "node-v24.22.3-linux-arm64.tar.gz"
        private const val NODE_URL = "https://nodejs.org/dist/v24.22.3/$NODE_FILE"
        private const val NODE_SHA256 =
            "261af2af250dfd7e9f25cf151d1b2713a0adf8f10a9ad116c4947f68627affe8"
    }
}
