package cn.zhengshang.highspeedvideodemo

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.util.Log
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author chenjim me@h89.cn
 * @description 可以打印当前的线程名，所在代码中的位置等信息
 * modify from https://github.com/orhanobut/logger
 * @date 2016/9/26.
 */
object Logger {
    /**
     * Android's max limit for a log entry is ~4076 bytes,
     * so 4000 bytes is used as chunk size since default charset
     * is UTF-8
     */
    private const val CHUNK_SIZE = 4000

    /**
     * Log default out level
     * [Log.ASSERT]>[Log.ERROR]>[Log.WARN]
     * >[Log.INFO]>[Log.DEBUG]>[Log.VERBOSE]
     */
    var logLevel = Log.VERBOSE

    /**
     * 不进一步封装，为4
     * 若进一步封装，此值需要改变
     */
    private const val STACK_INDEX = 4

    /**
     * 单个文件限制大小
     */
    private var logFileMaxLen = 20 * 1024 * 1024L
    var logDir: File? = null
        private set
    private const val CUR_LOG_NAME = "log1.txt"
    private const val LAST_LOG_NAME = "log2.txt"
    private val dateFormat by lazy { SimpleDateFormat("yyMMdd_HHmmss_SSS", Locale.CHINA) }

    private val logHandler by lazy {
        val thread = HandlerThread(Logger::class.java.simpleName)
        thread.start()
        Handler(thread.looper)
    }

    /**
     * 初始化，不是必须
     * 当需要写入到日志文件时，必需
     * 日志文件位置'/sdcard/Android/data/com.xxx.xxx/files/log/'
     *
     * @param writeFileContext 空，不写入日志
     * 非空，写入日志，用来获取应用的数据存储目录，
     * 不需要权限[Context.getExternalFilesDir]
     * @param len 每个日日志文件大小
     * @param level  默认值 [logLevel]
     */
    @JvmStatic
    fun init(writeFileContext: Context?, len: Long = logFileMaxLen, level: Int = Log.VERBOSE) {
        if (writeFileContext != null) {
            val path = File(writeFileContext.getExternalFilesDir(null), "log")
            if (!path.exists()) {
                path.mkdirs()
            }
            logDir = path
            d("write log to dir:" + logDir!!.path)
        }
        logLevel = level
        logFileMaxLen = len
    }

    @JvmStatic
    fun d() {
        log(Log.DEBUG, null, null)
    }

    private fun objectToString(obj: Any?): String {
        return when {
            obj == null -> {
                "null"
            }
            obj.javaClass.isArray -> {
                (obj as Array<*>).contentDeepToString()
            }
            obj is List<*> -> {
                val objects: Array<Any?> = obj.toTypedArray()
                objects.contentDeepToString()
            }
            obj is Map<*, *> -> {
                var ret = "{"
                obj.forEach { (key, value) ->
                    ret = "$ret[$key:$value],"
                }
                ret = "$ret}"
                ret
            }
            obj is Throwable -> {
                obj.printStackTrace()
                obj.toString()
            }
            else -> {
                obj.toString()
            }
        }
    }

    @JvmStatic
    fun d(obj: Any?) {
        log(Log.DEBUG, null, objectToString(obj))
    }

    @JvmStatic
    fun d(tag: String?, message: String?) {
        log(Log.DEBUG, tag, message)
    }

    @JvmStatic
    fun e(obj: Any?) {
        log(Log.ERROR, null, objectToString(obj))
    }

    @JvmStatic
    fun e(tag: String, message: String) {
        log(Log.ERROR, tag, message)
    }

    @JvmStatic
    fun e(tag: String, e: Exception?) {
        log(Log.ERROR, tag, e?.toString())
    }

    @JvmStatic
    fun e(tag: String, message: String, e: Exception) {
        val messageFinal = "$message:$e"
        log(Log.ERROR, tag, messageFinal)
    }

    @JvmStatic
    fun w(obj: Any?) {
        log(Log.WARN, null, objectToString(obj))
    }

    @JvmStatic
    fun w(tag: String, message: String) {
        log(Log.WARN, tag, message)
    }

    @JvmStatic
    fun i() {
        log(Log.INFO, null, null)
    }

    @JvmStatic
    fun i(obj: Any?) {
        log(Log.INFO, null, objectToString(obj))
    }

    @JvmStatic
    fun i(tag: String, message: String) {
        log(Log.INFO, tag, message)
    }

    @JvmStatic
    fun v() {
        log(Log.VERBOSE, null, null)
    }

    @JvmStatic
    fun v(message: String) {
        log(Log.VERBOSE, null, message)
    }

    @JvmStatic
    fun v(tag: String, message: String) {
        log(Log.VERBOSE, tag, message)
    }

