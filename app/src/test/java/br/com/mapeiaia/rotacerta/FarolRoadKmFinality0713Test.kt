package br.com.mapeiaia.rotacerta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FarolRoadKmFinality0713Test {
    @Test fun localHaversineIsNeverPublic() {
        assertNull(FarolRoadKmFinality0713.publicDistanceKm(FarolRoadKmFinality0713.DistanceAuthority.LOCAL_HAVERSINE, 2.769))
    }
    @Test fun confirmedRoadDistanceIsPublic() {
        assertEquals(3.653, FarolRoadKmFinality0713.publicDistanceKm(FarolRoadKmFinality0713.DistanceAuthority.ROAD_CONFIRMED, 3.653)!!, 0.000001)
    }
    @Test fun invalidRoadDistanceCannotPublish() {
        assertNull(FarolRoadKmFinality0713.publicDistanceKm(FarolRoadKmFinality0713.DistanceAuthority.ROAD_CONFIRMED, Double.NaN))
        assertFalse(FarolRoadKmFinality0713.isPublishableRoadDistance(-1.0))
    }
    @Test fun sameBindingPreservesConfirmedRoadDuringReprocessing() {
        assertTrue(FarolRoadKmFinality0713.shouldPreserveConfirmedRoad("uber|rua-a-10","uber|rua-a-10",3.653,3.653))
    }
    @Test fun changedBindingNeverPreservesOldRoadResult() {
        assertFalse(FarolRoadKmFinality0713.shouldPreserveConfirmedRoad("uber|rua-a-10","uber|rua-b-20",3.653,3.653))
    }
}
