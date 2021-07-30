<p align="center"><img src="hara_logo.png" width=20% height=20% ></p>
<h1 align="center">Eclipse Hara™ - hara-ddiclient</h1>
<p align="center">
<a href="https://www.eclipse.org/legal/epl-2.0"><img alt="License" src="https://img.shields.io/badge/License-EPL%202.0-red.svg"></a>
</p>

Hara-ddiclient is a Kotlin library that facilitates and speeds up the development
of [DDI API](https://www.eclipse.org/hawkbit/apis/ddi_api/) clients for devices 
connecting to [hawkBit](https://eclipse.org/hawkbit/) servers. It can be used from
any JVM compatible language (Java, Kotlin, etc).
Hara-ddiclient is part of the [Eclipse Hara™ project](https://projects.eclipse.org/projects/iot.hawkbit.hara)

## Project structure

The hara-ddiclient project provides several software modules:
1. `ddiapi-client`: implementation of a REST client for hawkBit DDI API
1. `core`: implementation of communication logic using actors (uses `ddiapi-client`)
1. `virtual-device`: a simple application using the hara-ddiclient library (uses `core`). Its purpose is to provide a configurable "virtual device" and a reference example on how to use the library. Some features, like the [Updater](core/core-api/src/main/kotlin/org/eclipse/hara/ddiclient/core/api/Updater.kt), are not implemented in the virtual device and are just mocked.

## Install

To import this project use [jitpack](https://jitpack.io/) plugin.

## Build from source

To build this project from source:

```shell
./gradlew assemble
```

to build this project and run the tests (`docker 17.09.0+` and `docker-compose 1.27.0+` required):

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
docker run -e HAWKBIT_GATEWAY_TOKEN=50f600c6e7e517b98b008311b0a325eb -e HAWKBIT_CONTROLLER_ID=mydevice hara-virtual-device:1.0.0
```

Make sure the authentication method provided in the parameters is enabled in the ["System Config"](https://www.eclipse.org/hawkbit/concepts/authentication/#ddi-api-authentication-modes) page. Available virtual device parameters can be found in the [Configuration class](virtual-device/src/main/kotlin/org/eclipse/hara/ddiclient/virtualdevice/Configuration.kt).

## Example

Create a class that implements the [DirectoryForArtifactsProvider](core/core-api/src/main/kotlin/org/eclipse/hara/ddiclient/core/api/DirectoryForArtifactsProvider.kt) interface:

    class DirectoryForArtifactsProviderImpl(): DirectoryForArtifactsProvider {
        override fun directoryForArtifacts(): File {
            ...
        }
    }

Create a class that implements the [ConfigDataProvider](core/core-api/src/main/kotlin/org/eclipse/hara/ddiclient/core/api/ConfigDataProvider.kt) interface:

    class ConfigDataProviderImpl(): ConfigDataProvider {
        override fun configData(): Map<String, String> {
            ...
        }
    }

Create a class that implements the [DeploymentPermitProvider](core/core-api/src/main/kotlin/org/eclipse/hara/ddiclient/core/api/DeploymentPermitProvider.kt) interface:

    class DeploymentPermitProviderImpl: DeploymentPermitProvider {
        override fun downloadAllowed(): Deferred<Boolean> {
            ...
        }
        override fun updateAllowed(): Deferred<Boolean> {
            ...
        }
    }

Create a class that implements the [MessageListener](core/core-api/src/main/kotlin/org/eclipse/hara/ddiclient/core/api/MessageListener.kt) interface:

    class MessageListenerImpl(): MessageListener {
            override fun onMessage(message: MessageListener.Message) {
            ...
        }
    }

Create a class that implements the [Updater](core/core-api/src/main/kotlin/org/eclipse/hara/ddiclient/core/api/Updater.kt) interface:

    class UpdaterImpl(): Updater {
        override fun apply(modules: Set<Updater.SwModuleWithPath>,messenger: Updater.Messenger){
            ...
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

To contribute to this project please [open a GitHub pull request](https://docs.github.com/en/github/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/about-pull-requests).

## Contact us

- Having questions about hara-ddiclient? Subscribe to the [hara-dev mailing list](https://accounts.eclipse.org/mailing-list/hara-dev) and post a question!
- Having issues with hara-ddiclient? Please open a GitHub issue.

## Third-Party Libraries

For information on the libraries used by this project see [NOTICE](NOTICE.md).

## Authors

* **Daniele Sergio** - *Initial work* - [danielesergio](https://github.com/danielesergio).

See also the list of [contributors](https://github.com/Kynetics/hara-ddiclient/graphs/contributors) who participated in this project.

## License

Copyright © 2017-2021, [Kynetics LLC](https://www.kynetics.com).

Released under the [EPLv2 License](https://www.eclipse.org/legal/epl-2.0).
