package org.eclipse.keyple.distributed

actual fun randomUUID(): String {
    return java.util.UUID.randomUUID().toString()
}