    private fun log(logType: Int, tag: String? = null, message: String? = null) {
        if (logType < logLevel) {
            return
        }
        log(logType, tag, message, Thread.currentThread().stackTrace[STACK_INDEX])
    }

    private fun log(
        logType: Int,
        tag: String? = null,
        message: String?,
        element: StackTraceElement
    ) {
        if (logType < logLevel) {
            return
        }
        val curThread = Thread.currentThread()
        val threadName = curThread.name
        logHandler.post { logOut(element, threadName, logType, tag, message) }
    }

    private fun logOut(element: StackTraceElement, threadName: String, logType: Int, tag: String?, message: String?) {
        val methodMaxLen = element.methodName.length.coerceAtMost(35)
        val method = element.methodName.subSequence(0, methodMaxLen)
        val header = "$threadName,(${element.fileName}:${element.lineNumber}),${method}()"
        var tagFinal = if (tag.isNullOrEmpty()) {
            element.fileName
        } else {
            tag
        }

        // get bytes of message with system's default charset (which is UTF-8 for Android)
        val bytes = message?.toByteArray() ?: "".toByteArray()
        val length = bytes.size
        var i = 0
        // 日志 TAG 长度限制已经在 Android 7.0 被移除
        if (tagFinal.length > 23 && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            tagFinal = tagFinal.substring(0, 23)
        }

        while (i <= length) {
            val count = (length - i).coerceAtMost(CHUNK_SIZE)
            // create a new String with system's default charset (which is UTF-8 for Android)
            logChunk(logType, tagFinal, "$header,${String(bytes, i, count)}")
            i += CHUNK_SIZE
        }
    }

    /**
     * @return Who(file name + line+ function name) call current function
     */
    @JvmStatic
    fun getParentCallLineInfo(): String {
        val trace = Thread.currentThread().stackTrace
        val builder = StringBuilder()
        if (trace.size > STACK_INDEX) {
            val element = trace[STACK_INDEX]
            builder.append("(")
                .append(element.fileName)
                .append(":")
                .append(element.lineNumber)
                .append("),")
                .append(element.methodName)
                .append("()")
        }
        return builder.toString()
    }

    private fun logChunk(logType: Int, tag: String, chunk: String) {
        when (logType) {
            Log.ERROR -> Log.e(tag, chunk)
            Log.WARN -> Log.w(tag, chunk)
            Log.INFO -> Log.i(tag, chunk)
            Log.DEBUG -> Log.d(tag, chunk)
            Log.VERBOSE -> Log.v(tag, chunk)
            else -> {
            }
        }
        writeLogToFile(logType, tag, chunk)
    }

    private fun getTypeString(logType: Int): String {
        var type = "D"
        when (logType) {
            Log.ERROR -> type = "E"
            Log.WARN -> type = "W"
            Log.INFO -> type = "I"
            Log.DEBUG -> type = "D"
            Log.VERBOSE -> type = "V"
            else -> {
            }
        }
        return type
    }

    @Synchronized
    private fun writeLogToFile(logType: Int, tag: String, msg: String) {
        if (logDir == null) {
            return
        }
        val date = dateFormat.format(Date())
        val data = "[${Process.myPid()}][$date: ${getTypeString(logType)}/$tag]$msg\r\n"
        logHandler.post { doWriteDisk(data) }
    }

    private fun doWriteDisk(msg: String) {
        val curFile = File(logDir, CUR_LOG_NAME)
        val oldFile = File(logDir, LAST_LOG_NAME)
        if (curFile.length() > logFileMaxLen && !curFile.renameTo(oldFile)) {
            return
        }
        try {
            FileWriter(curFile, true).use { writer ->
                writer.write(msg)
                writer.flush()
            }
        } catch (exception: IOException) {
            exception.printStackTrace()
        }
    }

    private fun readLogFile(file: File, readLen: Long): StringBuilder {
        val data = StringBuilder()
        try {
            BufferedReader(FileReader(file)).use { reader ->
                val skip = if (file.length() > readLen) file.length() - readLen else 0
                reader.skip(skip)
                val length = if (readLen > file.length()) file.length() else readLen
                while (data.length < length) {
                    data.append(reader.readLine()).append("\r\n")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return data
    }

    /**
     * 读取最后一段log
     */
    @JvmStatic
    fun getLastLogX(maxSize: Long): String {
        val data = StringBuilder()
        val curLogFile = File(logDir, CUR_LOG_NAME)
        if (!curLogFile.exists()) {
            return data.toString()
        }

        // 是否需要读取上一个文件
        if (curLogFile.length() < maxSize) {
            val lastLogFile = File(logDir, LAST_LOG_NAME)
            val lastLogData = readLogFile(lastLogFile, maxSize - curLogFile.length())
            data.append(lastLogData)
        }
        val curLogData = readLogFile(curLogFile, maxSize)
        data.append(curLogData)
        return data.toString()
    }
}