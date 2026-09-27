package com.eatbefore.domain.shelflife

import com.eatbefore.domain.model.HomemadeKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HomemadeShelfLifeTest {

    @Test
    fun `soup keeps three days in the fridge`() {
        assertEquals(3, HomemadeShelfLife.suggestDays("Борщ", HomemadeKind.DISH))
    }

    /** Unlike bought food, an unknown dish still gets the general rule for cooked food. */
    @Test
    fun `a dish nobody listed still gets the cooked-food default`() {
        assertEquals(
            HomemadeShelfLife.DAYS_DISH_DEFAULT,
            HomemadeShelfLife.suggestDays("Бабушкино фирменное", HomemadeKind.DISH),
        )
        assertEquals(HomemadeShelfLife.DAYS_DISH_DEFAULT, HomemadeShelfLife.suggestDays(null, HomemadeKind.DISH))
    }

    @Test
    fun `a mayonnaise salad is for tomorrow at the latest`() {
        assertEquals(1, HomemadeShelfLife.suggestDays("Оливье", HomemadeKind.DISH))
    }

    @Test
    fun `cutlets keep two days`() {
        assertEquals(2, HomemadeShelfLife.suggestDays("Котлеты куриные", HomemadeKind.DISH))
    }

    @Test
    fun `a dish frozen on the day keeps for months`() {
        assertEquals(
            HomemadeShelfLife.DAYS_DISH_FROZEN,
            HomemadeShelfLife.suggestDays("Котлеты", HomemadeKind.DISH, frozen = true),
        )
    }

    @Test
    fun `a sealed jar keeps a year`() {
        assertEquals(365, HomemadeShelfLife.suggestDays("Варенье вишнёвое", HomemadeKind.PRESERVE))
        assertEquals(365, HomemadeShelfLife.suggestDays("Огурцы маринованные", HomemadeKind.PRESERVE))
    }

    @Test
    fun `frozen berries keep less than a jar`() {
        assertEquals(270, HomemadeShelfLife.suggestDays("Клубника замороженная", HomemadeKind.PRESERVE))
    }

    /** Once the jar is open the opening table takes over, and must know home preserves. */
    @Test
    fun `an opened jar of pickles is not treated as a two-day tin`() {
        assertEquals(14, OpeningShelfLife.suggestDays("Огурцы солёные"))
        assertEquals(5, OpeningShelfLife.suggestDays("Икра кабачковая"))
        assertEquals(30, OpeningShelfLife.suggestDays("Варенье вишнёвое"))
    }
}
