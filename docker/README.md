# LAUNCH UPDATE-SERVER

```$shell
cd test
docker compose up
```

`./gradlew test` does the same and waits until the server is seeded.

The server is hawkBit 1.1.0 with its in-memory database: every start is empty, and the `seed`
service fills it through the Management API ([seed.sh](test/seed.sh)). The artifact files are
in `test/artifactrepo`.

# DATA
- Tenant: DEFAULT
- Port: 8080

## MANAGEMENT API
- username: test
- password: test

## SOFTWARE MODULES
- app (id 1): type application, 1 file named test_1
- apps (id 2): type application, 2 files named test_2, test_3
- os (id 3): type os, 1 file named test_4

## DISTRIBUTIONS
- osWithApps (id 1): type os with apps, with app, apps and os software modules
- os (id 2): type os, with software module os
- app (id 3): type app, with software module app

## DDI SECURITY:
- Gateway token: 66076ab945a127dd80b15e9011995109

The tests create their own targets.
