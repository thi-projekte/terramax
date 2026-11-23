package de.thi.terramax.location;

/**
 * Wird geworfen, wenn ein GeoJSON-Feature nicht in eine {@link Location} umgewandelt werden kann.
 */
public class InvalidFeatureException extends Exception {

    public InvalidFeatureException(String message) {
        super(message);
    }
}
