<p align="center"><img src="hara_logo.png" width=20% height=20% ></p>
<h1 align="center">Eclipse Hara™ - hara-ddiclient</h1>
<p align="center">
<a href="https://github.com/eclipse-hara/hara-ddiclient/actions/workflows/pipeline-build.yml"><img alt="Build Status" src="https://github.com/eclipse-hara/hara-ddiclient/actions/workflows/pipeline-build.yml/badge.svg"></a>
<a href="https://sonarcloud.io/summary/new_code?id=eclipse-hara_hara-ddiclient"><img alt="Quality Gate Status" src="https://sonarcloud.io/api/project_badges/measure?project=eclipse-hara_hara-ddiclient&metric=alert_status"></a>
<a href="https://www.eclipse.org/legal/epl-2.0"><img alt="EPL-2.0 License" src="https://img.shields.io/badge/License-EPL%202.0-red.svg"></a>
<a href="#contributing"><img alt="PRs welcome" src="https://img.shields.io/badge/PRs-welcome-brightgreen.svg"></a>
</p>

Hara-ddiclient is a Kotlin library that facilitates and speeds up the development
of [DDI API](https://www.eclipse.org/hawkbit/apis/ddi_api/) clients for devices 
connecting to [hawkBit](https://eclipse.org/hawkbit/) servers. It can be used from
any JVM compatible language (Java, Kotlin, etc).
Hara-ddiclient is part of the [Eclipse Hara™ project](https://projects.eclipse.org/projects/iot.hawkbit.hara)

## Project structure

The hara-ddiclient project provides several software modules:
1. `ddi-consumer`: implementation of a REST client for hawkBit DDI API
1. `hara-ddiclient`: implementation of communication logic using actors (uses `ddi-consumer`)
1. `virtual-device`: a simple application using the hara-ddiclient library (uses `hara-ddiclient`). Its purpose is to provide a configurable "virtual device" and a reference example on how to use the library. Some features, like the [Updater](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/Updater.kt), are not implemented in the virtual device and are just mocked.

## Install
Here an example on how to import hara-ddiclient in a gradle project.

Add the JitPack repository to your build.gradle file at the end of repositories section:

```gradle
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```
Add the dependency

```gradle
dependencies {
        implementation 'com.github.eclipse:hara-ddiclient:Tag'
}
```

For additional information refer to [jitpack](https://jitpack.io/#eclipse-hara/hara-ddiclient).

## Build from source

To build this project from source:

```shell
./gradlew assemble
```

to build this project and run the tests (`docker compose v2` required):

```shell
./gradlew build
```

to build the `hara-virtual-device` docker image:

```shell
./gradlew buildImage
```

to connect the virtual devices to the [hawkBit sandbox](https://hawkbit.eclipseprojects.io/):

```shell
docker run -e HAWKBIT_GATEWAY_TOKEN=<gatewaytokenvalue> -e HAWKBIT_CONTROLLER_ID=<mycontrollerid> hara-virtual-device:<virtual-device-version>
```

for example:

```shell
docker run -e HAWKBIT_GATEWAY_TOKEN=50f600c6e7e517b98b008311b0a325eb -e HAWKBIT_CONTROLLER_ID=mydevice hara-virtual-device:2.0.0
```

Make sure the authentication method provided in the parameters is enabled in the ["System Config"](https://www.eclipse.org/hawkbit/concepts/authentication/#ddi-api-authentication-modes) page. Available virtual device parameters can be found in the [Configuration class](virtual-device/src/main/kotlin/org/eclipse/hara/ddiclient/virtualdevice/Configuration.kt).

## API usage

To learn how to use the hara-ddiclient library:
1. read the [APIs documentation](https://eclipse.github.io/hara-ddiclient/)
1. follow the [API usage example instructions](#api-usage-example)
1. poke around the [virtual-device source code](virtual-device/src/main/kotlin/org/eclipse/hara/ddiclient/virtualdevice/)

## API usage example

Create a class that implements the [DirectoryForArtifactsProvider](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/DirectoryForArtifactsProvider.kt) interface:

    class DirectoryForArtifactsProviderImpl(): DirectoryForArtifactsProvider {
        override fun directoryForArtifacts(): File {
            ...
        }
    }

Create a class that implements the [ConfigDataProvider](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/ConfigDataProvider.kt) interface:

    class ConfigDataProviderImpl(): ConfigDataProvider {
        override fun configData(): Map<String, String> {
            ...
        }
    }

Create a class that implements the [DeploymentPermitProvider](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/DeploymentPermitProvider.kt) interface:

    class DeploymentPermitProviderImpl: DeploymentPermitProvider {
        override fun downloadAllowed(): Deferred<Boolean> {
            ...
        }
        override fun updateAllowed(): Deferred<Boolean> {
            ...
        }
    }

Create a class that implements the [MessageListener](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/MessageListener.kt) interface:

    class MessageListenerImpl(): MessageListener {
            override fun onMessage(message: MessageListener.Message) {
            ...
        }
    }

Create a class that implements the [Updater](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/Updater.kt) interface:

    class UpdaterImpl(): Updater {
        override fun apply(modules: Set<Updater.SwModuleWithPath>,messenger: Updater.Messenger){
            ...
        }
    }

Create a class that implements the [DownloadBehavior](hara-ddiclient-api/src/main/kotlin/org/eclipse/hara/ddiclient/api/DownloadBehavior.kt) interface:

    class DownloadBehaviorImpl(): DownloadBehavior {
        override fun onAttempt(attempt: Int, artifactId:String, previousError: Throwable?): DownloadBehavior.Try {
            ...
        }
    }

Create the Client, add the provider and return the client:

    val client = HaraClientDefaultImpl()
    client.init(
        clientData,
        DirectoryForArtifactsProviderImpl(),
        ConfigDataProviderImpl(),
        DeploymentPermitProviderImpl(),
        listOf(MessageListenerImpl()),
        listOf(UpdaterImpl())
    )
    return client

## Contributing

To contribute to this project please [open a GitHub pull request](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests).

## Contact us

- Having questions about hara-ddiclient? Subscribe to the [hara-dev mailing list](https://accounts.eclipse.org/mailing-list/hara-dev) and post a question!
- Having issues with hara-ddiclient? Please [open a GitHub issue](https://github.com/eclipse-hara/hara-ddiclient/issues).

## Third-Party Libraries

For information on the libraries used by this project see [NOTICE](NOTICE.md).

## Authors

* **Daniele Sergio** - *Initial work* - [danielesergio](https://github.com/danielesergio).
* **Andrea Zoleo**
* **Diego Rondini**
* **Alberto Battiston**
* **Saeed Rezaee**

See also the list of [contributors](https://github.com/eclipse-hara/hara-ddiclient/graphs/contributors) who participated in this project.

## License

Copyright © 2017-2024, [Kynetics LLC](https://www.kynetics.com).

Released under the [EPLv2 License](https://www.eclipse.org/legal/epl-2.0).
