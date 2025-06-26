# Keyple Kotlin Multiplatform Distributed Client Library

## Overview

The **Keyple Interop Distributed JSON Client Library** is a Kotlin Multiplatform implementation enabling distributed remote
client communications across Android, iOS and desktop platforms. This library provides a distributed architecture layer
for remote terminals, making it easier to develop cross-platform applications connecting to a Keyple server.

## Documentation & Contribution Guide
Full documentation available at [keyple.org](https://keyple.org)

## Supported Platforms
- Android 7.0+ (API 24+)
- iOS
- JVM 17+

## Build
The code is built with **Gradle** and targets **Android**, **iOS**, and **JVM** platforms.
To build and publish the artifacts for all supported targets locally, use:
```
./gradlew publishToMavenLocal
```
Note: you need to use a mac to build or use iOS artifacts. Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…


## API Documentation
API documentation & class diagrams are available
at [docs.keyple.org/keyple-interop-jsonapi-client-kmp-lib](https://docs.keyple.org/keyple-interop-jsonapi-client-kmp-lib/)

You will need to provide two implementations to use this library: a network client, and a Local NFC reader.

* The network client is used to communicate with the Keyple server. Usually, it will be an HTTP client configured by you with basic or advanced authentication, allowing to communicate with your Keyple server.
  See the [demo app](https://github.com/calypsonet/keyple-demo-ticketing-reloading-remote/blob/main/client/kmp/composeApp/src/commonMain/kotlin/org/calypsonet/keyple/demo/reload/remote/network/SimpleHttpNetworkClient.kt) for a simple example, using Ktor with HTTP basic-auth.

* The Local NFC reader provides the actual NFC communication depending on the platform.
  For most use cases, you can just use the provided [mobile NFC Reader lib](https://github.com/eclipse-keyple/keyple-interop-jsonapi-client-kmp-lib) that supports Android, iOS and JVM desktop (using PC/SC NFC readers) out of the box.

Create a KeypleTerminal object and wait for a NFC card to be presented:
```kotlin
val keypleTerminal = KeypleTerminal(networkClient, nfcReader, "MY_CLIENT_ID")
// Wait for a card to be presented (in iOS, this will trigger the system mandatory NFC popup)

// Using the sync API (suspending):
val cardFound = keypleService.waitForCard() 
if (cardFound) readContracts()

// or using the Async API (callback)
keypleTerminal.waitForCard {
    // Card found...
    viewModelScope.launch(Dispatchers.IO) { readContracts() }
}
```

When we have a card on the NFC interface, we connect it to the Keyple server: 
```kotlin
fun readContracts() {
    when (keypleTerminal.executeRemoteService("MY_SERVICE_NAME", inputData, inputSerializer, outputSerializer)) {
        is KeypleResult.Failure -> {
            // Handle error, message is in result.error
        }
        is KeypleResult.Success -> {
            // Keyple transaction completed, result is in result.data. Check for applicative status and payload
        }
    }
}
```


It is strongly recommended that you implement a Card Selection Scenario strategy. See the [Selection JSON Specification here](https://keyple.org/user-guides/non-keyple-client/selection-json-specification/) to learn more.
Retrieve the JSON Selection strategy from your server, and pass it to the KeypleTerminal (prior to waiting for a card):
```kotlin
keypleTerminal.setCardSelectionScenarioJsonString(scenarioJsonString)
```