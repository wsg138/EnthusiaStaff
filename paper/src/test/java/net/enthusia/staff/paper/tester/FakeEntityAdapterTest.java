package net.enthusia.staff.paper.tester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FakeEntityAdapterTest {
    @Test
    void unavailableAdapterFailsClosedWithoutCreatingSyntheticState() {
        FakeEntityAdapter adapter = FakeEntityAdapter.unavailable();
        assertFalse(adapter.available());
        assertThrows(IllegalStateException.class, adapter::create);
        adapter.close();
    }
}
