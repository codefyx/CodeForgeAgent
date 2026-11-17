package com.codeforge.agent.nativebridge

object PtyBridge {
    val isAvailable: Boolean = try {
        System.loadLibrary("pty_bridge")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }

    external fun nativeOpenPty(masterFd: IntArray): String?
    external fun nativeSpawn(shellPath: String, cwd: String?, rows: Int, cols: Int, masterFd: Int): Int
    external fun nativeRead(fd: Int, buf: ByteArray, len: Int): Int
    external fun nativeWrite(fd: Int, buf: ByteArray, len: Int): Int
    external fun nativeResize(fd: Int, rows: Int, cols: Int)
    external fun nativeClose(fd: Int)
    external fun nativeWait(pid: Int): Int

    fun openPty(): Pair<Int, String?> {
        val arr = IntArray(1)
        val slave = nativeOpenPty(arr)
        return arr[0] to slave
    }
}