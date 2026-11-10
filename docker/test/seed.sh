#!/bin/sh
#
# Copyright © 2026  Eurotech S.p.A.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Seeds a fresh hawkBit (in-memory database) with the data the integration tests expect,
# through the Management API. Creation order matters: on an empty database it gives the
# ids the tests use (software modules 1-3, distribution sets 1-3).
#
#   software module 1 "app"  (application): test_1
#   software module 2 "apps" (application): test_2, test_3
#   software module 3 "os"   (os):          test_4
#   distribution set 1 "osWithApps" (os_app): modules 1, 2, 3
#   distribution set 2 "os"         (os):     module 3
#   distribution set 3 "app"        (app):    module 1
#
# The tests wait for distribution set 3, so it is created last.

set -eu

HAWKBIT_URL=${HAWKBIT_URL:-http://update-server:8080}
FIXTURES=${FIXTURES:-/fixtures}
AUTH=${AUTH:-test:test}
GATEWAY_TOKEN=${GATEWAY_TOKEN:-66076ab945a127dd80b15e9011995109}
API="$HAWKBIT_URL/rest/v1"

# POST/PUT a JSON body; print the response, fail on any HTTP error
call() {
    curl -sS -f -u "$AUTH" -H 'Content-Type: application/json' -H 'Accept: application/json' \
        -X "$1" -d "$3" "$API$2"
}

# first "id" in a JSON response
first_id() {
    grep -o '"id":[0-9]*' | head -n 1 | cut -d: -f2
}

expect_id() {
    if [ "$2" != "$3" ]; then
        echo "seed: $1 got id $2, expected $3 (the database was not empty?)" >&2
        exit 1
    fi
}

create_module() { # name type expected-id
    id=$(call POST /softwaremodules "[{\"name\":\"$1\",\"version\":\"1\",\"type\":\"$2\"}]" | first_id)
    expect_id "software module $1" "$id" "$3"
}

upload() { # module-id artifact-name fixture-file
    curl -sS -f -u "$AUTH" -H 'Accept: application/json' \
        -F "file=@$FIXTURES/$3;filename=$2" "$API/softwaremodules/$1/artifacts" > /dev/null
}

create_set() { # name type modules-json expected-id
    id=$(call POST /distributionsets \
        "[{\"name\":\"$1\",\"version\":\"1\",\"type\":\"$2\",\"modules\":$3}]" | first_id)
    expect_id "distribution set $1" "$id" "$4"
}

echo "seed: waiting for $HAWKBIT_URL"
# the first authenticated call also creates the tenant
until curl -s -f -o /dev/null -u "$AUTH" "$API/system/configs"; do sleep 2; done

call PUT /system/configs \
    "{\"authentication.gatewaytoken.enabled\":true,\"authentication.gatewaytoken.key\":\"$GATEWAY_TOKEN\"}" > /dev/null

create_module app application 1
upload 1 test_1 4b/5a/b54e43082887d1e7cdb10b7a21fe4a1e56b44b5a
create_module apps application 2
upload 2 test_2 b6/1e/a096a9d3cb96fa4cf6c63bd736a84cb7a7e4b61e
upload 2 test_3 bf/94/cde0c01b26634f869bb876326e4fbe969792bf94
create_module os os 3
upload 3 test_4 dd/0a/07fa4d03ac54d0b2a52f23d8e878c96db7aadd0a

create_set osWithApps os_app '[{"id":1},{"id":2},{"id":3}]' 1
create_set os os '[{"id":3}]' 2
create_set app app '[{"id":1}]' 3

echo "seed: done"
