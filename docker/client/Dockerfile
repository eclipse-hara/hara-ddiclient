#
# Copyright © 2017-2024  Kynetics, Inc.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
FROM eclipse-temurin:11

COPY ./virtual-device /client

WORKDIR /client

ENTRYPOINT ["/bin/bash", "-c", "./bin/virtual-device"]
