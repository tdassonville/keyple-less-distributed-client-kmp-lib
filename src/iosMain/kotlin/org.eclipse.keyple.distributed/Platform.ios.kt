package org.eclipse.keyple.distributed

import platform.Foundation.NSUUID

actual fun randomUUID(): String {
    return NSUUID.UUID().toString()
}