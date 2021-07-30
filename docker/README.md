# LAUNCH UPDATE-SERVER

```$shell
$docker-compose up
```

# DATA
- Tenant: Default
- Port: 8081

## UI
- username: test
- password: test

## SOFTWARE MODULES
- os: type os, 1 file named test_4
- app: type app, 1 file named test_1
- apps: type app, 2 file named test_2, test_3

## DISTRIBUTIONS
- osWithApps: type os with apps, with app, apps and os software module
- os: type os, with software module named os
- app: type app, with softwrae module named app

## DDI SECURITY:
- Gataway token: 66076ab945a127dd80b15e9011995109
- Target1 target token: 447fb8b5b3ea156470e852b94166a673
- Target2 target token: 0fe7b8c9de2102ec6bf305b6f66df5b2
- Target3 target token: 4a28d893bb841def706073c789c0f3a7

## Server request:
- Target1 server request: 
  * put target metadata
  * cancel update osWithApps;
  * apply update app;
- Target2 server request:
  * put target metadata
  * apply update os
- Target3 server request:
  * put target metadata
  * apply osWithApps
