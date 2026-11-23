package de.thi.terramax.tour;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Bildet {@link TourStatus} auf die Strings der Spalte {@code tour.status} ab und zurück.
 */
@Converter(autoApply = true)
public class TourStatusConverter implements AttributeConverter<TourStatus, String> {

    @Override
    public String convertToDatabaseColumn(TourStatus status) {
        return status == null ? null : status.getDbValue();
    }

    @Override
    public TourStatus convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : TourStatus.fromDbValue(dbValue);
    }
}
