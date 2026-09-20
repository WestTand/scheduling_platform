package scheduling.scheduling.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.OrderBy;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class WorkShiftMappingTest {

    @Test
    void shiftAssignmentsAreOrderedById() throws NoSuchFieldException {
        Field shiftAssignments = WorkShift.class.getDeclaredField("shiftAssignments");

        assertThat(shiftAssignments.getAnnotation(OrderBy.class))
                .extracting(OrderBy::value)
                .isEqualTo("id ASC");
    }
}
