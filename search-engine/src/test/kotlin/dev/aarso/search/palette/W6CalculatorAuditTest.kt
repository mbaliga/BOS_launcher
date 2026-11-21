package dev.aarso.search.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Wave 6 audit of PAL-05: locale decimal comma and the nanometre unit. */
class W6CalculatorAuditTest {
    @Test fun PAL_05_a_decimal_comma_is_a_decimal_mark_when_it_is_unambiguous() {
        assertEquals("3", Calculator.evaluate("1,5 * 2")?.text)
        assertEquals("1,5 * 2", Calculator.evaluate("1,5 * 2")?.expression)
        assertEquals("4", Calculator.evaluate("1,5 + 2,5")?.text)
        assertEquals("1.5", Calculator.evaluate("3 / 2,0")?.text)
    }

    @Test fun PAL_05_a_thousands_comma_or_a_stray_comma_is_still_not_a_calculation() {
        assertNull(Calculator.evaluate("1,000 + 1"))
        assertNull(Calculator.evaluate("1, + 1"))
        assertNull(Calculator.evaluate("1,5"))
    }

    @Test fun PAL_05_a_decimal_comma_works_in_unit_conversion() {
        assertEquals("2.5 km", Calculator.evaluate("2500 m to km")?.text)
        assertEquals("1.5 m", Calculator.evaluate("1,5 m to m")?.text)
    }

    @Test fun PAL_05_nm_is_the_nanometre_and_the_nautical_mile_is_nmi() {
        assertEquals("0.000001 mm", Calculator.evaluate("1 nm to mm")?.text)
        assertEquals("1.852 km", Calculator.evaluate("1 nmi to km")?.text)
    }
}
