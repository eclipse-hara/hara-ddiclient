#!/bin/sh

#
# Copyright © 2017-2021  Kynetics  LLC
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
#SPDX-License-Identifier: EPL-2.0
#

puts "Checking kotlin code style..."

# stash any unstaged changes
git stash -q --keep-index

# check code respect kotlin style guide
./gradlew detekt

# store the last exit code in a variable
RESULT=$?

# unstash the unstashed changes
git stash pop -q

# return the './gradlew checkStyle' exit code
exit ${RESULT}