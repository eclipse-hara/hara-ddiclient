#!/bin/bash
#
# /*
#  * Copyright © 2017-2024  Kynetics  LLC
#  *
#  * This program and the accompanying materials are made
#  * available under the terms of the Eclipse Public License 2.0
#  * which is available at https://www.eclipse.org/legal/epl-2.0/
#  *
#  * SPDX-License-Identifier: EPL-2.0
#  */
#

./gradlew --init-script init.gradle cyclonedxBom --info --dependency-verification lenient
