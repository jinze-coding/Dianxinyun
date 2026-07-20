package com.example.siteplatform.electricbox.service;

import com.example.siteplatform.electricbox.entity.ElectricBox;
import com.example.siteplatform.inspection.entity.InspectionRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElectricBoxServiceTest {

    private final ElectricBoxService service = new ElectricBoxService();

    @Test
    void doesNotCountHistoricalRectificationAsTodayInspection() {
        ElectricBox box = activeBox();

        assertEquals("UNCHECKED", service.resolveTodayStatus(box, null, 2));
    }

    @Test
    void keepsTodayInspectionAbnormalWhileRectificationIsOpen() {
        ElectricBox box = activeBox();
        InspectionRecord record = new InspectionRecord();
        record.setAbnormalCount(0);

        assertEquals("ABNORMAL", service.resolveTodayStatus(box, record, 1));
    }

    @Test
    void resolvesTodayRecordByItsAbnormalCount() {
        ElectricBox box = activeBox();
        InspectionRecord normalRecord = new InspectionRecord();
        normalRecord.setAbnormalCount(0);
        InspectionRecord abnormalRecord = new InspectionRecord();
        abnormalRecord.setAbnormalCount(1);

        assertEquals("CHECKED", service.resolveTodayStatus(box, normalRecord, 0));
        assertEquals("ABNORMAL", service.resolveTodayStatus(box, abnormalRecord, 0));
    }

    @Test
    void inactiveBoxIsAlwaysUnchecked() {
        ElectricBox box = new ElectricBox();
        box.setStatus("INACTIVE");
        InspectionRecord record = new InspectionRecord();
        record.setAbnormalCount(1);

        assertEquals("UNCHECKED", service.resolveTodayStatus(box, record, 1));
    }

    private ElectricBox activeBox() {
        ElectricBox box = new ElectricBox();
        box.setStatus("ACTIVE");
        return box;
    }
}
