package be.stealingdapenta.coreai.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModelPageTest {

    @Test
    void reservesNavigationSlotsOutsideTheModelRange() {
        assertEquals(44, ModelPage.modelIndex(0, 44, 60));
        assertEquals(-1, ModelPage.modelIndex(0, 45, 60));
        assertEquals(-1, ModelPage.modelIndex(0, 53, 60));
        assertEquals(45, ModelPage.modelIndex(1, 0, 60));
    }

    @Test
    void boundsTheLastPageAndKeepsEmptyListsAtOnePage() {
        assertEquals(2, ModelPage.count(46));
        assertEquals(59, ModelPage.modelIndex(1, 14, 60));
        assertEquals(-1, ModelPage.modelIndex(1, 15, 60));
        assertEquals(1, ModelPage.count(0));
        assertEquals(-1, ModelPage.modelIndex(0, 0, 0));
    }
}
