This document is the specification of the API dedicated to the Keyple Kotlin Multiplatform Distributed Client Library.  
It enables remote client communication across Android, iOS, and JVM platforms by providing a distributed architecture
layer for cross-platform applications to interact with a Keyple server, supporting NFC card interactions and remote
service execution.

This library requires two platform-specific implementations: a network client for server communication, and a local NFC
reader for card detection.  
It simplifies building interoperable apps that integrate NFC terminal functionality with remote distributed services,
leveraging Kotlin Multiplatform for code sharing.

The API offers both synchronous and asynchronous usage patterns, and supports custom card selection strategies via JSON
configuration.
