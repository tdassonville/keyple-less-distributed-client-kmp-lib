package org.eclipse.keyple.distributed

import java.util.UUID

actual fun randomUUID(): String {
    return UUID.randomUUID().toString()
}