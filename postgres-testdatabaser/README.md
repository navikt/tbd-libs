Postgres testdatabaser
==========================

Tilbyr et overbygg for å parallelisere database-tester (E2E-tester?) 
uten at testene går i beina på hverandre.

Det funkerer slik at det opprettes én postgres-container
for hver unike appnavn, og hver postgres-container opretter
`n` antall databaser som kan gjenbrukes.

En test starter med å "ta" en tilkobling slik at ingen
andre tester kan bruke den samme.
Testen avsluttes med at tilkoblingen gis tilbake slik at andre tester kan bruke den.

## Eksempel

```kotlin
abstract class AbstractE2E {
    private companion object {
        private val databaseContainer = DatabaseContainers.container("navn-på-containeren", "person, melding")
    }
    
    private lateinit var dataSource: TestDataSource 
    
    @BeforeEach
    fun setup() {
        dataSource = databaseContainer.nyTilkobling()
    }
    
    @AfterEach
    fun teardown() {
        // gi tilbake tilkoblingen
        databaseContainer.droppTilkobling(dataSource)
    }
    
    @Test
    fun eksempel() {
        sessionOf(dataSource.ds) {
            ...
        }
    }
}

```