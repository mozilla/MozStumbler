import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertTrue;

@Config(emulateSdk = 18)
@RunWith(RobolectricTestRunner.class)
public class DefaultPackageTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void shouldBeTrue() {
        assertTrue(true);
    }
}