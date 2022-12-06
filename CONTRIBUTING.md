# Contributing to Eclipse Hara

Thanks for your interest in this project.

## Project description

Eclipse Hara™ provides a reference agent software implementation featuring the
Eclipse hawkBit device API. Such reference implementations are initially driven
by operating systems and application frameworks that today constitute the main
platforms for the majority of IoT and embedded devices. These devices include
but are not limited to: Open Embedded, Android, QT, etc. The scope of the
project is to fill the gap that was intentionally left out by the hawkbit
project. The purpose is to provide device update management and client
solutions for handling software updates on the device. By providing a solid open
source reference implementations of a hawkBit client, which is driven by the
fundamental use cases for updating a remote device, the project can be
beneficial toward the adoption of the hawkBit update server as a backend
solution. Fundamental blocks of the client design are: hawBit DDI Client, which
implements API towards the update server  the Service, which is the runtime
execution context of the DDI Client. The service includes the DDI client as a
library messaging systems (IPC) between the Service and the Service Consumer  
The Service Consumer is implemented in the Application context and it
communicates with the Service by using an interprocess communication mechanism
provided by the Server. The proposed model is independent from the particular
device operating system and all the blocks can be implemented in any language.
In particular the DDI Client implementation is based on a straightforward states
interaction which can serve as a reference for other implementations. The first
implementation has been developed to serve Android OS based embedded devices. In
fact, the lack of an OSS distribution model for Android OS and application
updates, that could be used in other specific industries other than consumer
context (smartphones), facilitates the adoption of existing OSS device
management systems for embedded Android and IoT appliances. In this scenario we
have seen the opportunity to use Eclipse hawkBit as the artifacts (Android apps
and OS updates) content delivery platform and of course we needed to handle such
artifacts on the device.  Because Android SDK is based on a JVM Runtime
environment, we have decided to develop the DDI Client block neutral with
respect to the operating system. In this way, the same code could be used in a
Linux operating system. Of course the Service and the IPC towards the service
consumer are Android specific, nevertheless a Linux based Service using DBUS as
IPC can  fit perfectly the reference design.  There are important aspects that
has to be considered in the update process which can be applied to any other
Platform/OS  in particular related to the particular update strategy: Single
copy update Dual copy update (A/B) Nowadays due to the larger size in MMC
memories, we have an increased number of devices implementing the redundant A/B
double copy update. Our current Android client implementation supports both.
HawkBit is a device neutral platform and it can provision artifacts also to
Microcotroller based embedded systems. Having identified a common artifacts
management workflow it is possible to provide an implementation based on free
RTOS by writing the just DDI Client block as a Task without the need of any
other sophistication.

* https://projects.eclipse.org/projects/iot.hawkbit.hara

## Terms of Use

This repository is subject to the Terms of Use of the Eclipse Foundation

* http://www.eclipse.org/legal/termsofuse.php

## Developer resources

Information regarding source code management, builds, coding standards, and
more.

* https://projects.eclipse.org/projects/iot.hawkbit.hara/developer

The project maintains the following source code repositories

* https://github.com/eclipse/hara-ddiclient

## Eclipse Development Process

This Eclipse Foundation open project is governed by the Eclipse Foundation
Development Process and operates under the terms of the Eclipse IP Policy.

* https://eclipse.org/projects/dev_process
* https://www.eclipse.org/org/documents/Eclipse_IP_Policy.pdf

## Eclipse Contributor Agreement

In order to be able to contribute to Eclipse Foundation projects you must
electronically sign the Eclipse Contributor Agreement (ECA).

* http://www.eclipse.org/legal/ECA.php

The ECA provides the Eclipse Foundation with a permanent record that you agree
that each of your contributions will comply with the commitments documented in
the Developer Certificate of Origin (DCO). Having an ECA on file associated with
the email address matching the "Author" field of your contribution's Git commits
fulfills the DCO's requirement that you sign-off on your contributions.

For more information, please see the Eclipse Committer Handbook:
https://www.eclipse.org/projects/handbook/#resources-commit

## Conventions

### License Header

Please make sure newly created files contain a proper license header like this:

```kotlin
/*
 * Copyright (c) <year> <author> and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
```

## Making your changes

* Fork the repository on GitHub
* Create a new branch for your changes
* Make your changes
* Make sure you include tests
* Make sure the tests pass after your changes
* Commit your changes into that branch
* Use descriptive and meaningful commit messages
* If you have a lot of commits squash them into a single commit
* Make sure you use the `-s` flag when committing as explained above.
* Push your changes to your branch in your forked repository

## Submitting the changes

Submit a pull request via the normal GitHub UI (desktop or web).

## After submitting

* Do not use your branch for any other development, otherwise further changes that you make will be visible in the PR.

## Reporting a security vulnerability

If you find a vulnerability, **DO NOT** disclose it in the public immediately! Instead, give us the possibility to fix it beforehand.
So please don’t report your finding using GitHub issues and better head over to [https://eclipse.org/security](https://eclipse.org/security) and learn how to disclose a vulnerability in a safe and responsible manner

## Further information

* [Eclipse Project Page](http://projects.eclipse.org/projects/iot.hara)

## Contact

Contact the project developers via the project's "dev" list.

* https://accounts.eclipse.org/mailing-list/hara-dev