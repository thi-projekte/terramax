package de.thi.terramax.location;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Bildet {@link LocationCategory} auf die Strings der Spalte {@code location.category} ab und zurück.
 */
@Converter(autoApply = true)
public class LocationCategoryConverter implements AttributeConverter<LocationCategory, String> {

    @Override
    public String convertToDatabaseColumn(LocationCategory category) {
        return category == null ? null : category.getDbValue();
    }

    @Override
    public LocationCategory convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : LocationCategory.fromDbValue(dbValue);
    }
}
